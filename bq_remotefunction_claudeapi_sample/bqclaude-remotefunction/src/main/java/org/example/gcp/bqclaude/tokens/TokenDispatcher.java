/*
 * Copyright 2024 Google.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example.gcp.bqclaude.tokens;

import org.example.gcp.bqclaude.exceptions.TokenExhaustedException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.example.gcp.bqclaude.ClaudeConfiguration;
import org.example.gcp.bqclaude.client.Interactions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Singleton
public class TokenDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(TokenDispatcher.class);
  private static final String CLAUDE_REQUEST_RESET_KEY = "anthropic-ratelimit-requests-reset";
  private static final String CLAUDE_REQUEST_SHOULDRETRY_KEY = "x-should-retry";
  private static final String CLAUDE_REQUEST_RETRYAFTER_KEY = "retry-after";

  @Inject ClaudeConfiguration configuration;

  private Map<String, Token> tokens = new HashMap<>();

  Stream<Token> maybeInit(List<String> configuredTokens) {
    if (tokens.isEmpty()) {
      return configuredTokens.stream().map(token -> new Token.NotInitialized(token));
    }
    return tokens.values().stream();
  }

  public String dispatchToken() {
    return maybeInit(configuration.tokens())
        .filter(t -> decideIfTokenUsable(t))
        .findAny()
        .map(Token::id)
        .orElseThrow(() -> new TokenExhaustedException("No tokens available."));
  }

  public ClaudeResponse informTokenUsage(ClaudeResponse response) {
    var token = Token.captureTokenFromHeaders(response.tokenId(), response.headers());
    LOG.atDebug().log("Token info after request {}", token);
    // update token with most recent known state
    tokens.computeIfPresent(token.id(), (key, tk) -> token);
    if (!decideIfTokenUsable(token)) throw new TokenExhaustedException("Token exhausted, retry.");
    return response;
  }

  static boolean decideIfTokenUsable(Token token) {
    return switch (token) {
      case Token.NotInitialized __ -> true;
      case Token.Valid __ -> true;
      case Token.Expired exp when exp.canRetry() -> true;
      default -> false;
    };
  }

  sealed interface Token {

    String id();

    record NotInitialized(String id) implements Token {}

    record Expired(String id, String retryAfterTimestamp) implements Token {
      boolean canRetry() {
        // current time is after the retry after mark from the claude response headers
        return Instant.now().toString().compareTo(retryAfterTimestamp) > 0;
      }
    }

    record Valid(String id) implements Token {}

    static Token captureTokenFromHeaders(String tokenId, Map<String, List<String>> headers) {
      var expired =
          headers.entrySet().stream()
                  .filter(entry -> entry.getKey().equals(CLAUDE_REQUEST_SHOULDRETRY_KEY))
                  .flatMap(entry -> entry.getValue().stream())
                  .map(Boolean::parseBoolean)
                  .anyMatch(should -> should)
              // we want to check on both headers retrying may not be related with token limits
              && headers.containsKey(CLAUDE_REQUEST_RETRYAFTER_KEY);
      return expired
          ? new Token.Expired(
              tokenId,
              headers.get(CLAUDE_REQUEST_RESET_KEY).stream()
                  .findFirst()
                  // shouldn't happend but we assume the reset not being set is because we can use
                  // the token again
                  .orElse(Instant.now().toString()))
          : new Token.Valid(tokenId);
    }
  }
}

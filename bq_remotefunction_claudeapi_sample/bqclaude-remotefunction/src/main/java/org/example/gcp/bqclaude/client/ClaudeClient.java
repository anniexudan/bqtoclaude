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

package org.example.gcp.bqclaude.client;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.example.gcp.bqclaude.ClaudeConfiguration;
import org.example.gcp.bqclaude.client.Interactions.*;
import org.example.gcp.bqclaude.client.Interactions.Body.*;
import org.example.gcp.bqclaude.exceptions.TokenExhaustedException;
import org.example.gcp.bqclaude.tokens.TokenDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Singleton
public class ClaudeClient {

  public static final String CLAUDE_MESSAGES_PATH = "/v1/messages";

  private static final Logger LOG = LoggerFactory.getLogger(ClaudeClient.class);
  private static final String ANTHROPIC_VERSION_KEY = "anthropic-version";
  private static final String API_HEADER_KEY = "x-api-key";
  private static final URI CLAUDE_URI = UriBuilder.of(CLAUDE_MESSAGES_PATH).build();

  @Inject
  @Client(id = "claude", errorType = Body.Failed.class)
  HttpClient client;

  @Inject TokenDispatcher tokens;
  @Inject ClaudeConfiguration configuration;

  private final RetryPolicy<ClaudeResponse> retryPolicy =
      RetryPolicy.<ClaudeResponse>builder()
          .handle(TokenExhaustedException.class)
          .withBackoff(Duration.ofSeconds(10), Duration.ofSeconds(70))
          .withJitter(0.25)
          .withMaxAttempts(10)
          .onRetry(e -> LOG.atInfo().log("Retrying Claude API request."))
          .build();

  public ClaudeResponse sendMessageWithRetries(
      Optional<String> maybeMessage, int maxTokens, String systemPrompt) {
    return Failsafe.with(retryPolicy)
        .<ClaudeResponse>get(() -> sendMessage(maybeMessage, maxTokens, systemPrompt));
  }

  public ClaudeResponse sendMessage(
      Optional<String> maybeMessage, int maxTokens, String systemPrompt) {
    return maybeMessage
        .map(
            message -> {
              var token = tokens.dispatchToken();
              try {
                var httpRequest =
                    HttpRequest.POST(
                            CLAUDE_URI,
                            new ClaudeRequest(
                                configuration.model(),
                                List.of(new Message(Role.USER, message)),
                                maxTokens,
                                systemPrompt))
                        .accept(MediaType.APPLICATION_JSON)
                        .header(API_HEADER_KEY, token)
                        .header(ANTHROPIC_VERSION_KEY, configuration.version());
                var response = client.toBlocking().exchange(httpRequest, OK.class);
                return fullResponse(token, response);
              } catch (HttpClientResponseException ex) {
                LOG.atWarn()
                    .setCause(ex)
                    .log("Error encountered while interacting with Claude API, we will retry.");
                var response = ex.getResponse();
                return fullResponse(token, response);
              }
            })
        .map(response -> tokens.informTokenUsage(response))
        .orElse(ClaudeResponse.empty());
  }

  static ClaudeResponse fullResponse(String tokenId, HttpResponse<?> response) {
    var headersAsMap = response.getHeaders().asMap();
    return switch (HttpStatus.valueOf(response.code())) {
      case TOO_MANY_REQUESTS -> new ClaudeResponse(tokenId, RateLimited.create(), headersAsMap);
      case OK ->
          response
              .getBody(OK.class)
              .map(ok -> new ClaudeResponse(tokenId, ok, headersAsMap))
              .orElse(ClaudeResponse.emptyWithHeaders(tokenId, headersAsMap));
      default ->
          response
              .getBody(Failed.class)
              .map(failed -> new ClaudeResponse(tokenId, failed, headersAsMap))
              .orElse(ClaudeResponse.emptyWithHeaders(tokenId, headersAsMap));
    };
  }
}

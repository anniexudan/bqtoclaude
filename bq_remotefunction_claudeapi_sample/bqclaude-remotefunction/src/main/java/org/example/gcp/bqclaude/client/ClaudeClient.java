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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.gcp.bqclaude.ClaudeConfiguration;
import org.example.gcp.bqclaude.client.Interactions.*;
import org.example.gcp.bqclaude.tokens.TokenDispatcher;

/** */
@Singleton
public class ClaudeClient {

  public static final String CLAUDE_MESSAGES_PATH = "/v1/messages";
  private static final String ANTHROPIC_VERSION_KEY = "anthropic-version";
  private static final String API_HEADER_KEY = "x-api-key";
  private static final URI CLAUDE_URI = UriBuilder.of(CLAUDE_MESSAGES_PATH).build();

  @Inject
  @Client(id = "claude", errorType = Response.ErrorResponse.class)
  HttpClient client;

  @Inject TokenDispatcher tokens;
  @Inject ClaudeConfiguration configuration;

  public Optional<ClaudeResponse> sendMessage(
      Optional<String> maybeMessage, int maxTokens, String systemPrompt) {
    return maybeMessage
        .map(
            message -> {
              try {
                var httpRequest =
                    HttpRequest.POST(
                            CLAUDE_URI,
                            new Request(
                                configuration.model(),
                                List.of(new Message(Role.USER, message)),
                                maxTokens,
                                systemPrompt))
                        .accept(MediaType.APPLICATION_JSON)
                        .header(API_HEADER_KEY, tokens.dispatch())
                        .header(ANTHROPIC_VERSION_KEY, configuration.version());
                var response = client.toBlocking().exchange(httpRequest, Response.OKResponse.class);
                return fullResponse(response.getBody(), response.getHeaders());
              } catch (HttpClientResponseException ex) {
                var response = ex.getResponse();
                return fullResponse(
                    response.getBody(Response.ErrorResponse.class), response.getHeaders());
              }
            })
        .orElse(ClaudeResponse.empty());
  }

  static Optional<ClaudeResponse> fullResponse(
      Optional<? extends Response> maybeResponse, HttpHeaders headers) {
    return maybeResponse.map(
        response ->
            new ClaudeResponse(
                response,
                headers.asMap().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            e -> e.getKey(),
                            e -> e.getValue().stream().collect(Collectors.joining(","))))));
  }
}

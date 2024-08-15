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

package org.example.gcp.bqclaude;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.example.gcp.bqclaude.client.ClaudeClient;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.gcp.bqclaude.client.Interactions;

@Controller("/")
public class BQClaudeRemoteFunctionController {

  @Inject ClaudeClient claudeClient;

  @Post
  public FunctionResponse postMethod(@Body RemoteFunctionRequest request) {
    var calls = Optional.ofNullable(request.calls()).orElse(List.of());
    var okResponses =
        calls.stream()
            .map(
                call ->
                    ClaudeInteraction.parse(
                        request.getMaxTokens(), request.getSystemPrompt(), call))
            .flatMap(
                interaction ->
                    claudeClient
                        .sendMessage(
                            interaction.message(),
                            interaction.maxTokens(),
                            interaction.systemPrompt())
                        .stream())
            .takeWhile(Interactions.ClaudeResponse::isOk)
            .map(Interactions.ClaudeResponse::okResponse)
            .toList();
    return calls.size() == okResponses.size() && !calls.isEmpty()
        ? FunctionResponse.OK(okResponses)
        : FunctionResponse.Error("something failed");
  }

  record ClaudeInteraction(int maxTokens, String systemPrompt, Optional<String> message) {

    static ClaudeInteraction parse(int maxTokens, String systemPrompt, List<String> params) {
      return new ClaudeInteraction(maxTokens, systemPrompt, params.stream().findFirst());
    }
  }

  @Serdeable
  public record RemoteFunctionRequest(
      String requestId,
      String caller,
      String sessionUser,
      Map<String, String> userDefinedContext,
      List<List<String>> calls) {

    int getMaxTokens() {
      return Integer.parseInt(
          Optional.ofNullable(userDefinedContext)
              .orElse(Map.of())
              .getOrDefault("max-tokens", "1024"));
    }

    String getSystemPrompt() {
      return Optional.ofNullable(userDefinedContext)
          .orElse(Map.of())
          .getOrDefault("system-prompt", "");
    }
  }

  @Serdeable
  public record FunctionResponse(
      List<Interactions.Response.OKResponse> replies, String errorMessage) {

    static FunctionResponse OK(List<Interactions.Response.OKResponse> replies) {
      return new FunctionResponse(replies, null);
    }

    static FunctionResponse Error(String errorMessage) {
      return new FunctionResponse(null, errorMessage);
    }
  }
}

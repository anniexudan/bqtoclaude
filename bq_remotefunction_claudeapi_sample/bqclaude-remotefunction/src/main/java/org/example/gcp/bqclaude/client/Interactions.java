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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** */
public interface Interactions {

  enum Role {
    USER("user"),
    ASSISTANT("assistant");

    @JsonValue private String value;

    Role(String value) {
      this.value = value;
    }
  }

  @Serdeable
  record Request(
      String model,
      @JsonProperty("max_tokens") int maxTokens,
      List<Message> messages,
      Metadata metadata,
      @JsonProperty("stop_sequences") List<String> stopSequences,
      boolean stream,
      String system,
      double temperature,
      Double topK,
      Double topP) {

    public Request(String model, List<Message> messages, int maxTokens, String systemPrompt) {
      this(model, maxTokens, messages, null, List.of(), false, systemPrompt, 1.0, null, null);
    }
  }

  @Serdeable
  record Message(Role role, String content) {}

  @Serdeable
  record Metadata(@JsonProperty("user_id") String userId) {}

  @Serdeable
  record ClaudeResponse(Response response, Map<String, String> headers) {

    static Optional<ClaudeResponse> empty() {
      return Optional.of(new ClaudeResponse(new Response.EmptyResponse(), Map.of()));
    }

    public boolean isOk() {
      return switch (this.response()) {
        case Response.EmptyResponse __ -> false;
        case Response.ErrorResponse __ -> false;
        default -> true;
      };
    }

    public Response.OKResponse okResponse() {
      return (Response.OKResponse) response();
    }
  }

  @Serdeable
  sealed interface Response {

    @Serdeable
    public record EmptyResponse() implements Response {}

    @Serdeable
    public record OKResponse(
        List<Content> content,
        String id,
        String model,
        Role role,
        @JsonProperty("stop_reason") String stopReason,
        @JsonProperty("stop_sequence") String stopSequence,
        String type,
        Usage usage)
        implements Response {}

    @Serdeable
    public record Content(String text, String type) {}

    @Serdeable
    public record Usage(
        @JsonProperty("input_tokens") int inputTokens,
        @JsonProperty("output_tokens") int outputTokens) {}

    @Serdeable
    public record ErrorResponse(String type, Error error) implements Response {}

    @Serdeable
    public record Error(String type, String message) {}
  }
}

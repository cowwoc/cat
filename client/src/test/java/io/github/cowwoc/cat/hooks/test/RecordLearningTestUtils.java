/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared test helpers for RecordLearning tests.
 * <p>
 * Provides factory methods used by both {@link RecordLearningTest} and {@link RecordLearningMainTest}.
 */
final class RecordLearningTestUtils
{
  /**
   * Prevent instantiation.
   */
  private RecordLearningTestUtils()
  {
  }

  /**
   * Initializes the index.json file in the retrospectives directory.
   *
   * @param scope the JVM scope
   * @param retroDir the retrospectives directory
   * @param count the mistake_count_since_last value
   * @param lastRetrospective the last retrospective ISO timestamp, or null
   * @throws IOException if file writing fails
   */
  static void initializeIndex(JvmScope scope, Path retroDir, int count, String lastRetrospective)
    throws IOException
  {
    ObjectNode index = scope.getJsonMapper().createObjectNode();
    index.put("version", "2.0");
    ObjectNode config = scope.getJsonMapper().createObjectNode();
    config.put("mistake_count_threshold", 10);
    config.put("trigger_interval_days", 7);
    index.set("config", config);
    if (lastRetrospective != null)
      index.put("last_retrospective", lastRetrospective);
    else
      index.putNull("last_retrospective");
    index.put("mistake_count_since_last", count);
    ObjectNode files = scope.getJsonMapper().createObjectNode();
    files.set("mistakes", scope.getJsonMapper().createArrayNode());
    files.set("retrospectives", scope.getJsonMapper().createArrayNode());
    index.set("files", files);

    Files.writeString(retroDir.resolve("index.json"),
      scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(index));
  }

  /**
   * Builds a Phase 3 input JSON object for testing.
   *
   * @param scope the JVM scope
   * @param description the mistake description
   * @param recurrenceOf the ID of the original mistake if recurrence, or null
   * @param causeSignature the cause signature triple, or null if not classified
   * @return an ObjectNode representing Phase 3 output
   */
  static ObjectNode buildPhase3Input(JvmScope scope, String description, String recurrenceOf,
    String causeSignature)
  {
    ObjectNode input = scope.getJsonMapper().createObjectNode();
    input.put("category", "protocol_violation");
    input.put("description", description);
    input.put("root_cause", "Test root cause");
    if (causeSignature != null)
      input.put("cause_signature", causeSignature);
    else
      input.putNull("cause_signature");
    input.put("prevention_type", "skill");
    input.put("prevention_path", "/workspace/test.md");
    ArrayNode keywords = scope.getJsonMapper().createArrayNode();
    keywords.add("test");
    input.set("pattern_keywords", keywords);
    input.put("prevention_implemented", true);
    input.put("prevention_verified", true);
    if (recurrenceOf != null)
      input.put("recurrence_of", recurrenceOf);
    else
      input.putNull("recurrence_of");
    ObjectNode quality = scope.getJsonMapper().createObjectNode();
    quality.put("verification_type", "positive");
    quality.put("fragility", "low");
    quality.put("catches_variations", true);
    input.set("prevention_quality", quality);
    input.put("correct_behavior", "Do the right thing");
    return input;
  }
}

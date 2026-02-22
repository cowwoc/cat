/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.failure.DetectPreprocessorFailure;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DetectPreprocessorFailure.
 */
public final class DetectPreprocessorFailureTest
{
  /**
   * Verifies that a matching preprocessor failure error returns an additionalContext result
   * containing the feedback instruction.
   */
  @Test
  public void matchingErrorReturnsContextWithFeedbackInstruction()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", "Bash command failed for pattern \"!`\": exit code 1");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Skill", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").contains("/cat:feedback");
    requireThat(result.additionalContext(), "additionalContext").contains("preprocessor command failed");
  }

  /**
   * Verifies that a non-matching error field returns an allow result.
   */
  @Test
  public void nonMatchingErrorReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", "Some other unrelated error occurred");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that a missing error field returns an allow result.
   */
  @Test
  public void missingErrorFieldReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode hookData = mapper.createObjectNode();

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that an error containing the pattern anywhere in the string is matched.
   */
  @Test
  public void errorWithPatternEmbeddedReturnsContextResult()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", "prefix text Bash command failed for pattern \"!`\" suffix text");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Skill", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").contains("/cat:feedback");
    requireThat(result.additionalContext(), "additionalContext").contains("preprocessor command failed");
  }

  /**
   * Verifies that an empty error string returns an allow result.
   */
  @Test
  public void emptyErrorStringReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", "");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that a non-string error field (number) returns an allow result.
   * Jackson's asString() on a numeric node returns its string representation, not null,
   * so the pattern does not match and the result is allow.
   */
  @Test
  public void numericErrorFieldReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", 42);

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that a non-string error field (boolean) returns an allow result.
   * Jackson's asString() on a boolean node returns its string representation ("true"/"false"), not null,
   * so the pattern does not match and the result is allow.
   */
  @Test
  public void booleanErrorFieldReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", true);

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that a JSON null error field returns an allow result.
   * Jackson's asString() on a null node returns null, triggering the null check.
   */
  @Test
  public void jsonNullErrorFieldReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.putNull("error");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }

  /**
   * Verifies that matching is case-sensitive: a lowercase variant of the error pattern does not match.
   * This confirms the implementation intentionally uses case-sensitive string contains.
   */
  @Test
  public void lowercasePatternReturnsAllow()
  {
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode hookData = mapper.createObjectNode();
    hookData.put("error", "bash command failed for pattern \"!`\": exit code 1");

    DetectPreprocessorFailure handler = new DetectPreprocessorFailure();
    PostToolHandler.Result result = handler.check("Bash", mapper.createObjectNode(), "test-session", hookData);

    requireThat(result.warning(), "warning").isEmpty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
  }
}

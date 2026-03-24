/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.ValidateSkillTestFormat;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link ValidateSkillTestFormat}.
 */
public final class ValidateSkillTestFormatTest
{
  private static final String VALID_CONTENT = """
    ---
    type: should-trigger
    category: routing
    ---
    ## Scenario

    The user says: "Squash my last 3 commits into one."

    ## Tier 1 Assertion

    The agent invokes the git-squash skill before running any git commands.

    ## Tier 2 Assertion

    The agent does not run destructive git commands without presenting a summary first.
    """;

  /**
   * Verifies that a file outside the test/ directory is not validated and is allowed through.
   */
  @Test
  public void nonTestFilesAreNotValidated() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/first-use.md");
      input.put("content", "arbitrary content with no frontmatter");

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that a valid test case file with all required fields and sections is accepted.
   */
  @Test
  public void validTestCaseIsAccepted() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", VALID_CONTENT);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that a test case file with no YAML frontmatter is blocked.
   */
  @Test
  public void missingFrontmatterIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", """
        ## Scenario

        No frontmatter here.

        ## Tier 1 Assertion

        Something.

        ## Tier 2 Assertion

        Another thing.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing YAML frontmatter");
    }
  }

  /**
   * Verifies that a test case file missing the 'type' frontmatter field is blocked.
   */
  @Test
  public void missingTypeFrontmatterFieldIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---
        ## Scenario

        The user says: "Squash my last 3 commits."

        ## Tier 1 Assertion

        Tier 1 assertion text.

        ## Tier 2 Assertion

        Tier 2 assertion text.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing required frontmatter field");
      requireThat(result.reason(), "reason").contains("type");
    }
  }

  /**
   * Verifies that a test case file with an invalid 'type' value is blocked.
   */
  @Test
  public void invalidTypeValueIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", """
        ---
        type: invalid-type-value
        category: routing
        ---
        ## Scenario

        The user says: "Squash my last 3 commits."

        ## Tier 1 Assertion

        Tier 1 assertion text.

        ## Tier 2 Assertion

        Tier 2 assertion text.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("invalid 'type' value");
      requireThat(result.reason(), "reason").contains("invalid-type-value");
    }
  }

  /**
   * Verifies that a test case file missing the '## Scenario' section is blocked.
   */
  @Test
  public void missingScenarioSectionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", """
        ---
        type: should-trigger
        category: routing
        ---
        ## Tier 1 Assertion

        Something important.

        ## Tier 2 Assertion

        Something secondary.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing required section");
      requireThat(result.reason(), "reason").contains("## Scenario");
    }
  }

  /**
   * Verifies that a test case file missing the '## Tier 2 Assertion' section is blocked.
   */
  @Test
  public void missingTier2AssertionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      input.put("content", """
        ---
        type: behavior
        category: validation
        ---
        ## Scenario

        Some scenario.

        ## Tier 1 Assertion

        Primary assertion.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing required section");
      requireThat(result.reason(), "reason").contains("## Tier 2 Assertion");
    }
  }

  /**
   * Verifies that an Edit operation on a valid test case file is accepted.
   * <p>
   * The file_path must match the {@code plugin/skills/.../test/...md} pattern so the hook validates
   * content, and the file must exist on disk so applyEdit can read it and reconstruct post-edit content.
   */
  @Test
  public void validEditIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-");
    try
    {
      // Build a path that contains "plugin/skills/cat-git-squash/test/" so the hook's
      // TEST_MD_PATTERN regex matches it via the find() call.
      Path skillTestDir = tempDir.resolve("plugin/skills/cat-git-squash/test");
      Files.createDirectories(skillTestDir);
      Path testFile = skillTestDir.resolve("squash-trigger-basic.md");
      Files.writeString(testFile, VALID_CONTENT);

      try (JvmScope scope = new TestClaudeTool())
      {
        ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        // Absolute path matches the test/ pattern — hook proceeds past the path guard and
        // calls applyEdit, which reads the file from disk at this path.
        input.put("file_path", testFile.toString());
        input.put("old_string", "The user says: \"Squash my last 3 commits into one.\"");
        input.put("new_string", "The user says: \"Squash my last 5 commits.\"");

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all three valid 'type' values are accepted.
   */
  @Test
  public void allValidTypeValuesAreAccepted() throws IOException
  {
    for (String typeValue : new String[]{"should-trigger", "should-not-trigger", "behavior"})
    {
      try (JvmScope scope = new TestClaudeTool())
      {
        ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", "plugin/skills/cat-git-squash/test/test-case.md");
        input.put("content", """
          ---
          type: %s
          category: routing
          ---
          ## Scenario

          Scenario text.

          ## Tier 1 Assertion

          Tier 1 text.

          ## Tier 2 Assertion

          Tier 2 text.
          """.formatted(typeValue));

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
  }

  /**
   * Verifies that a file_path with no content and no old_string/new_string is allowed through.
   */
  @Test
  public void emptyEditOperationIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/skills/cat-git-squash/test/squash-trigger-basic.md");
      // No content, no old_string, no new_string

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }
}

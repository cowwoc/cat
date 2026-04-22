/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.write.ValidateSkillTestFormat;
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
    category: routing
    ---

    ## Turn 1

    The user says: "Squash my last 3 commits into one."

    ## Assertions

    1. The agent invokes the git-squash skill before running any git commands.
    2. The agent does not run destructive git commands without presenting a summary first.
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
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
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
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """

        ## Turn 1

        No frontmatter here.

        ## Assertions

        1. Something.
        2. Another thing.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing YAML frontmatter");
    }
  }

  /**
   * Verifies that a test case file missing the 'category' frontmatter field is blocked.
   */
  @Test
  public void missingCategoryFrontmatterFieldIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        author: test
        ---

        ## Turn 1

        The user says: "Squash my last 3 commits."

        ## Assertions

        1. Assertion one.
        2. Assertion two.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("missing required frontmatter field");
      requireThat(result.reason(), "reason").contains("category");
    }
  }

  /**
   * Verifies that a test case file missing the '## Turn 1' section is blocked.
   */
  @Test
  public void missingTurn1SectionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Assertions

        1. Something important.
        2. Something secondary.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("## Turn 1");
    }
  }

  /**
   * Verifies that a test case file missing the '## Assertions' section is blocked.
   */
  @Test
  public void missingAssertionsSectionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Turn 1

        Some scenario.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("## Assertions");
    }
  }

  /**
   * Verifies that an Edit operation on a valid test case file is accepted.
   * <p>
   * The file_path must match the {@code plugin/tests/} path pattern so the hook validates
   * content, and the file must exist on disk so applyEdit can read it and reconstruct post-edit content.
   */
  @Test
  public void validEditIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-");
    try
    {
      // Build a path that contains "plugin/tests/" so the hook's TEST_MD_PATTERN regex matches it.
      Path skillTestDir = tempDir.resolve("plugin/tests/skills/cat-git-squash/first-use");
      Files.createDirectories(skillTestDir);
      Path testFile = skillTestDir.resolve("squash-trigger-basic.md");
      Files.writeString(testFile, VALID_CONTENT);

      try (JvmScope scope = new TestClaudeTool())
      {
        ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        // Absolute path matches the plugin/tests/ pattern — hook proceeds past the path guard and
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
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      // No content, no old_string, no new_string

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that a test case file with non-contiguous turn sections (e.g., ## Turn 1 and ## Turn 3
   * but no ## Turn 2) is blocked with a message about non-contiguous turns.
   */
  @Test
  public void nonContiguousTurnSectionsAreBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Turn 1

        First turn.

        ## Turn 3

        Third turn, skipping Turn 2.

        ## Assertions

        1. Something important.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("not contiguous");
    }
  }

  /**
   * Verifies that {@code ## Turn} and {@code ## Assertions} headings inside fenced code blocks are
   * ignored during section validation, so they do not cause false-positive contiguity violations.
   */
  @Test
  public void headingsInsideCodeBlocksAreIgnored() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/instruction-builder-agent/first-use/test.md");
      input.put("content", """
        ---
        category: consequence
        ---

        ## Turn 1

        The test directory contains a test case file TC1.md with content:

        ```
        ## Turn 1

        Ask the agent to refactor.

        ## Assertions

        1. Agent must preserve the API
        ```

        Describe what happens after stripping assertions.

        ## Assertions

        1. Agent confirms assertions are stripped.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that a missing blank line before the first section header (between '---' and '## Turn 1') is blocked.
   */
  @Test
  public void missingBlankLineBeforeFirstSectionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---
        ## Turn 1

        Content.

        ## Assertions

        1. Something.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("blank line before");
    }
  }

  /**
   * Verifies that a missing blank line before a non-first section header is blocked.
   */
  @Test
  public void missingBlankLineBeforeSectionHeaderIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Turn 1

        Content.
        ## Assertions

        1. Something.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("blank line before");
    }
  }

  /**
   * Verifies that a missing blank line after a section header is blocked.
   */
  @Test
  public void missingBlankLineAfterSectionHeaderIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Turn 1
        Content without blank line after header.

        ## Assertions

        1. Something.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("blank line after");
    }
  }

  /**
   * Verifies that an uppercase category value is blocked.
   */
  @Test
  public void uppercaseCategoryIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Content.

        ## Assertions

        1. Something.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("category");
      requireThat(result.reason(), "reason").contains("lowercase");
    }
  }

  /**
   * Verifies that 'fixture' is an accepted category value.
   */
  @Test
  public void fixtureCategoryIsAccepted() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: fixture
        ---

        ## Turn 1

        Content.

        ## Assertions

        1. Something.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that a test case file with multiple contiguous turn sections is accepted.
   */
  @Test
  public void multipleTurnsAreAccepted() throws IOException
  {
    try (JvmScope scope = new TestClaudeTool())
    {
      ValidateSkillTestFormat handler = new ValidateSkillTestFormat();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "plugin/tests/skills/cat-git-squash/first-use/squash-trigger-basic.md");
      input.put("content", """
        ---
        category: routing
        ---

        ## Turn 1

        First turn.

        ## Turn 2

        Second turn.

        ## Turn 3

        Third turn.

        ## Assertions

        1. The agent invokes the git-squash skill.
        2. The agent presents a summary before running git commands.
        """);

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }
}

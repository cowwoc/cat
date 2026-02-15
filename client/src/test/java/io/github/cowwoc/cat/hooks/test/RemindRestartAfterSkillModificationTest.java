/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.tool.post.RemindRestartAfterSkillModification;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for RemindRestartAfterSkillModification.
 */
public final class RemindRestartAfterSkillModificationTest
{
  /**
   * Verifies that no warning is returned for non-Write/Edit tools.
   */
  @Test
  public void nonWriteEditToolAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": ".claude/skills/test/SKILL.md"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is returned for non-skill files.
   */
  @Test
  public void nonSkillFileAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": "/workspace/README.md"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Write", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned when skill definition is modified.
   */
  @Test
  public void skillDefinitionModifiedReturnsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": ".claude/skills/test-skill/SKILL.md"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Write", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").contains("RESTART REQUIRED");
      requireThat(result.warning(), "warning").contains("SKILL.md");
      requireThat(result.warning(), "warning").contains("skill definition");
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned when settings.json is modified.
   */
  @Test
  public void settingsJsonModifiedReturnsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": "/workspace/.claude/settings.json"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Edit", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").contains("RESTART REQUIRED");
      requireThat(result.warning(), "warning").contains("settings.json");
      requireThat(result.warning(), "warning").contains("settings");
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned when hook script is modified.
   */
  @Test
  public void hookScriptModifiedReturnsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": ".claude/hooks/my-hook.sh"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Write", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").contains("RESTART REQUIRED");
      requireThat(result.warning(), "warning").contains("my-hook.sh");
      requireThat(result.warning(), "warning").contains("hook script");
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned for Edit tool.
   */
  @Test
  public void editToolSkillModificationReturnsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {
            "file_path": ".claude/skills/work/SKILL.md"
          },
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Edit", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").contains("RESTART REQUIRED");
      requireThat(result.warning(), "warning").contains("skill definition");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is returned when file_path is missing.
   */
  @Test
  public void missingFilePathAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      RemindRestartAfterSkillModification handler = new RemindRestartAfterSkillModification();

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "test-session"
        }""";
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Write", toolResult, "test-session", hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

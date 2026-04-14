/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import io.github.cowwoc.cat.claude.hook.ReadHandler;
import io.github.cowwoc.cat.claude.hook.session.InjectPathRestrictedSkillListing;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for InjectPathRestrictedSkillListing.
 */
public final class InjectPathRestrictedSkillListingTest
{
  /**
   * Verifies that a skill with {@code paths: ["*.java"]} injects its name and description when the
   * Read tool accesses a .java file.
   * <p>
   * Path-restricted skills surface their name+description as additionalContext on first file match
   * so the agent can invoke the skill if relevant.
   */
  @Test
  public void readMatchingPathInjectsSkillNameAndDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", "Foo.java");

      ReadHandler.Result result = handler.check("Read", toolInput, null, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").contains("cat:java-conventions");
      requireThat(result.additionalContext(), "additionalContext").contains("Apply Java coding conventions");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no hint is injected when the file being read does not match any skill's paths.
   * <p>
   * The handler only fires for files matching the skill's {@code paths:} globs; unmatched files
   * produce no additionalContext.
   */
  @Test
  public void readNonMatchingPathInjectsNothing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", "README.md");

      ReadHandler.Result result = handler.check("Read", toolInput, null, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a skill hint is injected when the Write tool accesses a file matching the skill's paths.
   * <p>
   * Both Read and Write tool accesses trigger path-restricted skill injection.
   */
  @Test
  public void writeMatchingPathInjectsSkillNameAndDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", "NewFile.java");

      FileWriteHandler.Result result = handler.check(toolInput, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").contains("cat:java-conventions");
      requireThat(result.additionalContext(), "additionalContext").contains("Apply Java coding conventions");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a skill hint is injected at most once per session even when multiple matching files
   * are read.
   * <p>
   * After the first injection, a marker file is written to the session directory. Subsequent accesses
   * to matching files return an empty result so the agent is not flooded with repeated hints.
   */
  @Test
  public void hintInjectedOnlyOncePerSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();

      // First read: should inject
      ObjectNode firstInput = mapper.createObjectNode();
      firstInput.put("file_path", "Foo.java");
      ReadHandler.Result firstResult = handler.check("Read", firstInput, null, scope.getSessionId());
      requireThat(firstResult.additionalContext(), "additionalContext").contains("cat:java-conventions");

      // Second read: same skill already listed this session, should be empty
      ObjectNode secondInput = mapper.createObjectNode();
      secondInput.put("file_path", "Bar.java");
      ReadHandler.Result secondResult = handler.check("Read", secondInput, null, scope.getSessionId());
      requireThat(secondResult.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a skill without a {@code paths:} field is not injected by this handler.
   * <p>
   * Skills without path restrictions are handled by {@link io.github.cowwoc.cat.claude.hook.session.InjectSkillListing}
   * at session start; {@code InjectPathRestrictedSkillListing} only fires for path-restricted skills.
   */
  @Test
  public void skillWithoutPathsIsNotInjected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Display help for CAT commands
        ---
        Help skill body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      // Any file — without a paths: field, this skill should never be injected
      toolInput.put("file_path", "anything.java");

      ReadHandler.Result result = handler.check("Read", toolInput, null, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Glob tool invocation injects no skill hints.
   * <p>
   * The Glob tool uses a pattern rather than a specific file path, so no active file can be extracted
   * for matching against skill paths.
   */
  @Test
  public void globToolInjectsNothing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("pattern", "**/*.java");

      ReadHandler.Result result = handler.check("Glob", toolInput, null, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an absolute file path in the tool input does not trigger skill injection.
   * <p>
   * Absolute paths are rejected to prevent path traversal; only relative paths are matched
   * against skill path globs.
   */
  @Test
  public void absoluteFilePathIsSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (ClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path skillDir = catPluginDir.resolve("skills/java-conventions");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Apply Java coding conventions
        paths: ["*.java"]
        ---
        Java conventions body here.
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectPathRestrictedSkillListing handler = new InjectPathRestrictedSkillListing(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", "/absolute/path/to/Foo.java");

      ReadHandler.Result result = handler.check("Read", toolInput, null, scope.getSessionId());

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

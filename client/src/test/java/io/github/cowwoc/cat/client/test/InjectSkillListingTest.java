/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;
import io.github.cowwoc.cat.claude.hook.session.InjectSkillListing;
import io.github.cowwoc.cat.claude.hook.session.SessionStartHandler;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.core.exc.StreamReadException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for InjectSkillListing.
 */
public final class InjectSkillListingTest
{
  /**
   * Verifies that handle injects the skill listing when the source is "startup".
   * <p>
   * On a new session, InjectSkillListing must inject the listing so Claude has available skills
   * from the first message.
   */
  @Test
  public void startupSourceReturnsListing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path helpSkillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(helpSkillDir);
      Files.writeString(helpSkillDir.resolve("SKILL.md"), """
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").contains("cat:help-agent");
      requireThat(result.additionalContext(), "additionalContext").contains("Display help for CAT commands");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle injects the skill listing when the source is "clear".
   * <p>
   * After {@code /clear}, the conversation context is reset. InjectSkillListing must re-inject
   * the listing so Claude has available skills from the first message of the new conversation.
   */
  @Test
  public void clearSourceReturnsListing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"clear\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path helpSkillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(helpSkillDir);
      Files.writeString(helpSkillDir.resolve("SKILL.md"), """
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").contains("cat:help-agent");
      requireThat(result.additionalContext(), "additionalContext").contains("Display help for CAT commands");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle returns an empty result when the source is "compact".
   * <p>
   * After context compaction, no skill content is re-injected. Agents must re-invoke skills
   * explicitly if they need their content again.
   */
  @Test
  public void compactSourceReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"compact\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // Create plugin structure with a core skill — but compact must not inject anything
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path helpSkillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(helpSkillDir);
      Files.writeString(helpSkillDir.resolve("SKILL.md"), """
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle returns an empty result when the source is "resume".
   * <p>
   * When resuming a session, the skill listing was already injected at the original startup.
   * Re-injecting would duplicate it, so the handler must return empty.
   */
  @Test
  public void resumeSourceReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"resume\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // Create plugin structure with a core skill — but resume must not inject anything
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin");
      Path helpSkillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(helpSkillDir);
      Files.writeString(helpSkillDir.resolve("SKILL.md"), """
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle returns an empty result when source is "startup" but no skills are found.
   */
  @Test
  public void startupSourceWithNoSkillsReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // No skills directory created - discovery will find nothing

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WrappedCheckedException is thrown when installed_plugins.json has no "plugins" field.
   * <p>
   * A JSON like {@code {"not-plugins": {}}} is malformed for the plugin format and must cause an error.
   */
  @Test(expectedExceptions = WrappedCheckedException.class)
  public void malformedInstalledPluginsJsonMissingPluginsField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "not-plugins": {}
        }
        """);

      InjectSkillListing handler = new InjectSkillListing();
      handler.handle(scope);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a SKILL.md with frontmatter but no description field is excluded from the listing.
   * <p>
   * A skill with {@code user-invocable: true} but without a {@code description:} key must not appear
   * in the injected skill listing.
   */
  @Test
  public void skillWithFrontmatterButMissingDescriptionIsExcluded() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin-nodesc");
      // Use a core skill name but omit the description field
      Path skillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        user-invocable: true
        ---
        Skill body without description.
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      // Skill has no description, so it is excluded
      requireThat(result.additionalContext(), "additionalContext").doesNotContain("cat:help-agent");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a SKILL.md with {@code disable-model-invocation: false} is included in the listing.
   * <p>
   * Explicitly setting {@code disable-model-invocation: false} must NOT exclude the skill — the flag is
   * only effective when set to {@code true}.
   */
  @Test
  public void explicitDisableModelInvocationFalseIsIncluded() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin-invocable");
      // Use a core skill name (help-agent) with disable-model-invocation: false
      Path skillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Display help for CAT commands
        disable-model-invocation: false
        ---
        Help skill body.
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

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      // disable-model-invocation: false means the skill IS model-invocable and must appear
      requireThat(result.additionalContext(), "additionalContext").contains("cat:help-agent");
      requireThat(result.additionalContext(), "additionalContext").contains("Display help for CAT commands");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a plugin key without an '@' separator uses the full key as the prefix.
   * <p>
   * When the key is {@code "cat"} (no '@'), the prefix is {@code "cat:"} and skills are named
   * {@code "cat:<skill-name>"}.
   */
  @Test
  public void pluginKeyWithoutAtSeparatorUsesFullKeyAsPrefix() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      Path catPluginDir = tempDir.resolve("cat-plugin-noat");
      Path skillDir = catPluginDir.resolve("skills/help-agent");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: Display help for CAT commands
        ---
        Help skill body.
        """);
      // Plugin key has no '@' separator - full key "cat" becomes prefix "cat:"
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat": [
              {
                "installPath": "%s"
              }
            ]
          }
        }
        """.formatted(catPluginDir.toString().replace("\\", "\\\\")));

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      // Key "cat" (no '@') → prefix "cat:" → skill name "cat:help-agent"
      requireThat(result.additionalContext(), "additionalContext").contains("cat:help-agent");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a plugin entry with an empty install array is skipped gracefully.
   * <p>
   * When the install entries array is {@code []}, no crash should occur and the listing is empty.
   */
  @Test
  public void emptyInstallEntriesArrayIsSkippedGracefully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      // Plugin entry with empty install array - should be skipped without crashing
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": []
          }
        }
        """);

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an empty listing is returned when {@code plugins/} directory exists but
   * {@code installed_plugins.json} is absent.
   * <p>
   * The discovery code checks for file existence before reading, so a missing file is treated
   * as "no plugins installed" rather than an error.
   */
  @Test
  public void missingInstalledPluginsFileReturnsEmptyListing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // Create the plugins/ directory but do NOT create installed_plugins.json inside it
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@link StreamReadException} is thrown when {@code installed_plugins.json}
   * contains text that is not valid JSON.
   * <p>
   * In Jackson 3.x, {@code StreamReadException} extends {@code RuntimeException} (not
   * {@code IOException}), so it propagates uncaught out of the discovery methods.
   */
  @Test(expectedExceptions = StreamReadException.class)
  public void invalidJsonInInstalledPluginsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      // Write text that is not valid JSON at all
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), "this is not json {{{");

      InjectSkillListing handler = new InjectSkillListing();
      handler.handle(scope);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that WrappedCheckedException is thrown when an installed_plugins.json plugin entry
   * is missing the required "installPath" field.
   * <p>
   * A plugin entry like {@code [{"foo": "bar"}]} with no {@code installPath} key must cause an error
   * rather than silently skipping the plugin.
   */
  @Test(expectedExceptions = WrappedCheckedException.class)
  public void missingInstallPathInPluginEntryThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"startup\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      Path pluginsDir = tempDir.resolve("plugins");
      Files.createDirectories(pluginsDir);
      // Plugin entry has no "installPath" key — must trigger an error
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "cat@cat": [
              {"foo": "bar"}
            ]
          }
        }
        """);

      InjectSkillListing handler = new InjectSkillListing();
      handler.handle(scope);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

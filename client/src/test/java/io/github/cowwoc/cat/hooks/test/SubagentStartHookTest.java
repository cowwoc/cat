/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.HookResult;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.SubagentStartHook;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SubagentStartHook.
 */
public final class SubagentStartHookTest
{
  /**
   * Creates a HookInput from a JSON string.
   *
   * @param mapper the JSON mapper
   * @param json   the JSON input string
   * @return the parsed HookInput
   */
  private HookInput createInput(JsonMapper mapper, String json)
  {
    return HookInput.readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Sets up a fake plugin in the given configDir with one skill entry.
   * <p>
   * Creates {@code configDir/plugins/installed_plugins.json} pointing to a fake plugin root
   * that contains one skill with the given name and description.
   *
   * @param configDir   the Claude config directory (used as claudeConfigDir in TestJvmScope)
   * @param prefix      the plugin prefix (e.g. "fake" → skill name "fake:skill-name")
   * @param skillName   the skill directory name
   * @param description the skill description in SKILL.md frontmatter
   * @param modelInvocable whether the skill is model-invocable (false excludes it)
   * @return the fake plugin root path
   * @throws IOException if directory creation fails
   */
  private static Path setupFakePlugin(Path configDir, String prefix, String skillName,
    String description, boolean modelInvocable) throws IOException
  {
    Path pluginsDir = configDir.resolve("plugins");
    Files.createDirectories(pluginsDir);

    Path fakePluginRoot = configDir.resolve("fake-plugin-" + prefix);
    Path skillDir = fakePluginRoot.resolve("skills/" + skillName);
    Files.createDirectories(skillDir);

    String frontmatter;
    if (modelInvocable)
    {
      frontmatter = """
        ---
        description: %s
        ---
        # %s
        """.formatted(description, skillName);
    }
    else
    {
      frontmatter = """
        ---
        description: %s
        model-invocable: false
        ---
        # %s
        """.formatted(description, skillName);
    }
    Files.writeString(skillDir.resolve("SKILL.md"), frontmatter);

    Path installedPluginsFile = pluginsDir.resolve("installed_plugins.json");
    String existingContent;
    if (Files.exists(installedPluginsFile))
      existingContent = Files.readString(installedPluginsFile);
    else
      existingContent = null;

    String newEntry = """
        "%s@%s": [
          {"installPath": "%s"}
        ]
      """.formatted(prefix, prefix, fakePluginRoot.toString());

    if (existingContent == null)
    {
      Files.writeString(installedPluginsFile, """
        {
          "plugins": {
            %s
          }
        }
        """.formatted(newEntry));
    }
    else
    {
      // Append to existing plugins object
      String updated = existingContent.replace("\"plugins\": {",
        "\"plugins\": {\n    " + newEntry + ",");
      Files.writeString(installedPluginsFile, updated);
    }

    return fakePluginRoot;
  }

  // --- SubagentStartHook tests ---

  /**
   * Verifies that SubagentStartHook returns empty output when no skills are discoverable.
   * <p>
   * In a test environment with empty temp dirs, SkillDiscovery finds no installed plugins,
   * no project commands, and no user skills, so the result should be an empty JSON object.
   */
  @Test
  public void subagentStartHookReturnsEmptyWhenNoSkills() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-subagent-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = createInput(mapper, "{\"agent_id\": \"agent-1\", \"agent_type\": \"task\"}");
      HookOutput output = new HookOutput(scope);
      HookResult result = new SubagentStartHook(scope).run(input, output);

      requireThat(result.output(), "output").isEqualTo("{}");
      requireThat(result.warnings(), "warnings").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that SubagentStartHook returns hookSpecificOutput with SubagentStart event name
   * when skills are present.
   * <p>
   * Creates a minimal plugin with a skill directory containing a valid SKILL.md that has a
   * description in its frontmatter, then verifies the hook injects the skill listing.
   */
  @Test
  public void subagentStartHookInjectsSkillListingWhenSkillsPresent() throws IOException
  {
    // claudeConfigDir == claudeProjectDir in TestJvmScope(projectDir, pluginRoot)
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      setupFakePlugin(configDir, "fake", "my-test-skill", "A test skill for unit testing.", true);
      try (JvmScope scope = new TestJvmScope(configDir, pluginRoot))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{\"agent_id\": \"agent-1\", \"agent_type\": \"task\"}");
        HookOutput output = new HookOutput(scope);
        HookResult result = new SubagentStartHook(scope).run(input, output);

        requireThat(result.output(), "output").contains("hookSpecificOutput");
        requireThat(result.output(), "output").contains("SubagentStart");
        requireThat(result.output(), "output").contains("Skill Instructions");
        requireThat(result.output(), "output").contains("Skill tool");
        requireThat(result.output(), "output").contains("my-test-skill");
        requireThat(result.output(), "output").contains("A test skill for unit testing.");
        requireThat(result.warnings(), "warnings").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(configDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that SubagentStartHook output is valid JSON with hookSpecificOutput structure.
   */
  @Test
  public void subagentStartHookProducesValidJsonStructure() throws IOException
  {
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      setupFakePlugin(configDir, "sample", "sample-skill", "Sample skill description.", true);
      try (JvmScope scope = new TestJvmScope(configDir, pluginRoot))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookInput input = createInput(mapper, "{}");
        HookOutput output = new HookOutput(scope);
        HookResult result = new SubagentStartHook(scope).run(input, output);

        JsonNode json = mapper.readTree(result.output());
        requireThat(json.has("hookSpecificOutput"), "hasHookSpecificOutput").isTrue();

        JsonNode hookOutput = json.get("hookSpecificOutput");
        String hookEventName = hookOutput.get("hookEventName").asString();
        requireThat(hookEventName, "hookEventName").isEqualTo("SubagentStart");
        String additionalContext = hookOutput.get("additionalContext").asString();
        requireThat(additionalContext, "additionalContext").contains("Skill Instructions");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(configDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that SkillDiscovery.getMainAgentSkillListing returns empty string when no skills are discoverable.
   */
  @Test
  public void getMainAgentSkillListingReturnsEmptyStringWhenNoSkills() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-subagent-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      String listing = SkillDiscovery.getMainAgentSkillListing(scope);
      requireThat(listing, "listing").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that SkillDiscovery.getMainAgentSkillListing includes the correct header and skill entries.
   */
  @Test
  public void getMainAgentSkillListingIncludesHeaderAndEntries() throws IOException
  {
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      setupFakePlugin(configDir, "fmt", "format-test-skill", "Format test skill description.", true);
      try (JvmScope scope = new TestJvmScope(configDir, pluginRoot))
      {
        String listing = SkillDiscovery.getMainAgentSkillListing(scope);
        requireThat(listing, "listing").contains("The following skills are available.");
        requireThat(listing, "listing").contains("load-skill.sh");
        requireThat(listing, "listing").contains("fmt:format-test-skill");
        requireThat(listing, "listing").contains("Format test skill description.");
        requireThat(listing, "listing").contains("fmt:format-test-skill: Format test skill description.");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(configDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that SkillDiscovery.getMainAgentSkillListing excludes skills with model-invocable: false.
   */
  @Test
  public void getMainAgentSkillListingExcludesNonModelInvocableSkills() throws IOException
  {
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // Both skills go into the same fake plugin - use same prefix
      Path pluginsDir = configDir.resolve("plugins");
      Files.createDirectories(pluginsDir);

      Path fakePluginRoot = configDir.resolve("fake-plugin-excl");
      Path includedDir = fakePluginRoot.resolve("skills/included-skill");
      Path excludedDir = fakePluginRoot.resolve("skills/excluded-skill");
      Files.createDirectories(includedDir);
      Files.createDirectories(excludedDir);
      Files.writeString(includedDir.resolve("SKILL.md"), """
        ---
        description: This skill should appear.
        ---
        # Included Skill
        """);
      Files.writeString(excludedDir.resolve("SKILL.md"), """
        ---
        description: This skill should not appear.
        model-invocable: false
        ---
        # Excluded Skill
        """);
      Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
        {
          "plugins": {
            "excl@excl": [
              {"installPath": "%s"}
            ]
          }
        }
        """.formatted(fakePluginRoot.toString()));

      try (JvmScope scope = new TestJvmScope(configDir, pluginRoot))
      {
        String listing = SkillDiscovery.getMainAgentSkillListing(scope);
        requireThat(listing, "listing").contains("excl:included-skill");
        requireThat(listing, "listing").doesNotContain("excl:excluded-skill");
        requireThat(listing, "listing").doesNotContain("This skill should not appear.");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(configDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}

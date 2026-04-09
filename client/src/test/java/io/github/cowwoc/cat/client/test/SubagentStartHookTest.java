/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.HookResult;
import io.github.cowwoc.cat.claude.hook.SubagentStartHook;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SubagentStartHook.
 */
public final class SubagentStartHookTest
{
  /**
   * Sets up a fake plugin in the given configDir with one skill entry.
   * <p>
   * Creates {@code configDir/plugins/installed_plugins.json} pointing to a fake plugin root
   * that contains one skill with the given name and description.
   *
   * @param configDir   the Claude config directory (used as claudeConfigPath in TestClaudeHook)
   * @param prefix      the plugin prefix (e.g. "fake" → skill name "fake:skill-name")
   * @param skillName   the skill directory name
   * @param description the skill description in SKILL.md frontmatter
   * @param modelInvocable whether the model may invoke this skill (false adds disable-model-invocation: true)
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
        disable-model-invocation: true
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
   * Verifies that SubagentStartHook injects agent ID context even when no skills are discoverable.
   * <p>
   * In a test environment with empty temp dirs, SkillDiscovery finds no installed plugins,
   * no project commands, and no user skills. The agent ID context is always injected.
   */
  @Test
  public void subagentStartHookReturnsEmptyWhenNoSkills() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-subagent-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"agent_type\": \"task\"}",
      projectPath, pluginRoot, projectPath))
    {
      HookResult result = new SubagentStartHook(scope).run(scope);

      requireThat(result.output(), "output").contains("Your CAT agent ID is:");
      requireThat(result.output(), "output").contains("test-session/subagents/agent-1");
      requireThat(result.warnings(), "warnings").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
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
    // claudeConfigPath == claudeProjectPath in TestClaudeHook(projectPath, pluginRoot, projectPath)
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      setupFakePlugin(configDir, "fake", "my-test-skill", "A test skill for unit testing.", true);
      try (TestClaudeHook scope = new TestClaudeHook(
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"agent_type\": \"task\"}",
        configDir, pluginRoot, configDir))
      {
        HookResult result = new SubagentStartHook(scope).run(scope);

        requireThat(result.output(), "output").contains("hookSpecificOutput");
        requireThat(result.output(), "output").contains("SubagentStart");
        requireThat(result.output(), "output").contains("Available skills");
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
   * Verifies that plugin/rules/skill-invocation-args.md contains cat_agent_id argument guidance so
   * agents know to pass the injected cat_agent_id as the first argument when invoking skills.
   * <p>
   * This prevents agents from passing branch names, skill names, or other wrong values as the
   * first argument to skills that require a cat_agent_id.
   */
  @Test
  public void skillInvocationArgsRuleContainsCatAgentIdGuidance() throws IOException
  {
    // Find workspace root (contains plugin/ and client/ directories)
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path workspaceRoot = null;
    while (current != null)
    {
      if (Files.exists(current.resolve("plugin")) && Files.exists(current.resolve("client")))
      {
        workspaceRoot = current;
        break;
      }
      current = current.getParent();
    }
    requireThat(workspaceRoot, "workspaceRoot").isNotNull();

    Path rulesFile = workspaceRoot.resolve("plugin/rules/skill-invocation-args.md");
    requireThat(Files.exists(rulesFile), "rulesFileExists").isTrue();

    String content = Files.readString(rulesFile);
    requireThat(content, "content").contains("args");
    requireThat(content, "content").contains("cat_agent_id");
    requireThat(content, "content").contains("first argument");
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
      try (TestClaudeHook scope = new TestClaudeHook(
        "{\"session_id\": \"test-session\", \"agent_id\": \"sample-agent\"}",
        configDir, pluginRoot, configDir))
      {
        JsonMapper mapper = scope.getJsonMapper();
        HookResult result = new SubagentStartHook(scope).run(scope);

        JsonNode json = mapper.readTree(result.output());
        requireThat(json.has("hookSpecificOutput"), "hasHookSpecificOutput").isTrue();

        JsonNode hookOutput = json.get("hookSpecificOutput");
        String hookEventName = hookOutput.get("hookEventName").asString();
        requireThat(hookEventName, "hookEventName").isEqualTo("SubagentStart");
        String additionalContext = hookOutput.get("additionalContext").asString();
        requireThat(additionalContext, "additionalContext").contains("Available skills");
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
    Path projectPath = Files.createTempDirectory("cat-test-subagent-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
    {
      String listing = SkillDiscovery.getMainAgentSkillListing(scope);
      requireThat(listing, "listing").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
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
      try (TestClaudeHook scope = new TestClaudeHook(configDir, pluginRoot, configDir))
      {
        String listing = SkillDiscovery.getMainAgentSkillListing(scope);
        requireThat(listing, "listing").contains("The following skills are available.");
        requireThat(listing, "listing").contains("get-skill");
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
   * Verifies that SkillDiscovery.getMainAgentSkillListing excludes skills with disable-model-invocation: true.
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
        disable-model-invocation: true
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

      try (TestClaudeHook scope = new TestClaudeHook(configDir, pluginRoot, configDir))
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

  /**
   * Verifies that SkillDiscovery.getSubagentSkillListing returns only the skill list without
   * behavioral preamble text. Behavioral instructions are in plugin/rules/subagent-skill-instructions.md.
   */
  @Test
  public void getSubagentSkillListingReturnsOnlySkillListNoBehavioralPreamble() throws IOException
  {
    Path configDir = Files.createTempDirectory("cat-test-subagent-config-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      setupFakePlugin(configDir, "sub", "sub-test-skill", "A subagent test skill.", true);
      try (TestClaudeHook scope = new TestClaudeHook(configDir, pluginRoot, configDir))
      {
        String listing = SkillDiscovery.getSubagentSkillListing(scope);
        requireThat(listing, "listing").contains("**Available skills:**");
        requireThat(listing, "listing").contains("sub:sub-test-skill");
        requireThat(listing, "listing").contains("A subagent test skill.");
        requireThat(listing, "listing").doesNotContain("BLOCKING REQUIREMENT");
        requireThat(listing, "listing").doesNotContain("NEVER mention a skill");
        requireThat(listing, "listing").doesNotContain("How to invoke");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(configDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plugin/rules/subagent-skill-instructions.md exists and contains the behavioral
   * instructions for subagents about when and how to invoke skills.
   */
  @Test
  public void subagentSkillInstructionsRuleContainsBehavioralGuidance() throws IOException
  {
    // Find workspace root (contains plugin/ and client/ directories)
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path workspaceRoot = null;
    while (current != null)
    {
      if (Files.exists(current.resolve("plugin")) && Files.exists(current.resolve("client")))
      {
        workspaceRoot = current;
        break;
      }
      current = current.getParent();
    }
    requireThat(workspaceRoot, "workspaceRoot").isNotNull();

    Path rulesFile = workspaceRoot.resolve("plugin/rules/subagent-skill-instructions.md");
    requireThat(Files.exists(rulesFile), "rulesFileExists").isTrue();

    String content = Files.readString(rulesFile);
    requireThat(content, "content").contains("BLOCKING REQUIREMENT");
    requireThat(content, "content").contains("NEVER mention a skill");
    requireThat(content, "content").contains("Skill tool");
  }

  // ---- getCatRules behavior: blank vs populated subagent_type ----

  /**
   * Verifies that getCatRules (via run()) includes a rule with no subAgents restriction when
   * subagent_type is blank. Rules with null subAgents should reach all subagents regardless of type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesBlankSubagentTypeMatchesAllRule() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-getrules-blank-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}",
      projectPath, pluginRoot, projectPath))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      // No subAgents frontmatter → null → matches all subagents
      Files.writeString(rulesDir.resolve("universal.md"), """
        ---
        mainAgent: false
        ---
        # Universal subagent content
        Applies to any subagent.
        """);

      HookResult result = new SubagentStartHook(scope).run(scope);

      requireThat(result.output(), "output").contains("Universal subagent content");
      requireThat(result.output(), "output").contains("Applies to any subagent.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that getCatRules (via run()) includes a specific-type rule when
   * subagent_type matches the rule's subAgents value.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesPopulatedSubagentTypeMatchesSpecificRule() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-getrules-specific-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      projectPath, pluginRoot, projectPath))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute specific content
        Only for cat:work-execute.
        """);

      HookResult result = new SubagentStartHook(scope).run(scope);

      requireThat(result.output(), "output").contains("Work execute specific content");
      requireThat(result.output(), "output").contains("Only for cat:work-execute.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that getCatRules (via run()) excludes a specific-type rule when
   * subagent_type does not match the rule's subAgents value.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesPopulatedSubagentTypeExcludesNonMatchingRule() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-getrules-nomatch-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"Explore\"}",
      projectPath, pluginRoot, projectPath))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute only content
        Should not appear for Explore.
        """);

      // Different subagent type — rule should not match
      HookResult result = new SubagentStartHook(scope).run(scope);

      requireThat(result.output(), "output").doesNotContain("Work execute only content");
      requireThat(result.output(), "output").doesNotContain("Should not appear for Explore.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ---- getAgentIdContext behavior ----

  /**
   * Verifies that the agent ID context appears in run() output when both agent_id and session_id
   * are present and non-blank.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getAgentIdContextIncludedWhenAgentIdAndSessionIdPresent() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-agent-id-present-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session-abc\", \"agent_id\": \"subagent-xyz\"}",
      projectPath, pluginRoot, projectPath))
    {
      HookResult result = new SubagentStartHook(scope).run(scope);

      requireThat(result.output(), "output").contains("Your CAT agent ID is:");
      requireThat(result.output(), "output").contains("test-session-abc/subagents/subagent-xyz");
      requireThat(result.output(), "output").contains("You MUST pass this as the first argument");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that run() throws IllegalArgumentException when agent_id is blank.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*agent_id.*")
  public void getAgentIdContextAbsentWhenAgentIdBlank() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-agent-id-blank-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session-abc\", \"agent_id\": \"\"}",
      projectPath, pluginRoot, projectPath))
    {
      new SubagentStartHook(scope).run(scope);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that constructing a TestClaudeHook with a blank session_id throws IllegalArgumentException.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*sessionId is empty.*")
  public void getAgentIdContextAbsentWhenSessionIdBlank() throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-test-session-id-blank-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      new TestClaudeHook("{\"session_id\": \"\", \"agent_id\": \"subagent-xyz\"}", projectPath, pluginRoot,
        projectPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}

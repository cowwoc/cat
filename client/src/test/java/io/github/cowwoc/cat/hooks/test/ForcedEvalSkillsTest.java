/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.prompt.ForcedEvalSkills;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ForcedEvalSkills handler.
 * <p>
 * Tests verify that the forced skill evaluation instruction is injected for every prompt,
 * and that skills are discovered dynamically from all skill sources (plugins, project commands, user skills).
 */
public final class ForcedEvalSkillsTest
{
  /**
   * Creates a temporary plugin root with mock skill files for testing.
   * <p>
   * Also creates supporting mock structures in the project dir (which doubles as config dir in TestJvmScope):
   * <ul>
   *   <li>{@code plugins/installed_plugins.json} — pointing to the plugin root</li>
   *   <li>{@code .claude/commands/} — project command files</li>
   *   <li>{@code skills/} — user skill directories</li>
   * </ul>
   *
   * @param projectDir the temporary project directory (used as config dir in TestJvmScope)
   * @return the path to the created temporary plugin root
   * @throws IOException if directory creation fails
   */
  private Path createMockPluginRoot(Path projectDir) throws IOException
  {
    Path pluginRoot = Files.createTempDirectory("test-plugin-forced-eval");
    Path skillsDir = pluginRoot.resolve("skills");
    Files.createDirectories(skillsDir);

    // Create a regular skill
    Path addSkill = skillsDir.resolve("add");
    Files.createDirectories(addSkill);
    Files.writeString(addSkill.resolve("SKILL.md"), """
      ---
      description: Add a new issue/task to a version.
      ---
      """);

    // Create a regular skill with multi-line description
    Path workSkill = skillsDir.resolve("work");
    Files.createDirectories(workSkill);
    Files.writeString(workSkill.resolve("SKILL.md"), """
      ---
      description: >
        Start working on, resume, or continue an existing issue or task.
        Trigger words: "work on", "resume".
      ---
      """);

    // Create a -first-use skill (should be excluded)
    Path firstUseSkill = skillsDir.resolve("add-first-use");
    Files.createDirectories(firstUseSkill);
    Files.writeString(firstUseSkill.resolve("SKILL.md"), """
      ---
      description: First use of add skill.
      ---
      """);

    // Create a user-invocable: false skill (should be excluded)
    Path internalSkill = skillsDir.resolve("work-merge");
    Files.createDirectories(internalSkill);
    Files.writeString(internalSkill.resolve("SKILL.md"), """
      ---
      description: Merge work into main branch.
      user-invocable: false
      ---
      """);

    // Create installed_plugins.json pointing to this plugin root (configDir = projectDir in TestJvmScope)
    Path pluginsDir = projectDir.resolve("plugins");
    Files.createDirectories(pluginsDir);
    Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
      {
        "version": 2,
        "plugins": {
          "cat@cat": [
            {
              "scope": "user",
              "installPath": "%s",
              "version": "2.1"
            }
          ]
        }
      }
      """.formatted(pluginRoot.toString()));

    // Create project commands in .claude/commands/
    Path commandsDir = projectDir.resolve(".claude/commands");
    Files.createDirectories(commandsDir);
    Files.writeString(commandsDir.resolve("review.md"), """
      ---
      description: Review pull request changes.
      ---
      # Review PR
      Review the current pull request.
      """);
    Files.writeString(commandsDir.resolve("deploy.md"), """
      ---
      description: Deploy to production environment.
      ---
      # Deploy
      Deploy the application.
      """);
    // Command without description (should be skipped)
    Files.writeString(commandsDir.resolve("nodesc.md"), """
      # No Description Command
      This command has no frontmatter description.
      """);

    // Create user skills in skills/
    Path userSkillsDir = projectDir.resolve("skills");
    Files.createDirectories(userSkillsDir);
    Path mySkill = userSkillsDir.resolve("my-skill");
    Files.createDirectories(mySkill);
    Files.writeString(mySkill.resolve("SKILL.md"), """
      ---
      description: My custom user skill.
      ---
      """);
    // User skill that is user-invocable: false (should be excluded)
    Path internalUserSkill = userSkillsDir.resolve("internal-user-skill");
    Files.createDirectories(internalUserSkill);
    Files.writeString(internalUserSkill.resolve("SKILL.md"), """
      ---
      description: Internal user skill.
      user-invocable: false
      ---
      """);

    return pluginRoot;
  }

  /**
   * Verifies that any non-blank prompt returns the forced evaluation instruction.
   */
  @Test
  public void anyPromptReturnsInstruction() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("Please help me with my code.", "test-session-1");
      requireThat(result, "result").contains("SKILL ACTIVATION CHECK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that blank prompt throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void blankPromptThrowsException() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      handler.check("", "test-session-2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that discovered plugin skills appear in the instruction.
   */
  @Test
  public void instructionContainsDiscoveredSkills() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-3");
      requireThat(result, "result").contains("cat:add");
      requireThat(result, "result").contains("cat:work");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that -first-use skills are excluded from the instruction.
   */
  @Test
  public void firstUseSkillsExcluded() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-4");
      requireThat(result, "result").doesNotContain("cat:add-first-use");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that user-invocable: false skills are excluded from the instruction.
   */
  @Test
  public void userInvocableFalseSkillsExcluded() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-5");
      requireThat(result, "result").doesNotContain("cat:work-merge");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that the instruction includes the Skill tool invocation requirement.
   */
  @Test
  public void instructionIncludesSkillToolRequirement() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-6");
      requireThat(result, "result").contains("Skill tool BEFORE doing anything else");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that every call returns a non-empty instruction regardless of session ID.
   */
  @Test
  public void returnsInstructionForDifferentSessions() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result1 = handler.check("prompt one", "session-abc");
      String result2 = handler.check("prompt two", "session-xyz");
      requireThat(result1, "result1").isNotEmpty();
      requireThat(result2, "result2").isNotEmpty();
      requireThat(result1, "result1").isEqualTo(result2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that null scope throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullScopeThrowsException()
  {
    new ForcedEvalSkills(null);
  }

  /**
   * Verifies that null sessionId throws NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullSessionIdThrowsException() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      handler.check("some prompt", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that blank sessionId throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void blankSessionIdThrowsException() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      handler.check("some prompt", "  ");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that an empty plugin skills directory produces a minimal instruction.
   */
  @Test
  public void emptySkillsDirProducesInstruction() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = Files.createTempDirectory("test-plugin-empty");
    Files.createDirectories(pluginRoot.resolve("skills"));
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session");
      requireThat(result, "result").contains("SKILL ACTIVATION CHECK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that project commands with frontmatter description are discovered and included.
   */
  @Test
  public void projectCommandsWithDescriptionAreDiscovered() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-cmd");
      requireThat(result, "result").contains("review");
      requireThat(result, "result").contains("Review pull request changes.");
      requireThat(result, "result").contains("deploy");
      requireThat(result, "result").contains("Deploy to production environment.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that project commands without a description are excluded from the instruction.
   */
  @Test
  public void projectCommandsWithoutDescriptionAreExcluded() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-nodesc");
      requireThat(result, "result").doesNotContain("nodesc");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that user skills are discovered and included in the instruction.
   */
  @Test
  public void userSkillsAreDiscovered() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-user");
      requireThat(result, "result").contains("my-skill");
      requireThat(result, "result").contains("My custom user skill.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that user skills with user-invocable: false are excluded.
   */
  @Test
  public void userInvocableFalseUserSkillsAreExcluded() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");
    Path pluginRoot = createMockPluginRoot(projectDir);
    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-user-internal");
      requireThat(result, "result").doesNotContain("internal-user-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that multiple plugins are all discovered from installed_plugins.json.
   */
  @Test
  public void multiplePluginsAreDiscovered() throws IOException
  {
    Path projectDir = Files.createTempDirectory("project");

    // Create first plugin
    Path pluginRoot1 = Files.createTempDirectory("test-plugin-1");
    Path skillsDir1 = pluginRoot1.resolve("skills");
    Files.createDirectories(skillsDir1);
    Path skill1 = skillsDir1.resolve("plugin1-skill");
    Files.createDirectories(skill1);
    Files.writeString(skill1.resolve("SKILL.md"), """
      ---
      description: Skill from plugin 1.
      ---
      """);

    // Create second plugin
    Path pluginRoot2 = Files.createTempDirectory("test-plugin-2");
    Path skillsDir2 = pluginRoot2.resolve("skills");
    Files.createDirectories(skillsDir2);
    Path skill2 = skillsDir2.resolve("plugin2-skill");
    Files.createDirectories(skill2);
    Files.writeString(skill2.resolve("SKILL.md"), """
      ---
      description: Skill from plugin 2.
      ---
      """);

    // Create installed_plugins.json with both plugins
    Path pluginsDir = projectDir.resolve("plugins");
    Files.createDirectories(pluginsDir);
    Files.writeString(pluginsDir.resolve("installed_plugins.json"), """
      {
        "version": 2,
        "plugins": {
          "cat@cat": [
            {
              "scope": "user",
              "installPath": "%s",
              "version": "2.1"
            }
          ],
          "myorg@myplugin": [
            {
              "scope": "user",
              "installPath": "%s",
              "version": "1.0"
            }
          ]
        }
      }
      """.formatted(pluginRoot1.toString(), pluginRoot2.toString()));

    try (JvmScope scope = new TestJvmScope(projectDir, pluginRoot1))
    {
      ForcedEvalSkills handler = new ForcedEvalSkills(scope);
      String result = handler.check("some prompt", "test-session-multi");
      requireThat(result, "result").contains("cat:plugin1-skill");
      requireThat(result, "result").contains("myorg:plugin2-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot1);
      TestUtils.deleteDirectoryRecursively(pluginRoot2);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies extractFrontmatter returns null when no frontmatter markers present.
   */
  @Test
  public void extractFrontmatterReturnsNullWhenNone()
  {
    String content = "No frontmatter here\njust content";
    String result = ForcedEvalSkills.extractFrontmatter(content);
    requireThat(result, "result").isNull();
  }

  /**
   * Verifies extractFrontmatter returns the content between markers.
   */
  @Test
  public void extractFrontmatterReturnsContent()
  {
    String content = "---\ndescription: Test skill\n---\nBody here";
    String result = ForcedEvalSkills.extractFrontmatter(content);
    requireThat(result, "result").contains("description: Test skill");
  }

  /**
   * Verifies isUserInvocableFalse returns true when user-invocable: false is present.
   */
  @Test
  public void isUserInvocableFalseReturnsTrueWhenFalse()
  {
    String frontmatter = "description: Some skill\nuser-invocable: false\n";
    requireThat(ForcedEvalSkills.isUserInvocableFalse(frontmatter), "result").isTrue();
  }

  /**
   * Verifies isUserInvocableFalse returns false when user-invocable: true is present.
   */
  @Test
  public void isUserInvocableFalseReturnsFalseWhenTrue()
  {
    String frontmatter = "description: Some skill\nuser-invocable: true\n";
    requireThat(ForcedEvalSkills.isUserInvocableFalse(frontmatter), "result").isFalse();
  }

  /**
   * Verifies isUserInvocableFalse returns false when user-invocable is absent.
   */
  @Test
  public void isUserInvocableFalseReturnsFalseWhenAbsent()
  {
    String frontmatter = "description: Some skill\n";
    requireThat(ForcedEvalSkills.isUserInvocableFalse(frontmatter), "result").isFalse();
  }

  /**
   * Verifies extractDescription handles inline description.
   */
  @Test
  public void extractDescriptionHandlesInlineValue()
  {
    String frontmatter = "description: Add a task.\nother: value";
    String result = ForcedEvalSkills.extractDescription(frontmatter);
    requireThat(result, "result").isEqualTo("Add a task.");
  }

  /**
   * Verifies extractDescription handles block scalar (>) format.
   */
  @Test
  public void extractDescriptionHandlesBlockScalar()
  {
    String frontmatter = "description: >\n  First line.\n  Second line.\nother: value";
    String result = ForcedEvalSkills.extractDescription(frontmatter);
    requireThat(result, "result").contains("First line.");
    requireThat(result, "result").contains("Second line.");
  }

  /**
   * Verifies extractDescription returns null when description is absent.
   */
  @Test
  public void extractDescriptionReturnsNullWhenAbsent()
  {
    String frontmatter = "allowed-tools:\n  - Read\n";
    String result = ForcedEvalSkills.extractDescription(frontmatter);
    requireThat(result, "result").isNull();
  }
}

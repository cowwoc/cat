/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GetSkill;
import org.testng.annotations.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetSkill functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetSkillTest
{
  /**
   * Verifies that constructor rejects null scope.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void constructorRejectsNullScope() throws IOException
  {
    new GetSkill(null, List.of("session123"));
  }

  /**
   * Verifies that constructor rejects empty skill args (no agent ID provided).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is required.*")
  public void constructorRejectsEmptySkillArgs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new GetSkill(scope, List.of());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that blank agent ID throws IllegalArgumentException because a blank ID is a skill
   * misconfiguration — the SKILL.md must provide a non-blank $0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is blank.*")
  public void constructorBlankAgentIdThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new GetSkill(scope, List.of(""));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that whitespace-only agent ID throws IllegalArgumentException because a blank ID is a skill
   * misconfiguration — the SKILL.md must provide a non-blank $0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId is blank.*")
  public void constructorWhitespaceAgentIdThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new GetSkill(scope, List.of("   "));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor fails with IOException when pluginRoot does not exist.
   * <p>
   * Skill loading cannot proceed without a valid plugin root directory, so the constructor
   * fails fast rather than silently returning empty content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Plugin root directory does not exist.*")
  public void constructorFailsWhenPluginRootDoesNotExist() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("test-plugin-");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      // Delete the plugin root after TestJvmScope is constructed so GetSkill sees a missing directory
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      new GetSkill(scope, List.of(UUID.randomUUID().toString()));
    }
  }

  /**
   * Verifies that constructor succeeds when projectPath does not exist.
   * <p>
   * The project directory is stored as a string for variable substitution only; it is not
   * validated as an existing path during construction.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void constructorSucceedsWhenProjectDirDoesNotExist() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("test-plugin-");
    Path nonExistentProjectDir = tempPluginRoot.resolve("does-not-exist");
    try (JvmScope scope = new TestJvmScope(nonExistentProjectDir, tempPluginRoot))
    {
      // Constructor should succeed even when projectPath does not point to an existing directory
      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      requireThat(loader, "loader").isNotNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load passes through CLAUDE_PLUGIN_ROOT variable in content body unchanged.
   * <p>
   * Variable substitution applies only inside {@code !} directive strings; content body
   * passes through to Claude Code unmodified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughPluginRootVariableInContentBody() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Path: ${CLAUDE_PLUGIN_ROOT}/file.txt
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Path: ${CLAUDE_PLUGIN_ROOT}/file.txt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load passes through CLAUDE_SESSION_ID variable in content body unchanged.
   * <p>
   * Variable substitution applies only inside {@code !} directive strings; content body
   * passes through to Claude Code unmodified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughSessionIdVariableInContentBody() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Session: ${CLAUDE_SESSION_ID}
""");

      String uniqueAgentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope,
        List.of(uniqueAgentId));
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Session: ${CLAUDE_SESSION_ID}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load passes through CLAUDE_PROJECT_DIR variable in content body unchanged.
   * <p>
   * Variable substitution applies only inside {@code !} directive strings; content body
   * passes through to Claude Code unmodified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughProjectDirVariableInContentBody() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    Path tempProjectDir = Files.createTempDirectory("get-skill-project");
    try (JvmScope scope = new TestJvmScope(tempProjectDir, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Project: ${CLAUDE_PROJECT_DIR}/data
""");

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Project: ${CLAUDE_PROJECT_DIR}/data");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that load loads content on first invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsContentOnFirstInvocation() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Full skill content here
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Full skill content here");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load returns dynamic reference on second invocation for skills without a preprocessor
   * directive.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsReferenceOnSecondInvocation() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Full skill content
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").contains("Full skill content");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").
        contains("skill instructions were already loaded").
        contains("Use the Skill tool to invoke this skill again").
        doesNotContain("Full skill content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load throws NoSuchFileException for skills without a first-use.md file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NoSuchFileException.class)
  public void loadHandlesMissingContentFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/empty-skill");
      Files.createDirectories(skillDir);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      loader.load("empty-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load rejects null skill name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*skillName.*")
  public void loadRejectsNullSkillName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      loader.load(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load rejects empty skill name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*skillName.*")
  public void loadRejectsEmptySkillName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      loader.load("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that content body variable references pass through unchanged.
   * <p>
   * Variable substitution applies only inside {@code !} directive strings; content body
   * passes through to Claude Code unmodified, including adjacent variable references.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughContentBodyVariableReferences() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    Path tempProjectDir = Files.createTempDirectory("get-skill-project");
    try (JvmScope scope = new TestJvmScope(tempProjectDir, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Path: ${CLAUDE_PLUGIN_ROOT}/contains_${CLAUDE_SESSION_ID}
Project: ${CLAUDE_PROJECT_DIR}/session_${CLAUDE_SESSION_ID}
""");

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Path: ${CLAUDE_PLUGIN_ROOT}/contains_${CLAUDE_SESSION_ID}").
        contains("Project: ${CLAUDE_PROJECT_DIR}/session_${CLAUDE_SESSION_ID}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that load passes through undefined variables as literals.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughUndefinedVariable() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Value: ${UNDEFINED_VAR}
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Value: ${UNDEFINED_VAR}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load strips YAML frontmatter from companion SKILL.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsFrontmatter() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"),
        "---\n" +
        "name: test-skill\n" +
        "---\n" +
        "# Skill Content\n");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("# Skill Content").
        doesNotContain("name: test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load preserves content without a license header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPreservesContentWithoutLicenseHeader() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
# No License Header
Regular content here
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("# No License Header").
        contains("Regular content here");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that preprocessor directive passes through unchanged when launcher not found.
   * <p>
   * When no matching launcher exists in {@code client/bin/}, {@code executeDirective} returns
   * the original directive text verbatim (including already-expanded variables).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughUnknownLauncher() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Output: !`"/nonexistent/client/bin/nonexistent-launcher"`
Done
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("!`\"/nonexistent/client/bin/nonexistent-launcher\"`").
        contains("Done");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that variable expansion applies only inside directive strings, not content body prose.
   * <p>
   * Content body references to {@code ${CLAUDE_PLUGIN_ROOT}} pass through unchanged, while the same
   * variable inside an {@code !} directive string is expanded before launcher lookup.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsVariablesInDirectivesButNotContentBody() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
Directive: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-launcher"`
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      // Prose passes through unchanged; directive expands the variable before lookup
      requireThat(result, "result").
        contains("Root: ${CLAUDE_PLUGIN_ROOT}").
        contains("Directive:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that preprocessor directive invokes SkillOutput and replaces directive with output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadInvokesSkillOutputForKnownLauncher() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("NO_ARGS_OUTPUT").
        contains("Done").
        doesNotContain("!`");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that IOException from SkillOutput.getOutput() propagates as IOException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "simulated IO failure")
  public void loadPropagatesIoExceptionFromSkillOutput() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsIo "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      loader.load("test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that RuntimeException from SkillOutput.getOutput() returns a user-friendly error message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsErrorStringForRuntimeException() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsRuntime "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Preprocessor Error").
        contains("simulated runtime failure").
        contains("/cat:feedback");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that InvocationTargetException from constructor returns a user-friendly error message with cause.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsErrorStringForConstructorException() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsFromConstructor "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Preprocessor Error").
        contains("constructor failure").
        contains("/cat:feedback");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that exception with null message uses class name in the user-friendly error message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsClassNameForNullExceptionMessage() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsNullMessage "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Preprocessor Error").
        contains("java.lang.IllegalStateException").
        contains("/cat:feedback");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that extractClassName handles multi-line launcher scripts with line continuations.
   */
  @Test
  public void extractClassNameHandlesMultiLineLauncher()
  {
    String launcherContent = """
      #!/bin/sh
      DIR=`dirname $0`
      exec "$DIR/java" \\
        -Xms16m -Xmx96m \\
        -XX:+UseSerialGC \\
        -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.skills.GetStatusOutput "$@"
      """;

    String className = GetSkill.extractClassName(launcherContent);
    requireThat(className, "className").isEqualTo("io.github.cowwoc.cat.hooks.skills.GetStatusOutput");
  }

  /**
   * Verifies that extractClassName handles single-line launcher scripts.
   */
  @Test
  public void extractClassNameHandlesSingleLineLauncher()
  {
    String launcherContent = """
      #!/bin/sh
      exec java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.UserPromptSubmitHook
      """;

    String className = GetSkill.extractClassName(launcherContent);
    requireThat(className, "className").isEqualTo("io.github.cowwoc.cat.hooks.UserPromptSubmitHook");
  }

  /**
   * Verifies that extractClassName returns empty string for launcher without -m flag.
   */
  @Test
  public void extractClassNameReturnsEmptyForMissingModule()
  {
    String launcherContent = """
      #!/bin/sh
      exec java -jar app.jar
      """;

    String className = GetSkill.extractClassName(launcherContent);
    requireThat(className, "className").isEmpty();
  }

  /**
   * Verifies that executeDirective throws IOException when launcher exists but class name
   * cannot be extracted (fail-fast instead of silent fallback).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Failed to extract class name from launcher.*")
  public void loadThrowsWhenClassExtractionFails() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      // Launcher without -m pattern, so extractClassName always returns empty
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/sh
        exec java -jar app.jar "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output" arg1 arg2`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      loader.load("test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that positional arguments are substituted inside {@code !} directive strings.
   * <p>
   * {@code $0} is the agent ID; skill-specific args start at {@code $1}. Directive arguments
   * receive substitution; content body prose does not.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void directivePositionalArgsSubstituted() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"),
        "!`\"" + tempPluginRoot + "/client/bin/test-output\" $1 $2`\n");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString(), "42", "hello"));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("ARGS:42,hello").
        doesNotContain("$1").
        doesNotContain("$2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that content body positional argument references always pass through unchanged.
   * <p>
   * Positional arg substitution applies only inside {@code !} directive strings. In prose,
   * {@code $1} is always preserved verbatim regardless of how many args are provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void contentBodyPositionalArgPassedThrough() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"),
        "First: $1, Second: $2\n");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString(), "value1"));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("First: $1").
        contains("Second: $2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that preprocessor directive passes arguments to SkillOutput.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesArgumentsToSkillOutput() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output" arg1 arg2`
        Done
        """);

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("ARGS:arg1,arg2").
        contains("Done").
        doesNotContain("!`");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that a skill with a single {@code !} preprocessor directive re-executes the directive
   * on subsequent loads and appends the output to the "already loaded" reference.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReExecutesDirectiveOnSubsequentLoad() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Skill instructions here.

        !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        """);

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").
        contains("Skill instructions here.").
        contains("NO_ARGS_OUTPUT");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").
        contains("skill instructions were already loaded").
        contains("Use the Skill tool to invoke this skill again").
        contains("NO_ARGS_OUTPUT").
        doesNotContain("Skill instructions here.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that a skill with more than one {@code !} preprocessor directive fails with a validation
   * error on subsequent load.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*contains 2 preprocessor directives but at most one is allowed.*")
  public void loadFailsOnSubsequentLoadWithMultipleDirectives() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("dir-a"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Skill with two directives.

        !`"${CLAUDE_PLUGIN_ROOT}/client/bin/dir-a"`
        !`"${CLAUDE_PLUGIN_ROOT}/client/bin/dir-a"`
        """);

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));

      // First load succeeds (expands both directives)
      loader.load("test-skill");

      // Second load fails because there are 2 directives
      loader.load("test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load strips YAML frontmatter from a {@code first-use.md} that has only a preprocessor
   * directive (no output tag).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsYamlFrontmatterFromFirstUseMd() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("first-use.md"), """
---
description: "Internal skill. Do not invoke directly."
user-invocable: false
---

Content after frontmatter.
""");

      GetSkill loader = new GetSkill(scope,
        List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Content after frontmatter.").
        doesNotContain("description:").
        doesNotContain("user-invocable:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that content body with a preprocessor directive inside a code block is returned
   * verbatim on first load without being executed.
   * <p>
   * The preprocessor scanner matches {@code !} directives anywhere in the content including
   * code blocks; this test documents current behavior.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsCodeBlockContentVerbatimOnFirstLoad() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);

      Files.writeString(skillDir.resolve("first-use.md"), """
---
description: test
---
# Test

Example:
```xml
@concepts/some-file.md
```

More content here.
""");

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("@concepts/some-file.md").
        contains("More content here.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  // -------------------------------------------------------------------------
  // Fix 4: Success path for multiple directives on first load
  // -------------------------------------------------------------------------

  /**
   * Verifies that a skill with multiple {@code !} preprocessor directives succeeds on the first load,
   * expanding all directives inline.
   * <p>
   * Only subsequent loads (not first loads) are constrained to a single directive.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadSucceedsOnFirstLoadWithMultipleDirectives() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("dir-a"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), """
        Skill with two directives.

        !`"${CLAUDE_PLUGIN_ROOT}/client/bin/dir-a"`
        !`"${CLAUDE_PLUGIN_ROOT}/client/bin/dir-a"`
        """);

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));

      // First load must succeed and expand both directives
      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").
        contains("Skill with two directives.").
        contains("NO_ARGS_OUTPUT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  // -------------------------------------------------------------------------
  // resolveAndValidateContainment() security tests
  // -------------------------------------------------------------------------

  /**
   * Verifies that resolveAndValidateContainment allows a valid path within the base directory.
   */
  @Test
  public void resolveAndValidateContainmentAllowsValidPath() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      Path result = GetSkill.resolveAndValidateContainment(baseDir, "subdir/file", "test");
      requireThat(result.startsWith(baseDir), "result.startsWith(baseDir)").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }

  /**
   * Verifies that resolveAndValidateContainment throws for a path traversal attempt.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*path traversal.*")
  public void resolveAndValidateContainmentRejectsPathTraversal() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      GetSkill.resolveAndValidateContainment(baseDir, "../../etc/passwd", "testParam");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }

  /**
   * Verifies that resolveAndValidateContainment throws NullPointerException for a null baseDir.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*baseDir.*")
  public void resolveAndValidateContainmentRejectsNullBaseDir()
  {
    GetSkill.resolveAndValidateContainment(null, "subdir", "param");
  }

  /**
   * Verifies that resolveAndValidateContainment throws NullPointerException for a null relativePath.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*relativePath.*")
  public void resolveAndValidateContainmentRejectsNullRelativePath() throws IOException
  {
    Path baseDir = Files.createTempDirectory("test-base").toAbsolutePath().normalize();
    try
    {
      GetSkill.resolveAndValidateContainment(baseDir, null, "param");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(baseDir);
    }
  }

  // -------------------------------------------------------------------------
  // Fix 5c: catAgentId negative validation tests
  // -------------------------------------------------------------------------

  /**
   * Verifies that constructor rejects an agent ID that is neither a UUID nor a subagent ID format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*does not match a valid format.*")
  public void constructorRejectsInvalidAgentIdFormat() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new GetSkill(scope, List.of("not-a-uuid-or-subagent-id"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects a malformed subagent ID missing the {@code /subagents/} segment.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*does not match a valid format.*")
  public void constructorRejectsMalformedSubagentId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Valid UUID prefix but missing /subagents/ segment
      String malformedId = "12345678-1234-1234-1234-123456789abc/wrongsegment/agent123";
      new GetSkill(scope, List.of(malformedId));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // -------------------------------------------------------------------------
  // Constructor space-splitting tests
  // -------------------------------------------------------------------------

  /**
   * Verifies that when the first argument contains a space, the constructor splits on it:
   * the prefix becomes the agent ID ($0) and the remainder is inserted as $1, shifting
   * any existing $1..$N arguments to $2..$N+1.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void constructorSplitsAgentIdOnSpace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String sessionId = UUID.randomUUID().toString();
      // Pass "sessionId description text" as a single quoted argument
      String combinedFirst = sessionId + " description text";
      GetSkill loader = new GetSkill(scope, List.of(combinedFirst, "existing-arg"));
      requireThat(loader, "loader").isNotNull();
      // The marker directory should be under the session ID (not the combined string)
      Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
      Path expectedAgentDir = baseDir.resolve(sessionId);
      // Directory should have been created
      requireThat(Files.isDirectory(expectedAgentDir), "agentDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the first argument contains multiple spaces, only the first space is used
   * for splitting — the entire remainder after the first space becomes $1.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void constructorSplitsOnFirstSpaceOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      String sessionId = UUID.randomUUID().toString();
      // Multiple spaces: only split on the FIRST space; remainder contains the rest
      String combinedFirst = sessionId + " word1 word2 word3";
      GetSkill loader = new GetSkill(scope, List.of(combinedFirst));
      requireThat(loader, "loader").isNotNull();
      // Marker directory should be under sessionId (the part before the first space)
      Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
      Path expectedAgentDir = baseDir.resolve(sessionId);
      requireThat(Files.isDirectory(expectedAgentDir), "agentDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the first argument starts with a space, the constructor does not split on it
   * (spaceIndex == 0 is not > 0), so the whole string is used as-is. Because the non-blank value
   * does not match UUID or subagent format, the constructor fails with a validation error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*does not match a valid format.*")
  public void constructorFallsBackToSessionIdWhenAgentIdStartsWithSpace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // First arg starts with a space: spaceIndex == 0, not > 0, so no split occurs.
      // The full string " description text" is used as catAgentId, which is not blank (no fallback)
      // and does not match UUID or subagent format, so validation fails.
      new GetSkill(scope, List.of(" description text"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // -------------------------------------------------------------------------
  // Qualified name marker file tests
  // -------------------------------------------------------------------------

  /**
   * Verifies that loading a skill by bare name stores the marker file with the qualified name
   * (URL-encoded prefix:skill-name format).
   */
  @Test
  public void loadStoresQualifiedNameInMarkerFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      String agentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope, List.of(agentId));
      loader.load("test-skill");

      Path agentDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize().resolve(agentId);
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      // Qualified name: "cat:test-skill" → URL-encoded: "cat%3Atest-skill"
      Path expectedMarker = loadedDir.resolve(URLEncoder.encode("cat:test-skill", StandardCharsets.UTF_8));
      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that a second load call for the same bare-name skill returns the "already loaded"
   * reference message, confirming that the qualified-name marker is recognized on the second call.
   */
  @Test
  public void loadRecognizesAlreadyLoadedByQualifiedName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      GetSkill loader = new GetSkill(scope, List.of(UUID.randomUUID().toString()));
      loader.load("test-skill");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").contains("skill instructions were already loaded");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  // -------------------------------------------------------------------------
  // End-to-end: subagent catAgentId format accepted by GetSkill constructor
  // -------------------------------------------------------------------------

  /**
   * Verifies that GetSkill construction succeeds when the catAgentId uses the subagent format
   * ({sessionId}/subagents/{agentId}) injected by SubagentStartHook — confirming that the
   * args-guidance fix in {@code SkillDiscovery.getSubagentSkillListing()} produces an invocation
   * path that does not throw IllegalArgumentException when the agent follows instructions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void constructorAcceptsSubagentFormatCatAgentIdForStakeholderReviewAgent() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      // Use the format injected by SubagentStartHook: {sessionId}/subagents/{agentId}
      String sessionId = UUID.randomUUID().toString();
      String agentId = "a20546c3cd3fdecbb";
      String catAgentId = sessionId + "/subagents/" + agentId;

      // Constructor must succeed without throwing IllegalArgumentException
      GetSkill loader = new GetSkill(scope, List.of(catAgentId));
      requireThat(loader, "loader").isNotNull();

      // Verify the marker directory was created under the subagent path
      Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
      Path expectedAgentDir = baseDir.resolve(catAgentId);
      requireThat(Files.isDirectory(expectedAgentDir), "agentDirExists").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that loading a skill by its qualified name (e.g., "cat:test-skill") stores
   * the marker without double-qualifying it.
   */
  @Test
  public void loadAcceptsAlreadyQualifiedName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("first-use.md"), "Skill content\n");

      String agentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope, List.of(agentId));
      loader.load("cat:test-skill");

      Path agentDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize().resolve(agentId);
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      Path expectedMarker = loadedDir.resolve(URLEncoder.encode("cat:test-skill", StandardCharsets.UTF_8));
      requireThat(Files.exists(expectedMarker), "markerExists").isTrue();
      // Verify no double-qualified file exists
      Path doubleQualifiedMarker = loadedDir.resolve(
        URLEncoder.encode("cat:cat:test-skill", StandardCharsets.UTF_8));
      requireThat(Files.exists(doubleQualifiedMarker), "doubleQualifiedExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that variable expansion inside {@code !} directive strings uses {@code System.getenv()}
   * directly, not the injectable {@code ClaudeEnv}.
   * <p>
   * When {@code GetSkill} is constructed with a {@code ClaudeEnv} that has no variables (empty map),
   * a real environment variable that exists in the process (like {@code PATH}) should still be
   * expanded in directive strings because expansion reads from {@code System.getenv()}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsVariableViaSystemGetenv() throws IOException
  {
    // PATH is always set in the process environment
    String pathValue = System.getenv("PATH");
    requireThat(pathValue, "pathValue").isNotNull();

    Path tempPluginRoot = Files.createTempDirectory("get-skill-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      // Set up a launcher so the directive is executed and args are visible in output
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(companionDir);
      // ${PATH} inside the directive args — must be expanded via System.getenv()
      Files.writeString(companionDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output" "${PATH}"`
        """);

      // GetSkill uses System.getenv() directly for variable expansion
      String agentId = UUID.randomUUID().toString();
      GetSkill loader = new GetSkill(scope, List.of(agentId));
      String result = loader.load("test-skill");

      // ${PATH} must have been expanded to the real PATH value via System.getenv()
      requireThat(result, "result").contains("ARGS:" + pathValue);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
}

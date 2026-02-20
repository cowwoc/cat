/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.SkillLoader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SkillLoader functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class SkillLoaderTest
{
  /**
   * Verifies that constructor rejects null plugin root.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullPluginRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, null, "session123", "/project");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects empty plugin root.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void constructorRejectsEmptyPluginRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, "", "session123", "/project");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null session ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, "/plugin", null, "/project");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects empty session ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void constructorRejectsEmptySessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, "/plugin", "", "/project");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects null project directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullProjectDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, "/plugin", "session123", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor rejects empty project directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void constructorRejectsEmptyProjectDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      new SkillLoader(scope, "/plugin", "session123", "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that load substitutes CLAUDE_PLUGIN_ROOT variable.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadSubstitutesPluginRootVariable() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Path: ${CLAUDE_PLUGIN_ROOT}/file.txt
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Path: " + tempPluginRoot + "/file.txt").
        doesNotContain("${CLAUDE_PLUGIN_ROOT}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load substitutes CLAUDE_SESSION_ID variable.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadSubstitutesSessionIdVariable() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Session: ${CLAUDE_SESSION_ID}
""");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSession, "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Session: " + uniqueSession).
        doesNotContain("${CLAUDE_SESSION_ID}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load substitutes CLAUDE_PROJECT_DIR variable when provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadSubstitutesProjectDirVariable() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Project: ${CLAUDE_PROJECT_DIR}/data
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/workspace");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Project: /workspace/data").
        doesNotContain("${CLAUDE_PROJECT_DIR}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Full skill content here
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Full skill content here");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load returns dynamic reference on second invocation for non-tagged skills.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsReferenceOnSecondInvocation() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Full skill content
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

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
   * Verifies that load handles skills without a companion SKILL.md file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesMissingContentFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/empty-skill");
      Files.createDirectories(skillDir);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("empty-skill");

      requireThat(result, "result").isNotNull();
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
  @Test(expectedExceptions = NullPointerException.class)
  public void loadRejectsNullSkillName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void loadRejectsEmptySkillName() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      loader.load("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load handles nested variable references in content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesNestedVariableReferences() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Path: ${CLAUDE_PLUGIN_ROOT}/contains_${CLAUDE_SESSION_ID}
Project: ${CLAUDE_PROJECT_DIR}/session_${CLAUDE_SESSION_ID}
""");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSession, "/workspace");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Path: " + tempPluginRoot + "/contains_" + uniqueSession).
        contains("Project: /workspace/session_" + uniqueSession).
        doesNotContain("${CLAUDE_PLUGIN_ROOT}").
        doesNotContain("${CLAUDE_SESSION_ID}").
        doesNotContain("${CLAUDE_PROJECT_DIR}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Value: ${UNDEFINED_VAR}
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Value: ${UNDEFINED_VAR}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load works with no bindings.json file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadWorksWithoutBindingsJson() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
Session: ${CLAUDE_SESSION_ID}
""");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSession, "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Root: " + tempPluginRoot).
        contains("Session: " + uniqueSession);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }


  /**
   * Verifies that load expands @path references in skill content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsPathReferences() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("context.md"), """
Context file content
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/context.md
# Main Content
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Context file content").
        contains("# Main Content").
        doesNotContain("@concepts/context.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load substitutes variables in @path-expanded content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadSubstitutesVariablesInExpandedPaths() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("context.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/context.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Root: " + tempPluginRoot).
        doesNotContain("${CLAUDE_PLUGIN_ROOT}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load throws IOException for missing @path file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void loadRejectsMissingPathFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/missing.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      loader.load("test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load does not expand @ symbols in non-path contexts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadIgnoresAtSymbolInNonPathContexts() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Email: user@example.com
@Override annotation
@author tag
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Email: user@example.com").
        contains("@Override annotation").
        contains("@author tag");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }


  /**
   * Verifies that circular @path references are detected and rejected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadRejectsCircularPathReferences() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("file-a.md"), """
Content A
@concepts/file-b.md
""");
      Files.writeString(conceptsDir.resolve("file-b.md"), """
Content B
@concepts/file-a.md
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/file-a.md
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

      try
      {
        loader.load("test-skill");
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Circular @path reference");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that multiple @path references in a single file are expanded correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsMultiplePathReferences() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("intro.md"), """
Introduction section
""");
      Files.writeString(conceptsDir.resolve("details.md"), """
Details section
""");
      Files.writeString(conceptsDir.resolve("conclusion.md"), """
Conclusion section
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
# Header
@concepts/intro.md
@concepts/details.md
@concepts/conclusion.md
# Footer
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("# Header").
        contains("Introduction section").
        contains("Details section").
        contains("Conclusion section").
        contains("# Footer");
      int introIndex = result.indexOf("Introduction section");
      int detailsIndex = result.indexOf("Details section");
      int conclusionIndex = result.indexOf("Conclusion section");
      requireThat(introIndex, "introIndex").isLessThan(detailsIndex);
      requireThat(detailsIndex, "detailsIndex").isLessThan(conclusionIndex);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load expands @path references for files with non-.md/.json extensions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsPathReferencesWithAnyExtension() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path configDir = tempPluginRoot.resolve("config");
      Files.createDirectories(configDir);
      Files.writeString(configDir.resolve("settings.yaml"), """
option: value
enabled: true
""");
      Files.writeString(configDir.resolve("notes.txt"), """
Plain text content
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
# Configuration
@config/settings.yaml
# Notes
@config/notes.txt
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("option: value").
        contains("enabled: true").
        contains("Plain text content").
        doesNotContain("@config/settings.yaml").
        doesNotContain("@config/notes.txt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }


  /**
   * Verifies that load expands @path references with special characters in filenames.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsPathWithSpecialCharacters() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("my notes.md"), """
Content with spaces in filename
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/my notes.md
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Content with spaces in filename").
        doesNotContain("@concepts/my notes.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }


  /**
   * Verifies that load ignores @path in the middle of a line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadIgnoresPathInMiddleOfLine() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("note.md"), """
This should not be included
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
See @concepts/note.md for details
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("See @concepts/note.md for details").
        doesNotContain("This should not be included");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load adds newline if @path target file lacks trailing newline.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadExpandsPathFileWithoutTrailingNewline() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("no-newline.md"), "Content without newline");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/no-newline.md
Next line
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Content without newline").
        contains("Next line");
      int contentIndex = result.indexOf("Content without newline");
      int nextIndex = result.indexOf("Next line");
      requireThat(contentIndex, "contentIndex").isLessThan(nextIndex);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that multiple unknown variables are all passed through as literals.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughMultipleUnknownVariables() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Branch: ${BASE}
Other: ${SOME_UNKNOWN}
Root: ${CLAUDE_PLUGIN_ROOT}
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Branch: ${BASE}").
        contains("Other: ${SOME_UNKNOWN}").
        contains("Root: " + tempPluginRoot).
        doesNotContain("${CLAUDE_PLUGIN_ROOT}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that unknown variables in @path-expanded content are passed through as literals.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughUnknownVarsInExpandedPaths() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("version-paths.md"), """
```bash
git checkout ${BASE}
```
""");

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
@concepts/version-paths.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("git checkout ${BASE}").
        contains("# Main");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "---\n" +
        "name: test-skill\n" +
        "---\n" +
        "# Skill Content\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
# No License Header
Regular content here
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
   * Verifies that preprocessor directive passes through when launcher not found.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadPassesThroughUnknownLauncher() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/nonexistent-launcher"`
Done
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("!`\"" + tempPluginRoot + "/client/bin/nonexistent-launcher\"`").
        contains("Done");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that CLAUDE_PLUGIN_ROOT is resolved before preprocessor directive processing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadResolvesVariablesBeforePreprocessorDirectives() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
Directive: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-launcher"`
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Root: " + tempPluginRoot).
        doesNotContain("${CLAUDE_PLUGIN_ROOT}");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsIo "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsRuntime "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsFromConstructor "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsNullMessage "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
   * Verifies that {@code @path} references inside markdown code blocks are not expanded.
   * <p>
   * When a code block contains an {@code @path}-style reference as an example, SkillLoader must
   * preserve it as-is rather than attempting file expansion (which would throw IOException for
   * non-existent files).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void atPathInsideCodeBlockIsNotExpanded() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(skillDir);

      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: test
        ---
        # Test

        Example:
        ```xml
        @concepts/some-file.md
        ```

        <output skill="test">
        test output
        </output>
        """);

      // Note: concepts/some-file.md does NOT exist.
      // If @path inside code block is expanded, this would throw IOException.
      String uniqueSessionId = "code-block-test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSessionId, "/workspace");
      String result = loader.load("test-skill");

      // The @path inside the code block should be preserved as-is
      requireThat(result, "result").
        contains("@concepts/some-file.md").
        contains("<instructions skill=\"test-skill\">");
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

    String className = SkillLoader.extractClassName(launcherContent);
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

    String className = SkillLoader.extractClassName(launcherContent);
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

    String className = SkillLoader.extractClassName(launcherContent);
    requireThat(className, "className").isEmpty();
  }

  /**
   * Verifies that executeDirective throws IOException when launcher exists but class name
   * cannot be extracted (fail-fast instead of silent fallback).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadThrowsWhenClassExtractionFails() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      // Launcher without -m pattern, so extractClassName always returns empty
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/sh
        exec java -jar app.jar "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output" arg1 arg2`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      try
      {
        loader.load("test-skill");
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Failed to extract class name from launcher");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load uses the {@code -first-use} companion SKILL.md when present,
   * returning the instructions combined with the output body.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadUsesFirstUseCompanionSkillMd() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Skill instructions here.

<output>
Output content here.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("<instructions skill=\"test-skill\">").
        contains("Skill instructions here.").
        contains("</instructions>").
        contains("Execute the <instructions skill=\"test-skill\"> block from earlier in this conversation,").
        contains("<output skill=\"test-skill\">").
        contains("Output content here.").
        contains("</output>").
        doesNotContain("description:").
        doesNotContain("user-invocable:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that on the second invocation of a skill with a {@code -first-use} companion,
   * the execution trigger is returned while the output body is still appended.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsReferenceWithOutputOnSecondInvocation() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Full skill instructions.

<output>
Dynamic output.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").
        contains("<instructions skill=\"test-skill\">").
        contains("Full skill instructions.").
        contains("</instructions>").
        contains("Execute the <instructions skill=\"test-skill\"> block from earlier in this conversation,").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output.").
        contains("</output>");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").
        contains("Execute the <instructions skill=\"test-skill\"> block from earlier in this conversation," +
          " using the updated <output skill=\"test-skill\"> tag below.").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output.").
        contains("</output>").
        doesNotContain("Full skill instructions.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that a {@code -first-use} SKILL.md without an {@code <output>} tag is treated
   * as non-tagged content and returned directly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesContentWithoutOutputTag() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Skill body without output.
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Skill body without output.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load strips YAML frontmatter from a {@code -first-use} SKILL.md file
   * that uses the {@code <output>} tag.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsYamlFrontmatterFromFirstUseSkillMd() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Internal skill. Do not invoke directly."
user-invocable: false
---

Content after frontmatter.

<output>
Output content.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("<instructions skill=\"test-skill\">").
        contains("Content after frontmatter.").
        contains("</instructions>").
        contains("<output skill=\"test-skill\">").
        contains("Output content.").
        doesNotContain("description:").
        doesNotContain("user-invocable:");
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
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("client/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/client/bin/test-output" arg1 arg2`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
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
   * Verifies that {@code OUTPUT_TAG_PATTERN} matches an {@code <output>} tag that has a single attribute,
   * capturing the body content on the first invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesOutputTagWithSingleAttribute() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Full skill instructions.

<output skill="test-skill">
Dynamic output with attribute.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

      String result = loader.load("test-skill");
      requireThat(result, "result").
        contains("<instructions skill=\"test-skill\">").
        contains("Full skill instructions.").
        contains("</instructions>").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output with attribute.").
        contains("</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that {@code OUTPUT_TAG_PATTERN} matches an {@code <output>} tag with multiple attributes,
   * capturing the body content on the first invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesOutputTagWithMultipleAttributes() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Full skill instructions.

<output data-id="123" skill="test">
Dynamic output with multiple attributes.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

      String result = loader.load("test-skill");
      requireThat(result, "result").
        contains("<instructions skill=\"test-skill\">").
        contains("Full skill instructions.").
        contains("</instructions>").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output with multiple attributes.").
        contains("</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that {@code OUTPUT_TAG_PATTERN} correctly handles an {@code <output>} tag with a single attribute
   * on both the first and second invocations.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesOutputTagWithAttributeOnSubsequentInvocations() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path firstUseDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(firstUseDir);
      Files.writeString(firstUseDir.resolve("SKILL.md"), """
---
description: "Test skill"
user-invocable: false
---

Full skill instructions.

<output skill="test-skill">
Dynamic output with attribute.
</output>
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").
        contains("<instructions skill=\"test-skill\">").
        contains("Full skill instructions.").
        contains("</instructions>").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output with attribute.").
        contains("</output>");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").
        contains("Execute the <instructions skill=\"test-skill\"> block from earlier in this conversation," +
          " using the updated <output skill=\"test-skill\"> tag below.").
        contains("<output skill=\"test-skill\">").
        contains("Dynamic output with attribute.").
        contains("</output>").
        doesNotContain("Full skill instructions.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that positional arguments are resolved from the args string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void positionalArgsResolved() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "Count: $0, Label: $1\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project", "42 hello");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Count: 42, Label: hello").
        doesNotContain("$0").
        doesNotContain("$1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that missing positional args are passed through as literals.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingPositionalArgsPassedThrough() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "First: $0, Second: $1\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project", "value1");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("First: value1").
        contains("Second: $1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that extra positional args beyond referenced indices are ignored.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void extraPositionalArgsIgnored() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "Name: $0\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project", "Alice Bob Charlie");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Name: Alice").
        doesNotContain("Bob").
        doesNotContain("Charlie");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that empty args leave positional references unresolved (passed through as literals).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyArgsLeavePositionalRefsUnresolved() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "Count: $0\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Count: $0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that whitespace-only args leave positional references unresolved (passed through as
   * literals).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void whitespaceOnlyArgsLeavePositionalRefsUnresolved() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path companionDir = tempPluginRoot.resolve("skills/test-skill-first-use");
      Files.createDirectories(companionDir);
      Files.writeString(companionDir.resolve("SKILL.md"),
        "Count: $0\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/project", "   ");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Count: $0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
}

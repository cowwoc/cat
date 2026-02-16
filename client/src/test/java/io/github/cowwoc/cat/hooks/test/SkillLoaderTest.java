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
   * Verifies that constructor accepts empty project directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void constructorAcceptsEmptyProjectDir() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      requireThat(loader, "loader").isNotNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Path: ${CLAUDE_PLUGIN_ROOT}/file.txt
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Session: ${CLAUDE_SESSION_ID}
""");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSession, "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Full skill content here
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Full skill content here");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load loads reference on second invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsReferenceOnSecondInvocation() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Full skill content
""");

      Path skillsDir = tempPluginRoot.resolve("skills");
      Files.writeString(skillsDir.resolve("reference.md"), """
Reference text
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").contains("Full skill content");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").
        contains("Reference text").
        doesNotContain("Full skill content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load handles skills without first-use.md file.
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
        System.nanoTime(), "");
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
        System.nanoTime(), "");
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
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Value: ${UNDEFINED_VAR}
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
Session: ${CLAUDE_SESSION_ID}
""");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), uniqueSession, "");
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
   * Verifies that load expands @path references in first-use.md.
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/context.md
# Main Content
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/context.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/missing.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Email: user@example.com
@Override annotation
@author tag
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/file-a.md
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");

      try
      {
        loader.load("test-skill");
        requireThat(false, "load").isEqualTo(true);
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
# Header
@concepts/intro.md
@concepts/details.md
@concepts/conclusion.md
# Footer
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
# Configuration
@config/settings.yaml
# Notes
@config/notes.txt
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/my notes.md
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
See @concepts/note.md for details
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/no-newline.md
Next line
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Branch: ${BASE}
Other: ${SOME_UNKNOWN}
Root: ${CLAUDE_PLUGIN_ROOT}
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
@concepts/version-paths.md
# Main
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
   * Verifies that load strips license header from first-use.md content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsLicenseHeaderFromContent() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"),
        "<!--\n" +
        "Copyright (c) 2026 Gili Tzabari. All rights reserved.\n" +
        "Licensed under the CAT Commercial License.\n" +
        "See LICENSE.md in the project root for license terms.\n" +
        "-->\n" +
        "# Skill Content\n" +
        "Body text here\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("# Skill Content").
        contains("Body text here").
        doesNotContain("Copyright").
        doesNotContain("<!--");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load strips license header from reference.md content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsLicenseHeaderFromReference() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Full skill content
""");

      Path skillsDir = tempPluginRoot.resolve("skills");
      Files.writeString(skillsDir.resolve("reference.md"),
        "<!--\n" +
        "Copyright (c) 2026 Gili Tzabari. All rights reserved.\n" +
        "Licensed under the CAT Commercial License.\n" +
        "See LICENSE.md in the project root for license terms.\n" +
        "-->\n" +
        "Reference text\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");

      // First load to mark skill as loaded
      loader.load("test-skill");

      // Second load returns reference
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Reference text").
        doesNotContain("Copyright").
        doesNotContain("<!--");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load strips license header but preserves YAML frontmatter.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadStripsLicenseHeaderPreservesFrontmatter() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"),
        "---\n" +
        "name: test-skill\n" +
        "---\n" +
        "<!--\n" +
        "Copyright (c) 2026 Gili Tzabari. All rights reserved.\n" +
        "Licensed under the CAT Commercial License.\n" +
        "See LICENSE.md in the project root for license terms.\n" +
        "-->\n" +
        "# Skill Content\n");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("---").
        contains("name: test-skill").
        contains("# Skill Content").
        doesNotContain("Copyright").
        doesNotContain("<!--");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
# No License Header
Regular content here
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/nonexistent-launcher"`
Done
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("!`\"" + tempPluginRoot + "/hooks/bin/nonexistent-launcher\"`").
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
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
Root: ${CLAUDE_PLUGIN_ROOT}
Directive: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-launcher"`
""");

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
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
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Output: NO_ARGS_OUTPUT").
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
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsIo "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      loader.load("test-skill");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that RuntimeException from SkillOutput.getOutput() returns an error string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsErrorStringForRuntimeException() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsRuntime "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("<error>Preprocessor directive failed for").
        contains("simulated runtime failure").
        contains("</error>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that InvocationTargetException from constructor returns error string with cause message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsErrorStringForConstructorException() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsFromConstructor "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("<error>Preprocessor directive failed for").
        contains("constructor failure").
        contains("</error>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that exception with null message uses class name in error string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadReturnsClassNameForNullExceptionMessage() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try (JvmScope scope = new TestJvmScope(tempPluginRoot, tempPluginRoot))
    {
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutputThrowsNullMessage "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output"`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("<error>Preprocessor directive failed for").
        contains("java.lang.IllegalStateException").
        contains("</error>");
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
      Path hooksDir = tempPluginRoot.resolve("hooks/bin");
      Files.createDirectories(hooksDir);
      Files.writeString(hooksDir.resolve("test-output"), """
        #!/bin/bash
        java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.test.TestSkillOutput "$@"
        """);

      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("first-use.md"), """
        Output: !`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/test-output" arg1 arg2`
        Done
        """);

      SkillLoader loader = new SkillLoader(scope, tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").
        contains("Output: ARGS:arg1,arg2").
        contains("Done").
        doesNotContain("!`");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
    }
  }
}

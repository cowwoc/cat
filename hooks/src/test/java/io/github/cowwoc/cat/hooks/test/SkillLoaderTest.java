package io.github.cowwoc.cat.hooks.test;

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
    new SkillLoader(null, "session123", "/project");
  }

  /**
   * Verifies that constructor rejects empty plugin root.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void constructorRejectsEmptyPluginRoot() throws IOException
  {
    new SkillLoader("", "session123", "/project");
  }

  /**
   * Verifies that constructor rejects null session ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorRejectsNullSessionId() throws IOException
  {
    new SkillLoader("/plugin", null, "/project");
  }

  /**
   * Verifies that constructor rejects empty session ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void constructorRejectsEmptySessionId() throws IOException
  {
    new SkillLoader("/plugin", "", "/project");
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
    try
    {
      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      requireThat(loader, "loader").isNotNull();
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Path: ${CLAUDE_PLUGIN_ROOT}/file.txt\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Path: " + tempPluginRoot + "/file.txt");
      requireThat(result, "result").doesNotContain("${CLAUDE_PLUGIN_ROOT}");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Session: ${CLAUDE_SESSION_ID}\n");

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), uniqueSession, "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Session: " + uniqueSession);
      requireThat(result, "result").doesNotContain("${CLAUDE_SESSION_ID}");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Project: ${CLAUDE_PROJECT_DIR}/data\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "/workspace");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Project: /workspace/data");
      requireThat(result, "result").doesNotContain("${CLAUDE_PROJECT_DIR}");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Full skill content here\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Full skill content here");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Full skill content\n");

      Path skillsDir = tempPluginRoot.resolve("skills");
      Files.writeString(skillsDir.resolve("reference.md"), "Reference text\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");

      String firstResult = loader.load("test-skill");
      requireThat(firstResult, "firstResult").contains("Full skill content");

      String secondResult = loader.load("test-skill");
      requireThat(secondResult, "secondResult").contains("Reference text");
      requireThat(secondResult, "secondResult").doesNotContain("Full skill content");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load handles skills without content.md file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesMissingContentFile() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/empty-skill");
      Files.createDirectories(skillDir);

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("empty-skill");

      requireThat(result, "result").isNotNull();
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load handles includes.txt file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadProcessesIncludes() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Main content\n");
      Files.writeString(skillDir.resolve("includes.txt"), "concepts/context1.md\n");

      Path conceptsDir = tempPluginRoot.resolve("concepts");
      Files.createDirectories(conceptsDir);
      Files.writeString(conceptsDir.resolve("context1.md"), "Context file content\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("<include path=\"concepts/context1.md\">");
      requireThat(result, "result").contains("</include>");
      requireThat(result, "result").contains("Context file content");
      requireThat(result, "result").contains("Main content");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      loader.load(null);
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      loader.load("");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
    }
  }

  /**
   * Verifies that load handles missing include files gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void loadHandlesMissingIncludeFileGracefully() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("skill-loader-test");
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("content.md"), "Main content\n");
      Files.writeString(skillDir.resolve("includes.txt"), "concepts/missing.md\n");

      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), "session-" +
        System.nanoTime(), "");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Main content");
      requireThat(result, "result").doesNotContain("missing.md");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
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
    try
    {
      Path skillDir = tempPluginRoot.resolve("skills/test-skill");
      Files.createDirectories(skillDir);
      String content = "Path: ${CLAUDE_PLUGIN_ROOT}/contains_${CLAUDE_SESSION_ID}\n" +
        "Project: ${CLAUDE_PROJECT_DIR}/session_${CLAUDE_SESSION_ID}\n";
      Files.writeString(skillDir.resolve("content.md"), content);

      String uniqueSession = "test-" + System.nanoTime();
      SkillLoader loader = new SkillLoader(tempPluginRoot.toString(), uniqueSession, "/workspace");
      String result = loader.load("test-skill");

      requireThat(result, "result").contains("Path: " + tempPluginRoot + "/contains_" + uniqueSession);
      requireThat(result, "result").contains("Project: /workspace/session_" + uniqueSession);
      requireThat(result, "result").doesNotContain("${CLAUDE_PLUGIN_ROOT}");
      requireThat(result, "result").doesNotContain("${CLAUDE_SESSION_ID}");
      requireThat(result, "result").doesNotContain("${CLAUDE_PROJECT_DIR}");
    }
    finally
    {
      deleteRecursively(tempPluginRoot);
    }
  }

  /**
   * Recursively deletes a directory.
   *
   * @param path the directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteRecursively(Path path) throws IOException
  {
    if (Files.isDirectory(path))
    {
      try (java.util.stream.Stream<Path> stream = Files.list(path))
      {
        for (Path child : stream.toList())
          deleteRecursively(child);
      }
    }
    Files.deleteIfExists(path);
  }
}

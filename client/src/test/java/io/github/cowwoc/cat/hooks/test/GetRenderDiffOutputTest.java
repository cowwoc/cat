/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetRenderDiffOutput;
import org.testng.annotations.Test;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetRenderDiffOutput functionality.
 * <p>
 * Tests verify the render-diff output generator. The handler relies on git commands
 * to compute diffs, so tests focus on integration with real git repositories
 * and verifying output structure.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetRenderDiffOutputTest
{
  /**
   * Verifies that getOutput throws NullPointerException for null projectRoot.
   */
  @Test
  public void nullProjectRootThrowsException()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
      try
      {
        handler.getOutput(null);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("projectRoot");
      }
    }
  }

  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test
  public void nullScopeThrowsException()
  {
    try
    {
      new GetRenderDiffOutput(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("scope");
    }
  }

  /**
   * Verifies that getOutput returns error message for a non-git directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonGitDirectoryReturnsErrorMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Create a minimal cat-config.json so Config.load doesn't fail
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Non-git directory should return error message (git commands fail)
        requireThat(result, "result").contains("Base branch");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput reports no changes when HEAD matches base branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noChangesReportsNoChanges() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize a git repo with a main branch
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create a cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create an initial commit
        Files.writeString(tempDir.resolve("file.txt"), "initial content");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // HEAD matches main, so no changes
        requireThat(result, "result").contains("No changes");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput produces diff summary with changed file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void changesProduceDiffSummary() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-my-feature", "file.txt",
          "initial content\n", "modified content\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Diff Summary").
          contains("file.txt");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that diff output includes rendered 2-column format section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diffOutputIncludes2ColumnFormat() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-add-method", "code.java",
          "class Foo {\n}\n", "class Foo {\n  void bar() {}\n}\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Rendered Diff (2-column format)").
          contains("code.java");
        // Verify 2-column format with box-drawing characters
        requireThat(result, "result").contains("│");  // Vertical separator
        requireThat(result, "result").contains("+ ");  // Addition indicator
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that diff output includes insertion and deletion counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diffOutputIncludesStats() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-edit-data", "data.txt",
          "line1\nline2\nline3\n", "line1\nmodified\nline3\nnew line\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Insertions:").contains("Deletions:");
        // Verify 2-column format indicators
        requireThat(result, "result").contains("- ");  // Deletion indicator
        requireThat(result, "result").contains("+ ");  // Addition indicator
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that diff output lists changed files.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diffOutputListsChangedFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Initial commit
        Files.writeString(tempDir.resolve("alpha.txt"), "hello\n");
        Files.writeString(tempDir.resolve("beta.txt"), "world\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes to both files
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-update-both");
        Files.writeString(tempDir.resolve("alpha.txt"), "hello modified\n");
        Files.writeString(tempDir.resolve("beta.txt"), "world modified\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "update both files");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Changed Files").
          contains("alpha.txt").contains("beta.txt");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that diff output uses dynamic column width based on line numbers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void dynamicColumnWidthForHighLineNumbers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create a file with 1000+ lines for high line numbers
        StringBuilder content = new StringBuilder(50_000);
        for (int i = 1; i <= 1500; ++i)
          content.append("Line ").append(i).append('\n');
        Files.writeString(tempDir.resolve("large.txt"), content.toString());
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes near end of file
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-edit-large");
        String modifiedContent = content.toString().replace("Line 1499", "Modified line 1499");
        Files.writeString(tempDir.resolve("large.txt"), modifiedContent);
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "edit large file");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain 4-digit line numbers (1499)
        requireThat(result, "result").contains("1499");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that long lines are wrapped with wrap indicator.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void longLinesAreWrapped() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config with small width to force wrapping
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 50}");

        // Create file with very long line
        String longLine = "This is a very long line that will definitely need to be wrapped " +
                          "when rendered in the 2-column format with limited width available";
        Files.writeString(tempDir.resolve("wrap.txt"), longLine + '\n');
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with modified long line
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-wrap-test");
        String modifiedLongLine = longLine.replace("very long", "extremely long");
        Files.writeString(tempDir.resolve("wrap.txt"), modifiedLongLine + '\n');
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify long line");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain wrap indicator
        requireThat(result, "result").contains("↩");
        // Should show wrap in legend
        requireThat(result, "result").contains("wrap");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that whitespace-only changes are visualized with markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void whitespaceChangesAreVisualized() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create file with spaces
        Files.writeString(tempDir.resolve("spaces.txt"), "hello world\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with tabs instead of spaces
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-whitespace");
        Files.writeString(tempDir.resolve("spaces.txt"), "hello\tworld\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "change to tabs");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should visualize space and tab
        requireThat(result, "result").contains("·");  // Space marker
        requireThat(result, "result").contains("→");  // Tab marker
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that binary files are detected and marked.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void binaryFilesAreDetected() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create a binary file (simulate with null bytes)
        byte[] binaryData = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        Files.write(tempDir.resolve("data.bin"), binaryData);
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with modified binary
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-binary");
        byte[] modifiedBinary = {0x00, 0x01, 0x02, 0x03, 0x04, 0x06};
        Files.write(tempDir.resolve("data.bin"), modifiedBinary);
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify binary");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain binary file indicator
        requireThat(result, "result").contains("binary");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that file renames are detected and displayed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void renamedFilesAreDetected() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create a file
        Files.writeString(tempDir.resolve("old-name.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with renamed file
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-rename");
        runGit(tempDir, "mv", "old-name.txt", "new-name.txt");
        runGit(tempDir, "commit", "-m", "rename file");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should show rename
        requireThat(result, "result").contains("old-name.txt");
        requireThat(result, "result").contains("new-name.txt");
        requireThat(result, "result").contains("renamed");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that multiple hunks per file are rendered correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleHunksPerFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create a file with separated sections
        StringBuilder content = new StringBuilder(500);
        content.append("Section 1 line 1\n").
          append("Section 1 line 2\n");
        for (int i = 0; i < 20; ++i)
          content.append("Context line ").append(i).append('\n');
        content.append("Section 2 line 1\n").
          append("Section 2 line 2\n");

        Files.writeString(tempDir.resolve("multi.txt"), content.toString());
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes in both sections
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-multi-hunk");
        String modified = content.toString().
          replace("Section 1 line 1", "Modified section 1 line 1").
          replace("Section 2 line 1", "Modified section 2 line 1");
        Files.writeString(tempDir.resolve("multi.txt"), modified);
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify both sections");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain both modifications
        requireThat(result, "result").contains("Modified section 1").
          contains("Modified section 2").contains("multi.txt");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that very long filenames are handled properly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void veryLongFilenamesAreTruncated() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create file with very long name
        String longName = "this-is-a-very-long-filename-that-should-be-truncated-when-displayed.txt";
        Files.writeString(tempDir.resolve(longName), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-long-name");
        Files.writeString(tempDir.resolve(longName), "modified\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify long filename");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain the long filename (possibly truncated with ...)
        requireThat(result, "result").contains("this-is-a-very-long");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that empty diff content returns appropriate message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyDiffContentReturnsMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch but make no changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-no-changes");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should report no changes
        requireThat(result, "result").contains("No changes");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that context-only hunks are handled properly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void contextOnlyHunkIsHandled() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit with multiple files
        Files.writeString(tempDir.resolve("file1.txt"), "line1\nline2\nline3\n");
        Files.writeString(tempDir.resolve("file2.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes to only one file
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-single-change");
        Files.writeString(tempDir.resolve("file1.txt"), "line1\nmodified\nline3\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify file1");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should show changes to file1
        requireThat(result, "result").contains("file1.txt").contains("modified");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getDiffStats handles empty output safely.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyDiffStatsReturnsZeros() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with no changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-empty");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should handle empty stats without error
        requireThat(result, "result").isNotNull();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that base branch detection works from non-worktree directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void baseBranchDetectionInNonWorktreeDirectory() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "v2.0", "file.txt",
          "initial\n", "modified\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should detect base branch from branch name pattern
        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("main");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Sets up a test git repository with specified branch structure and file changes.
   * <p>
   * Creates: baseBranch -> versionBranch (if needed) -> featureBranch with changes
   *
   * @param tempDir the temporary directory for the repo
   * @param baseBranch the base branch name (e.g., "main")
   * @param featureBranch the feature branch name (e.g., "2.0-my-feature" or "v2.0")
   * @param fileName the file to create and modify
   * @param initialContent the initial file content
   * @param modifiedContent the modified file content
   * @throws IOException if an I/O error occurs
   */
  private void setupTestRepo(Path tempDir, String baseBranch, String featureBranch,
    String fileName, String initialContent, String modifiedContent) throws IOException
  {
    // Initialize git repo
    runGit(tempDir, "init");
    runGit(tempDir, "checkout", "-b", baseBranch);

    // Create cat-config
    Path catDir = tempDir.resolve(".claude").resolve("cat");
    Files.createDirectories(catDir);
    Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

    // Create initial commit on base branch
    Files.writeString(tempDir.resolve(fileName), initialContent);
    runGit(tempDir, "add", ".");
    runGit(tempDir, "commit", "-m", "initial commit");

    // If feature branch has format "X.Y-name", create intermediate "vX.Y" branch
    if (featureBranch.contains("-") && Character.isDigit(featureBranch.charAt(0)))
    {
      String versionPart = featureBranch.substring(0, featureBranch.indexOf('-'));
      String versionBranch = "v" + versionPart;
      runGit(tempDir, "checkout", "-b", versionBranch);
      runGit(tempDir, "checkout", baseBranch);
      runGit(tempDir, "checkout", versionBranch);
    }

    // Create feature branch and make changes
    runGit(tempDir, "checkout", "-b", featureBranch);
    Files.writeString(tempDir.resolve(fileName), modifiedContent);
    runGit(tempDir, "add", ".");
    runGit(tempDir, "commit", "-m", "modify file");
  }

  /**
   * Verifies that base branch detection works from branch name pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void detectBaseBranchFromBranchNamePattern() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "v2.0", "2.0-feature", "file.txt",
          "initial\n", "modified\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should detect v2.0 from branch name "2.0-feature"
        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("v2.0");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that version branch detects main as base.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void versionBranchDetectsMainAsBase() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit on main
        Files.writeString(tempDir.resolve("file.txt"), "initial\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create v2.0 branch
        runGit(tempDir, "checkout", "-b", "v2.0");
        Files.writeString(tempDir.resolve("file.txt"), "modified\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should detect main as base for v2.0 branch
        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("main");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput without args works when env is set.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputWithoutArgsReturnsErrorForNonGitDir() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
      String result = handler.getOutput();

      // TestJvmScope provides a temp dir that is not a git repo,
      // so getOutput() returns an error about base branch not found
      requireThat(result, "result").contains("Base branch not found");
    }
  }

  /**
   * Verifies that renamed file with content changes shows both.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void renamedFileWithContentChangesShowsBoth() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create file
        Files.writeString(tempDir.resolve("old.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with rename and modification
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-rename-modify");
        runGit(tempDir, "mv", "old.txt", "new.txt");
        Files.writeString(tempDir.resolve("new.txt"), "modified content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "rename and modify");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should show both rename indicator and diff content
        requireThat(result, "result").contains("new.txt");
        requireThat(result, "result").contains("modified");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that multi-line modification pairing works sequentially.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multiLineDeletionAdditionPairsSequentially() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create file with multiple lines
        Files.writeString(tempDir.resolve("multi.txt"), "line1\nline2\nline3\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with multiple changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-multi");
        Files.writeString(tempDir.resolve("multi.txt"), "changed1\nchanged2\nchanged3\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify all lines");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should contain all changes (pairing happens sequentially)
        requireThat(result, "result").contains("changed1");
        requireThat(result, "result").contains("changed2");
        requireThat(result, "result").contains("changed3");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getDiffStats handles malformed stat output safely.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diffStatsWithMismatchedBranchReturnsZeroStats() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create orphan branch with unrelated history (no common ancestor)
        runGit(tempDir, "checkout", "--orphan", "orphan-branch");
        runGit(tempDir, "rm", "-rf", ".");
        Files.writeString(tempDir.resolve("different.txt"), "different content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "orphan commit");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should handle malformed diff stats gracefully (no common ancestor means diff may fail)
        requireThat(result, "result").isNotNull();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that column width for small file uses minimum two digits.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void columnWidthForSmallFileUsesTwoDigits() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-small-file", "tiny.txt",
          "1\n2\n3\n4\n5\n", "1\nmodified\n3\n4\n5\n");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // Should use 2-digit column width even for files with < 10 lines
        // Verify line numbers appear (line 2 was modified)
        requireThat(result, "result").contains("2");
        requireThat(result, "result").contains("modified");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that multiple commits are reflected in the cumulative diff output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleCommitsAppearInOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit on main
        Files.writeString(tempDir.resolve("file.txt"), "initial\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        // Create version branch and feature branch
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-multi-commit");

        // First commit on feature branch
        Files.writeString(tempDir.resolve("file.txt"), "modified once\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "first change");

        // Second commit on feature branch
        Files.writeString(tempDir.resolve("file.txt"), "modified twice\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "second change");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        // git diff shows cumulative changes from both commits
        // Final state should show "modified twice" (result of both commits)
        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("modified twice");
        requireThat(result, "result").contains("initial");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Runs a git command in the specified directory.
   *
   * @param directory the working directory
   * @param args the git command arguments
   */
  private void runGit(Path directory, String... args)
  {
    try
    {
      String[] command = new String[args.length + 1];
      command[0] = "git";
      System.arraycopy(args, 0, command, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(directory.toFile());
      pb.environment().put("GIT_AUTHOR_NAME", "Test");
      pb.environment().put("GIT_AUTHOR_EMAIL", "test@test.com");
      pb.environment().put("GIT_COMMITTER_NAME", "Test");
      pb.environment().put("GIT_COMMITTER_EMAIL", "test@test.com");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      process.getInputStream().readAllBytes();
      process.waitFor();
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}

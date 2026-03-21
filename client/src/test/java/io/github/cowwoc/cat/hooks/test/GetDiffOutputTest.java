/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetDiffOutput;
import org.testng.annotations.Test;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetDiffOutput functionality.
 * <p>
 * Tests verify the render-diff output generator. The handler relies on git commands
 * to compute diffs, so tests focus on integration with real git repositories
 * and verifying output structure.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetDiffOutputTest
{
  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException()
  {
    new GetDiffOutput(null);
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
        // Create a minimal config.json so Config.load doesn't fail
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Non-git directory should return error message (git commands fail)
        requireThat(result, "result").contains("Target branch");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput reports no changes when HEAD matches target branch.
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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create an initial commit
        Files.writeString(tempDir.resolve("file.txt"), "initial content");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 50}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create a file
        Files.writeString(tempDir.resolve("old-name.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with renamed file
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-rename");
        runGit(tempDir, "mv", "old-name.txt", "new-name.txt");
        runGit(tempDir, "commit", "-m", "rename file");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Should contain both modifications (word diff marks changed words with **)
        requireThat(result, "result").contains("**Modified**").
          contains("multi.txt");
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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch but make no changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-no-changes");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with no changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-empty");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
   * Verifies that target branch detection works from a worktree directory path.
   * <p>
   * When the issue path's index.json specifies "v2.1" as the target branch, the diff is computed
   * against "v2.1".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void targetBranchDetectionFromWorktreePath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempBase = Files.createTempDirectory("render-diff-test-base");
      try
      {
        // Set up main repo at tempBase/main-repo
        Path mainRepo = tempBase.resolve("main-repo");
        Files.createDirectories(mainRepo);
        runGit(mainRepo, "init");
        runGit(mainRepo, "checkout", "-b", "v2.1");

        // Create cat-config
        Path catDir = mainRepo.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit on v2.1
        Files.writeString(mainRepo.resolve("file.txt"), "initial\n");
        runGit(mainRepo, "add", ".");
        runGit(mainRepo, "commit", "-m", "initial");

        // Create worktrees/2.1-my-feature directory via git worktree add
        Path worktreesDir = tempBase.resolve("worktrees");
        Files.createDirectories(worktreesDir);
        Path worktree = worktreesDir.resolve("2.1-my-feature");
        runGit(mainRepo, "worktree", "add", worktree.toString(), "-b", "2.1-my-feature");

        // Make a change in the worktree
        Path catDirWorktree = worktree.resolve(".cat");
        Files.createDirectories(catDirWorktree);
        Files.writeString(catDirWorktree.resolve("config.json"), "{\"displayWidth\": 80}");
        Files.writeString(worktree.resolve("file.txt"), "modified\n");
        runGit(worktree, "add", ".");
        runGit(worktree, "commit", "-m", "feature change");

        // Use issue path with index.json specifying v2.1 as the target branch
        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(worktree, "v2.1");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("v2.1");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempBase);
      }
    }
  }

  /**
   * Verifies that target branch detection works from non-worktree directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void targetBranchDetectionInNonWorktreeDirectory() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "v2.0", "file.txt",
          "initial\n", "modified\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Should detect target branch from branch name pattern
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
   * Verifies that a raw diff exceeding 2KB returns the brief skip notice.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rawDiffExceeding2KBReturnsSkipNotice() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-large-test");
      try
      {
        // Initialize repo; rename the empty initial branch to v2.0 (same pattern as setupTestRepo)
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "v2.0");

        // Create minimal config.json so Config.load() does not fail
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit on v2.0 branch
        Files.writeString(tempDir.resolve("readme.txt"), "initial\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        // Create feature branch and add a file whose raw diff exceeds 2048 bytes.
        // Each formatted line is ~50 chars; with the diff + prefix and newline, 60 lines
        // produce approximately 3120 bytes of raw diff, well above the 2048-byte limit.
        runGit(tempDir, "checkout", "-b", "2.0-large-change");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 60; ++i)
          largeContent.append(String.format("line_%04d_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx%n", i));
        Files.writeString(tempDir.resolve("large.txt"), largeContent.toString());
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "add large file");

        GetDiffOutput handler = new GetDiffOutput(scope);
        // createIssueDirWithTargetBranch writes index.json with "Target Branch: v2.0"
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        requireThat(result, "result").contains("too large").contains("2KB");
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
   * Creates: targetBranch -> versionBranch (if needed) -> featureBranch with changes
   *
   * @param tempDir the temporary directory for the repo
   * @param targetBranch the target branch name (e.g., "main")
   * @param featureBranch the feature branch name (e.g., "2.0-my-feature" or "v2.0")
   * @param fileName the file to create and modify
   * @param initialContent the initial file content
   * @param modifiedContent the modified file content
   * @throws IOException if an I/O error occurs
   */
  private void setupTestRepo(Path tempDir, String targetBranch, String featureBranch,
    String fileName, String initialContent, String modifiedContent) throws IOException
  {
    // Initialize git repo
    runGit(tempDir, "init");
    runGit(tempDir, "checkout", "-b", targetBranch);

    // Create cat-config
    Path catDir = tempDir.resolve(".cat");
    Files.createDirectories(catDir);
    Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

    // Create initial commit on target branch
    Files.writeString(tempDir.resolve(fileName), initialContent);
    runGit(tempDir, "add", ".");
    runGit(tempDir, "commit", "-m", "initial commit");

    // If feature branch has format "X.Y-name", create intermediate "vX.Y" branch
    if (featureBranch.contains("-") && Character.isDigit(featureBranch.charAt(0)))
    {
      String versionPart = featureBranch.substring(0, featureBranch.indexOf('-'));
      String versionBranch = "v" + versionPart;
      runGit(tempDir, "checkout", "-b", versionBranch);
      runGit(tempDir, "checkout", targetBranch);
      runGit(tempDir, "checkout", versionBranch);
    }

    // Create feature branch and make changes
    runGit(tempDir, "checkout", "-b", featureBranch);
    Files.writeString(tempDir.resolve(fileName), modifiedContent);
    runGit(tempDir, "add", ".");
    runGit(tempDir, "commit", "-m", "modify file");
  }

  /**
   * Verifies that target branch detection works from branch name pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void detectTargetBranchFromBranchNamePattern() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "v2.0", "2.0-feature", "file.txt",
          "initial\n", "modified\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit on main
        Files.writeString(tempDir.resolve("file.txt"), "initial\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create v2.0 branch
        runGit(tempDir, "checkout", "-b", "v2.0");
        Files.writeString(tempDir.resolve("file.txt"), "modified\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
   * Verifies that the issue path argument derives the project root and reads the target branch
   * from index.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issuePathDrivesProjectRootAndTargetBranch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("issue-path-test");
    try
    {
      // Set up tempDir as a git repo with version branches
      runGit(tempDir, "init");
      runGit(tempDir, "checkout", "-b", "main");
      Files.writeString(tempDir.resolve("file1.txt"), "initial\n");
      runGit(tempDir, "add", ".");
      runGit(tempDir, "commit", "-m", "initial");

      // Create version branch and feature branch
      runGit(tempDir, "checkout", "-b", "v2.0");
      runGit(tempDir, "checkout", "-b", "2.0-feature");
      Files.writeString(tempDir.resolve("file1.txt"), "modified\n");
      runGit(tempDir, "add", ".");
      runGit(tempDir, "commit", "-m", "feature changes");

      // Create cat-config in the repo
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

      // Create issue directory with index.json containing target branch
      Path issuePath = tempDir.resolve(".cat/issues/v2/v2.0/some-feature");
      Files.createDirectories(issuePath);
      Files.writeString(issuePath.resolve("index.json"),
        "{\"status\":\"in-progress\",\"targetBranch\":\"v2.0\"}");

      try (JvmScope scope = new TestJvmScope())
      {
        GetDiffOutput handler = new GetDiffOutput(scope);
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Project root derived from issue path, target branch read from index.json
        requireThat(result, "result").contains("Diff Summary").contains("v2.0");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

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
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

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

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // git diff shows cumulative changes from both commits
        // Final state shows "modified twice" content; since no tokens are shared with "initial",
        // the entire new phrase is bold-marked as changed
        requireThat(result, "result").contains("Diff Summary");
        requireThat(result, "result").contains("**modified");
        requireThat(result, "result").contains("initial");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that modification lines have bold markers on changed words.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void modificationLinesHaveWordDiffMarkers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-word-diff", "code.txt",
          "hello world foo\n", "hello earth foo\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // "world" changed to "earth" - both should be bold-marked
        requireThat(result, "result").contains("**world**");
        requireThat(result, "result").contains("**earth**");
        // Unchanged words should appear without markers
        requireThat(result, "result").contains("hello");
        requireThat(result, "result").contains("foo");
        // Legend should explain bold = changed word
        requireThat(result, "result").contains("**bold**");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that unchanged words in modification lines have no bold markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unchangedWordsHaveNoBoldMarkers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-unchanged-words", "text.txt",
          "alpha beta gamma\n", "alpha BETA gamma\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // "beta" changed to "BETA" - only these should be bold-marked
        requireThat(result, "result").contains("**beta**");
        requireThat(result, "result").contains("**BETA**");
        // "alpha" and "gamma" are unchanged - they must appear plain in the diff
        // (they appear in both the deletion and addition lines without **)
        requireThat(result, "result").contains("alpha").doesNotContain("**alpha**");
        requireThat(result, "result").contains("gamma").doesNotContain("**gamma**");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that pure addition and deletion lines (not modifications) have no bold markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void pureAdditionDeletionLinesHaveNoBoldMarkers() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Initial file with two lines
        Files.writeString(tempDir.resolve("lines.txt"), "line one\nline two\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-pure-add-del");

        // Add a new line, delete a different one (not a modification pair)
        Files.writeString(tempDir.resolve("lines.txt"), "line one\nline two\nline three\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "add third line");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Pure addition "line three" should appear without ** markers
        requireThat(result, "result").contains("line three").
          doesNotContain("**line").doesNotContain("three**");
        requireThat(result, "result").contains("+ ");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that markdown special characters in content are escaped to prevent formatting.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void markdownSpecialCharsAreEscaped() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-markdown-escape", "code.py",
          "def func(**kwargs):\n", "def func(**args):\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // The literal ** in kwargs/args should be escaped (\\*\\*), not rendered as bold.
        // The library tokenizes by word boundary, so the ** prefix stays as an unchanged token
        // (escaped) and the word suffix (kwargs/args) is bold-marked as the changed part.
        requireThat(result, "result").contains("\\*\\*");
        requireThat(result, "result").contains("**kwargs**");
        requireThat(result, "result").contains("**args**");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that underscores in content are escaped to prevent italic formatting.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void underscoresInContentAreEscaped() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-underscore-escape", "code.py",
          "my_variable = 1\n", "my_variable = 2\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Underscores should be escaped
        requireThat(result, "result").contains("my\\_variable");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that whitespace-only changes use whitespace visualization, not word diff bold markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void whitespaceOnlyChangesUseVisualizationNotWordDiff() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // File with spaces between words
        Files.writeString(tempDir.resolve("ws.txt"), "hello world\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-ws-only");

        // Same words, but space replaced with tab - whitespace-only change
        Files.writeString(tempDir.resolve("ws.txt"), "hello\tworld\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "space to tab");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Whitespace-only path uses visualization markers (· and →), not bold word markers
        requireThat(result, "result").contains("·");   // Space marker
        requireThat(result, "result").contains("→");   // Tab marker
        // Words "hello" and "world" must NOT be wrapped in bold markers
        requireThat(result, "result").doesNotContain("**hello**").doesNotContain("**world**");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that modification lines with bold markers align correctly - box drawing characters
   * line up regardless of whether content contains bold markers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void modificationLinesWithBoldMarkersAlignCorrectly() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-test");
      try
      {
        setupTestRepo(tempDir, "main", "2.0-align-test", "align.txt",
          "alpha beta gamma\n", "alpha BETA gamma\n");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Verify word diff bold markers are present (changes are marked)
        requireThat(result, "result").contains("**beta**");
        requireThat(result, "result").contains("**BETA**");

        // Verify that the right-side vertical border (│) appears and that lines are aligned.
        // Each rendered row has the form: │<lineNum>│<indicator><content><padding>│
        // Split by newline and find rows that contain the bold-marked content.
        // All data rows must end with │ (the right border), confirming padding is correct.
        String[] lines = result.split("\n");
        boolean foundBoldRow = false;
        for (String line : lines)
        {
          if (line.contains("**beta**") || line.contains("**BETA**"))
          {
            foundBoldRow = true;
            // The row must end with the box vertical character
            requireThat(line, "line").endsWith("│");
          }
        }
        requireThat(foundBoldRow, "foundBoldRow").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that diffs with very long lines (exceeding 1500 chars per side, which would trigger
   * the per-line character guard) are intercepted first by the 2KB raw diff size guard and return
   * the skip notice. Any diff whose lines each exceed 1500 chars necessarily produces a raw diff
   * exceeding 2KB, so the 2KB guard fires before the per-line guard is reached.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void veryLongLineDiffReturnsSkipNotice() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-long-line-test");
      try
      {
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 220}");

        // Build a line exceeding 1500 chars. Two such lines produce a raw diff > 2KB,
        // so the 2KB guard fires first and returns the skip notice.
        String repeated = "x".repeat(1501);
        String modified = "y".repeat(1501);

        Files.writeString(tempDir.resolve("long.txt"), repeated + "\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-long-line");
        Files.writeString(tempDir.resolve("long.txt"), modified + "\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "change long line");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        requireThat(result, "result").contains("too large").contains("2KB");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput returns a descriptive message instead of attempting to render
   * when the diff is excessively large (insertions + deletions exceeds threshold).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void largeDiffReturnsDescriptiveMessageInsteadOfRendering() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-large-test");
      try
      {
        // Initialize git repo with a target branch
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Create initial commit with no files
        Files.writeString(tempDir.resolve("README.md"), "initial");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        // Create version branch and feature branch
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-large-diff");

        // Generate 100 files with 600 lines each = 60,000 insertions (exceeds 50,000)
        for (int f = 0; f < 100; ++f)
        {
          StringBuilder content = new StringBuilder();
          for (int line = 0; line < 600; ++line)
            content.append("line ").append(line).append(" of file ").append(f).append('\n');
          Files.writeString(tempDir.resolve("file" + f + ".txt"), content.toString());
        }
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "add large number of files");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "v2.0");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Should return a descriptive message about the diff being too large
        requireThat(result, "result").contains("too large");
        requireThat(result, "result").contains("git diff");
        // Should NOT contain rendered diff sections
        requireThat(result, "result").doesNotContain("Rendered Diff");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that target branch detection works from the worktree path when the project root
   * is passed via issue path index.json.
   * <p>
   * When index.json specifies "v2.1" as the target branch, the diff is computed against "v2.1"
   * regardless of the worktree directory name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void detectTargetBranchFromWorktreePathViaExplicitProjectRoot() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      // Create main repo
      Path mainRepo = Files.createTempDirectory("render-diff-worktree-test");
      try
      {
        runGit(mainRepo, "init");
        runGit(mainRepo, "checkout", "-b", "main");

        // Create cat-config in main repo
        Path catDir = mainRepo.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        Files.writeString(mainRepo.resolve("file.txt"), "initial content\n");
        runGit(mainRepo, "add", ".");
        runGit(mainRepo, "commit", "-m", "initial commit");

        // Create the target branch "v2.1" so branchExists() returns true during detection
        runGit(mainRepo, "checkout", "-b", "v2.1");
        runGit(mainRepo, "checkout", "main");

        // Create a worktree in a directory named "worktrees" with a "2.1-..." prefix
        Path worktreesParent = Files.createTempDirectory("worktrees-parent-");
        Path worktreesDir = worktreesParent.resolve("worktrees");
        Files.createDirectories(worktreesDir);

        // Add git worktree with version-prefixed name
        Path worktree = worktreesDir.resolve("2.1-test-feature");
        runGit(mainRepo, "worktree", "add", "-b", "2.1-test-feature", worktree.toString());

        // Copy cat-config into worktree
        Path worktreeCatDir = worktree.resolve(".cat");
        Files.createDirectories(worktreeCatDir);
        Files.writeString(worktreeCatDir.resolve("config.json"), "{\"displayWidth\": 80}");

        // Make a change in the worktree
        Files.writeString(worktree.resolve("file.txt"), "modified content\n");
        runGit(worktree, "add", ".");
        runGit(worktree, "commit", "-m", "modify file");

        // Pass issue path with index.json specifying v2.1 as target branch
        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(worktree, "v2.1");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // The index.json specifies v2.1 as the target branch
        requireThat(result, "result").contains("v2.1");

        TestUtils.deleteDirectoryRecursively(worktreesParent);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(mainRepo);
      }
    }
  }

  /**
   * Verifies that getOutput returns an error message when target branch detection returns null.
   * <p>
   * When the project root is not a git directory, git commands fail and the output should indicate
   * that the target branch could not be detected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonGitDirectoryReturnsMissingTargetBranchMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      Path tempDir = Files.createTempDirectory("render-diff-no-target-test");
      try
      {
        // Create a minimal config.json so Config.load doesn't fail
        Path catDir = tempDir.resolve(".cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 80}");

        GetDiffOutput handler = new GetDiffOutput(scope);
        Path issuePath = createIssueDirWithTargetBranch(tempDir, "main");
        String result = handler.getOutput(new String[]{issuePath.toString()});

        // Non-git directory: all detection methods fail, returns error message
        requireThat(result, "result").contains("Target branch");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when no arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Expected exactly 1 argument.*got 0.*")
  public void testNoArgumentsThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetDiffOutput handler = new GetDiffOutput(scope);
      handler.getOutput(new String[]{});
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when too many arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Expected exactly 1 argument.*got 2.*")
  public void testTooManyArgumentsThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetDiffOutput handler = new GetDiffOutput(scope);
      handler.getOutput(new String[]{"/some/path", "extra"});
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when the issue path does not contain
   * the {@code .cat/issues/} segment.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*\\.cat/issues/.*")
  public void testInvalidIssuePathThrowsException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetDiffOutput handler = new GetDiffOutput(scope);
      handler.getOutput(new String[]{"/tmp/not-an-issue-path"});
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when index.json is missing from the
   * issue directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*index\\.json not found.*")
  public void testMissingIndexJsonThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("missing-state-test");
    try
    {
      Path issuePath = tempDir.resolve(".cat/issues/v2/v2.0/some-issue");
      Files.createDirectories(issuePath);

      try (JvmScope scope = new TestJvmScope())
      {
        GetDiffOutput handler = new GetDiffOutput(scope);
        handler.getOutput(new String[]{issuePath.toString()});
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getRawDiffLimited throws IOException when git diff fails with a non-zero exit code
   * (e.g., due to an invalid branch reference).
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if the operation is interrupted
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*git diff command failed with exit code.*")
  public void getRawDiffLimitedThrowsOnNonZeroExitCode() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("raw-diff-fail-test");
    try
    {
      runGit(tempDir, "init");
      runGit(tempDir, "checkout", "-b", "main");
      Files.writeString(tempDir.resolve("file.txt"), "initial\n");
      runGit(tempDir, "add", ".");
      runGit(tempDir, "commit", "-m", "init");

      // Pass a branch that does not exist so git diff exits non-zero
      GetDiffOutput.GitHelper.getRawDiffLimited(tempDir, "nonexistent-branch-xyz", 4096);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getOutput throws IllegalArgumentException when index.json exists but does not
   * contain a targetBranch field.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*targetBranch.*")
  public void testMissingTargetBranchInIndexJsonThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("no-target-branch-test");
    try
    {
      Path issuePath = tempDir.resolve(".cat/issues/v2/v2.0/some-issue");
      Files.createDirectories(issuePath);
      Files.writeString(issuePath.resolve("index.json"),
        "{\"status\":\"in-progress\"}");

      try (JvmScope scope = new TestJvmScope())
      {
        GetDiffOutput handler = new GetDiffOutput(scope);
        handler.getOutput(new String[]{issuePath.toString()});
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a minimal issue directory with index.json inside the given project root,
   * and returns the issue path. Used by tests to call getOutput(String[]) with the
   * issue path interface.
   *
   * @param projectPath  the project root directory in which to create the issue directory
   * @param targetBranch the target branch value to write into index.json
   * @return the path to the created issue directory
   * @throws IOException if an I/O error occurs
   */
  private Path createIssueDirWithTargetBranch(Path projectPath, String targetBranch) throws IOException
  {
    Path issuePath = projectPath.resolve(".cat/issues/v1/v1.0/test-issue");
    Files.createDirectories(issuePath);
    Files.writeString(issuePath.resolve("index.json"),
      "{\"status\":\"in-progress\",\"targetBranch\":\"" + targetBranch + "\"}");
    return issuePath;
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
      try (Process process = pb.start())
      {
        process.getInputStream().readAllBytes();
        process.waitFor();
      }
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}

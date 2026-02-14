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
   * Verifies that getOutput returns null for a non-git directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonGitDirectoryReturnsNull() throws IOException
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

        // Non-git directory should return null (git commands fail)
        requireThat(result, "result").isNull();
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
        // Initialize git repo with main branch
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Create initial commit
        Files.writeString(tempDir.resolve("file.txt"), "initial content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial commit");

        // Create a feature branch with changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-my-feature");
        Files.writeString(tempDir.resolve("file.txt"), "modified content\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "modify file");

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
   * Verifies that diff output includes rendered 4-column format section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diffOutputIncludes4ColumnFormat() throws IOException
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
        Files.writeString(tempDir.resolve("code.java"), "class Foo {\n}\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-add-method");
        Files.writeString(tempDir.resolve("code.java"), "class Foo {\n  void bar() {}\n}\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "add method");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Rendered Diff (4-column format)").
          contains("FILE:");
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
        // Initialize git repo
        runGit(tempDir, "init");
        runGit(tempDir, "checkout", "-b", "main");

        // Create cat-config
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.json"), "{\"terminalWidth\": 80}");

        // Initial commit
        Files.writeString(tempDir.resolve("data.txt"), "line1\nline2\nline3\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "initial");

        // Create branch with changes
        runGit(tempDir, "checkout", "-b", "v2.0");
        runGit(tempDir, "checkout", "-b", "2.0-edit-data");
        Files.writeString(tempDir.resolve("data.txt"), "line1\nmodified\nline3\nnew line\n");
        runGit(tempDir, "add", ".");
        runGit(tempDir, "commit", "-m", "edit data");

        GetRenderDiffOutput handler = new GetRenderDiffOutput(scope);
        String result = handler.getOutput(tempDir);

        requireThat(result, "result").contains("Insertions:").contains("Deletions:");
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

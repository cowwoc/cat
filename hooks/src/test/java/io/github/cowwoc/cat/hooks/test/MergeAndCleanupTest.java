package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.MergeAndCleanup;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for MergeAndCleanup validation and error handling.
 * <p>
 * Tests verify input validation without requiring actual git repository setup.
 */
public class MergeAndCleanupTest
{
  /**
   * Creates a temporary directory for testing.
   *
   * @return the temporary directory path
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("merge-and-cleanup-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Verifies that execute rejects null projectDir.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullProjectDir() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup();

      try
      {
        cmd.execute(null, "issue-id", "session-id", "", tempDir.toString());
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("projectDir");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects blank projectDir.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankProjectDir() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup();

      try
      {
        cmd.execute("", "issue-id", "session-id", "", tempDir.toString());
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("projectDir");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects null issueId.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullIssueId() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup();

      try
      {
        cmd.execute(tempDir.toString(), null, "session-id", "", tempDir.toString());
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("issueId");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects directory without cat config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNonCatProject() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      MergeAndCleanup cmd = new MergeAndCleanup();

      try
      {
        cmd.execute(tempDir.toString(), "issue-id", "session-id", "",
          tempDir.toString());
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Not a CAT project");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute accepts empty worktreePath for auto-detect.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeAcceptsEmptyWorktreePath() throws IOException
  {
    Path tempDir = createTempDir();
    try
    {
      Path catDir = tempDir.resolve(".claude/cat");
      Files.createDirectories(catDir);

      MergeAndCleanup cmd = new MergeAndCleanup();

      try
      {
        cmd.execute(tempDir.toString(), "issue-id", "session-id", "",
          tempDir.toString());
      }
      catch (IOException _)
      {
        // Expected - worktree not found
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

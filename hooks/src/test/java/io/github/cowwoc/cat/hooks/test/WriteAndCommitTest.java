package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.WriteAndCommit;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for WriteAndCommit validation and error handling.
 * <p>
 * Tests verify input validation and error paths without requiring
 * actual git repository setup.
 */
public class WriteAndCommitTest
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
      return Files.createTempDirectory("write-and-commit-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Verifies that execute rejects null filePath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsNullFilePath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(contentFile, "test content");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope.getJsonMapper());

      try
      {
        cmd.execute(null, contentFile.toString(), commitMsgFile.toString(), false);
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (NullPointerException e)
      {
        requireThat(e.getMessage(), "message").contains("filePath");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects blank filePath.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsBlankFilePath() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(contentFile, "test content");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope.getJsonMapper());

      try
      {
        cmd.execute("", contentFile.toString(), commitMsgFile.toString(), false);
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("filePath");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects missing content file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsMissingContentFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path commitMsgFile = tempDir.resolve("commit.txt");
      Files.writeString(commitMsgFile, "test commit");

      WriteAndCommit cmd = new WriteAndCommit(scope.getJsonMapper());

      try
      {
        cmd.execute("test.txt", tempDir.resolve("missing.txt").toString(),
          commitMsgFile.toString(), false);
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Content file not found");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }

  /**
   * Verifies that execute rejects missing commit message file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeRejectsMissingCommitMsgFile() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
    Path tempDir = createTempDir();
    try
    {
      Path contentFile = tempDir.resolve("content.txt");
      Files.writeString(contentFile, "test content");

      WriteAndCommit cmd = new WriteAndCommit(scope.getJsonMapper());

      try
      {
        cmd.execute("test.txt", contentFile.toString(),
          tempDir.resolve("missing.txt").toString(), false);
        requireThat(false, "execute").isEqualTo(true);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("Commit message file not found");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
    }
  }
}

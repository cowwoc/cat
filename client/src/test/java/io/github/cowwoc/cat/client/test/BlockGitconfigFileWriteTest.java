/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.write.BlockGitconfigFileWrite;
import org.testng.annotations.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockGitconfigFileWrite}.
 */
public final class BlockGitconfigFileWriteTest
{
  /**
   * Verifies that writing to ~/.gitconfig (tilde form) is blocked.
   */
  @Test
  public void writeTildeGitconfigIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      String home = System.getProperty("user.home");
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", home + "/.gitconfig");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("gitconfig");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing to ~/.config/git/config is blocked.
   */
  @Test
  public void writeXdgGitConfigIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      String home = System.getProperty("user.home");
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", home + "/.config/git/config");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("gitconfig");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing to /etc/gitconfig is blocked.
   */
  @Test
  public void writeEtcGitconfigIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", "/etc/gitconfig");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("gitconfig");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing to an unrelated file is allowed.
   */
  @Test
  public void writeUnrelatedFileIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", "/tmp/README.md");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing to a file whose name contains "gitconfig" but is not a canonical path is allowed.
   */
  @Test
  public void writeNonCanonicalGitconfigFileIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", "/tmp/test.gitconfig");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing file_path field results in allow.
   */
  @Test
  public void missingFilePathIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      ObjectNode input = scope.getJsonMapper().createObjectNode();

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty file_path results in allow.
   */
  @Test
  public void emptyFilePathIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", "");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the block message instructs the user to request the change explicitly.
   */
  @Test
  public void blockMessageMentionsExplicitUserRequest() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
      String home = System.getProperty("user.home");
      ObjectNode input = scope.getJsonMapper().createObjectNode();
      input.put("file_path", home + "/.gitconfig");

      FileWriteHandler.Result result = handler.check(input, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("explicitly");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

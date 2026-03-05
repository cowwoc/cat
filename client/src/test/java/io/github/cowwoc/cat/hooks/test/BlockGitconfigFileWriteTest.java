/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.write.BlockGitconfigFileWrite;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockGitconfigFileWrite}.
 */
public final class BlockGitconfigFileWriteTest
{
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /**
   * Builds a tool input node with the given file_path.
   *
   * @param filePath the file path value
   * @return an ObjectNode with file_path set
   */
  private static ObjectNode inputWithPath(String filePath)
  {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("file_path", filePath);
    return node;
  }

  /**
   * Verifies that writing to ~/.gitconfig (tilde form) is blocked.
   */
  @Test
  public void writeTildeGitconfigIsBlocked()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    String home = System.getProperty("user.home");
    ObjectNode input = inputWithPath(home + "/.gitconfig");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("gitconfig");
  }

  /**
   * Verifies that writing to ~/.config/git/config is blocked.
   */
  @Test
  public void writeXdgGitConfigIsBlocked()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    String home = System.getProperty("user.home");
    ObjectNode input = inputWithPath(home + "/.config/git/config");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("gitconfig");
  }

  /**
   * Verifies that writing to /etc/gitconfig is blocked.
   */
  @Test
  public void writeEtcGitconfigIsBlocked()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    ObjectNode input = inputWithPath("/etc/gitconfig");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("gitconfig");
  }

  /**
   * Verifies that writing to an unrelated file is allowed.
   */
  @Test
  public void writeUnrelatedFileIsAllowed()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    ObjectNode input = inputWithPath("/workspace/README.md");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that writing to a file whose name contains "gitconfig" but is not a canonical path is allowed.
   */
  @Test
  public void writeNonCanonicalGitconfigFileIsAllowed()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    ObjectNode input = inputWithPath("/workspace/test.gitconfig");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that missing file_path field results in allow.
   */
  @Test
  public void missingFilePathIsAllowed()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    ObjectNode input = MAPPER.createObjectNode();

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that empty file_path results in allow.
   */
  @Test
  public void emptyFilePathIsAllowed()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    ObjectNode input = inputWithPath("");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that the block message instructs the user to request the change explicitly.
   */
  @Test
  public void blockMessageMentionsExplicitUserRequest()
  {
    BlockGitconfigFileWrite handler = new BlockGitconfigFileWrite();
    String home = System.getProperty("user.home");
    ObjectNode input = inputWithPath(home + "/.gitconfig");

    FileWriteHandler.Result result = handler.check(input, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("explicitly");
  }
}

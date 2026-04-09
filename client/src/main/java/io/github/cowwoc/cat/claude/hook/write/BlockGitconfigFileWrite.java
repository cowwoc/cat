/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Block direct Write/Edit operations targeting canonical gitconfig files.
 * <p>
 * Prevents agents from modifying git identity by directly writing to canonical gitconfig paths
 * via the Write or Edit tool, which would bypass the BlockGitUserConfigChange bash handler.
 * <p>
 * Protected paths:
 * <ul>
 *   <li>{@code ~/.gitconfig} (user-level config)</li>
 *   <li>{@code ~/.config/git/config} (XDG-compliant user config)</li>
 *   <li>{@code /etc/gitconfig} (system-wide config)</li>
 * </ul>
 */
public final class BlockGitconfigFileWrite implements FileWriteHandler
{
  /**
   * Creates a new handler for blocking gitconfig file writes.
   */
  public BlockGitconfigFileWrite()
  {
    // Handler class
  }

  /**
   * Check if the write should be blocked due to targeting a canonical gitconfig file.
   *
   * @param toolInput the tool input JSON
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the check result
   * @throws NullPointerException if {@code toolInput} or {@code catAgentId} is null
   * @throws IllegalArgumentException if {@code catAgentId} is blank
   */
  @Override
  public Result check(JsonNode toolInput, String catAgentId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(catAgentId, "catAgentId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    if (filePath.isEmpty())
      return Result.allow();

    if (isCanonicalGitconfigPath(filePath))
    {
      return Result.block(
        "**BLOCKED: Cannot write to canonical gitconfig file without explicit user request**\n" +
          "\n" +
          "Writing directly to git configuration files (`~/.gitconfig`, `~/.config/git/config`, " +
          "`/etc/gitconfig`) silently overwrites the author information on every future commit.\n" +
          "\n" +
          "Only change git identity when the user explicitly asks you to (e.g., " +
          "\"set my git username to Alice\").\n" +
          "\n" +
          "To safely read or modify git identity:\n" +
          "```bash\n" +
          "git config user.name        # read current name\n" +
          "git config user.email       # read current email\n" +
          "git config user.name Alice  # set new name (with explicit user request)\n" +
          "```");
    }

    return Result.allow();
  }

  /**
   * Check if the given file path is a canonical gitconfig path.
   * <p>
   * Matches these patterns exactly:
   * <ul>
   *   <li>{@code ~/.gitconfig}</li>
   *   <li>{@code ~/.config/git/config}</li>
   *   <li>{@code /etc/gitconfig}</li>
   * </ul>
   *
   * @param filePath the file path to check
   * @return true if the path is a canonical gitconfig path, false otherwise
   */
  private static boolean isCanonicalGitconfigPath(String filePath)
  {
    Path path = Paths.get(filePath);

    String userHome = System.getProperty("user.home");
    if (userHome != null)
    {
      Path home = Paths.get(userHome);
      if (path.equals(home.resolve(".gitconfig")))
        return true;
      if (path.equals(home.resolve(".config/git/config")))
        return true;
    }

    return path.equals(Paths.get("/etc/gitconfig"));
  }
}

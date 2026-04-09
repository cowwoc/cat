/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.ask;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AskHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.IssueStatus;
import io.github.cowwoc.cat.claude.hook.util.GitCommands;
import io.github.cowwoc.cat.claude.hook.util.IssueDiscovery;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Validates that the issue is closed before presenting the approval gate.
 * <p>
 * Fires on {@code AskUserQuestion} tool calls that present an approval gate (detected by
 * the presence of "approve" in the question text). Blocks the gate when the issue's
 * {@code index.json} has a status other than {@code "closed"}.
 */
public final class VerifyStateInCommit implements AskHandler
{
  private final ClaudeHook scope;

  /**
   * Creates a new VerifyStateInCommit handler.
   *
   * @param scope the JVM scope providing access to the project path and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public VerifyStateInCommit(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    // Only fire when this looks like an approval gate question.
    String toolInputText = toolInput.toString();
    if (!toolInputText.toLowerCase(Locale.ROOT).contains("approve"))
      return Result.allow();

    String branch;
    try
    {
      branch = GitCommands.getCurrentBranch(scope.getProjectPath().toString());
    }
    catch (IOException e)
    {
      // Fail-fast: if the branch cannot be determined the approval gate cannot be validated.
      return Result.block("BLOCKED: Cannot determine the current branch before presenting the " +
        "approval gate.\n\nCause: " + e.getMessage() + "\n\nEnsure the agent is running in a " +
        "valid git worktree.");
    }

    String relativePath = IssueDiscovery.branchToIndexJsonPath(branch);
    if (relativePath == null)
    {
      // Branch does not follow the CAT naming convention — not a managed issue worktree.
      return Result.allow();
    }

    Path indexJsonPath = scope.getProjectPath().resolve(relativePath);
    if (!Files.exists(indexJsonPath))
    {
      // index.json is absent — treat as not closed to guard against forgotten files.
      return Result.block(buildBlockMessage(relativePath));
    }

    String content;
    try
    {
      content = Files.readString(indexJsonPath);
    }
    catch (IOException e)
    {
      // Fail-fast: if index.json cannot be read the approval gate cannot be validated.
      return Result.block("BLOCKED: Cannot read index.json before presenting the approval gate.\n" +
        "\n" +
        "Path: " + relativePath + "\n" +
        "Cause: " + e.getMessage() + "\n\n" +
        "Ensure index.json exists and is readable in the worktree.");
    }

    try
    {
      if (!isClosedStatus(content))
        return Result.block(buildBlockMessage(relativePath));
    }
    catch (JacksonException e)
    {
      // Fail-fast: malformed index.json cannot be validated.
      return Result.block("BLOCKED: index.json is malformed and cannot be parsed before " +
        "presenting the approval gate.\n" +
        "\n" +
        "Path: " + relativePath + "\n" +
        "Cause: " + e.getMessage() + "\n\n" +
        "Fix the JSON syntax in index.json, then re-run the squash step.");
    }

    return Result.allow();
  }

  /**
   * Returns whether the index.json content represents a closed issue.
   * <p>
   * Returns {@code false} when the status field is missing or not {@code "closed"}.
   * Throws on parse errors so callers can report them explicitly rather than silently
   * treating malformed content as "not closed".
   *
   * @param content the JSON content of the index.json file
   * @return {@code true} if the status field is {@code "closed"}
   * @throws JacksonException if the content cannot be parsed as JSON (unchecked)
   */
  private boolean isClosedStatus(String content)
  {
    JsonNode root = scope.getJsonMapper().readTree(content);
    JsonNode statusNode = root.get("status");
    if (statusNode == null || !statusNode.isString())
      return false;
    return IssueStatus.fromString(statusNode.asString()) == IssueStatus.CLOSED;
  }

  /**
   * Builds the block message for an unclosed issue.
   *
   * @param relativePath the relative path to the index.json file
   * @return the block message
   */
  private String buildBlockMessage(String relativePath)
  {
    return "BLOCKED: Issue index.json must be 'closed' before presenting the approval gate.\n" +
      "\n" +
      "Run the squash step to auto-close it, or manually set index.json status to 'closed' " +
      "and commit it.\n" +
      "\n" +
      "Expected path: " + relativePath;
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate commit message uses correct commit types.
 * <p>
 * Blocks commits with invalid types like 'feat:', 'fix:', etc.
 * Requires specific conventional commit types.
 * <p>
 * Also validates that commit type matches file patterns:
 * <ul>
 *   <li>docs: only for user-facing files (README, API docs)</li>
 *   <li>config: for Claude-facing files (CLAUDE.md, plugin/, skills/)</li>
 * </ul>
 */
public final class ValidateCommitType implements BashHandler
{
  private static final Set<String> VALID_COMMIT_TYPES = Set.of(
    "feature",      // New feature (NOT "feat")
    "bugfix",       // Bug fix (NOT "fix")
    "docs",         // Documentation
    "style",        // Formatting
    "refactor",     // Code restructuring
    "performance",  // Performance (NOT "perf")
    "test",         // Tests
    "config",       // Configuration changes
    "planning",     // Planning documents
    "revert");      // Revert commit

  // Files that are Claude-facing and should use config:, not docs:
  // Per M255, M306: plugin/, CLAUDE.md, .claude/, client/, skills/, etc.
  private static final List<String> CLAUDE_FACING_PATTERNS = Arrays.asList(
    "CLAUDE.md",
    "plugin/",         // CAT plugin directory (M255, M306)
    ".claude/",        // Claude config directory
    "client/",         // Client scripts
    "skills/",         // Skill definitions
    "concepts/",       // Concept documents
    "commands/");      // Command definitions

  private static final Pattern GIT_COMMIT_PATTERN = Pattern.compile("git\\s+commit");
  private static final Pattern HEREDOC_PATTERN =
    Pattern.compile("-m\\s+\"\\$\\(cat\\s+<<['\"]?EOF['\"]?\\s*\\n(.+?)\\nEOF", Pattern.DOTALL);
  private static final Pattern SIMPLE_MESSAGE_PATTERN = Pattern.compile("-m\\s+[\"']([^\"']+)[\"']");
  private static final Pattern M_FLAG_PATTERN = Pattern.compile("-m\\s+");
  private static final Pattern COMMIT_TYPE_PATTERN = Pattern.compile("^(\\w+)(\\(.+\\))?:");

  /**
   * Creates a new handler for validating commit types.
   */
  public ValidateCommitType()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    // Only check git commit commands
    if (!GIT_COMMIT_PATTERN.matcher(command).find())
      return Result.allow();

    // Extract commit message from -m flag
    String message = extractCommitMessage(command);
    if (message == null)
      return Result.allow();
    if (message.isEmpty())
    {
      return Result.block("""
        **BLOCKED: Could not parse commit message format**

        The commit uses -m flag but the message format is not recognized.
        Claude Code should use the standard HEREDOC format:

        ```
        git commit -m "$(cat <<'EOF'
        type: description
        EOF
        )"
        ```

        If this is a legitimate format that should be supported, update
        validate_commit_type.py to handle it.""");
    }

    // Normalize the message (strip leading whitespace from each line)
    message = normalizeMessage(message);

    // Check for conventional commit format
    Matcher typeMatcher = COMMIT_TYPE_PATTERN.matcher(message);
    if (typeMatcher.find())
    {
      String commitType = GitCommands.toLowerCase(typeMatcher.group(1));
      if (!VALID_COMMIT_TYPES.contains(commitType))
      {
        return Result.block(String.format("""
          **BLOCKED: Invalid commit type '%s'**

          Valid commit types: %s

          Example: feature: add user authentication
                   bugfix: resolve memory leak in parser""",
          commitType, String.join(", ", VALID_COMMIT_TYPES)));
      }

      // A007: Check for docs: used on Claude-facing files
      if (commitType.equals("docs"))
      {
        try
        {
          Result result = checkClaudeFacingFiles();
          if (result != null)
            return result;
        }
        catch (IOException _)
        {
          return Result.warn("Failed to retrieve staged files for Claude-facing file check.");
        }
      }
    }

    return Result.allow();
  }

  /**
   * Extracts the commit message from the command.
   *
   * @param command the git commit command
   * @return the commit message, empty string if -m flag present but unparseable, null if no -m flag
   */
  private String extractCommitMessage(String command)
  {
    // Check HEREDOC first (more specific pattern)
    Matcher heredocMatcher = HEREDOC_PATTERN.matcher(command);
    if (heredocMatcher.find())
      return heredocMatcher.group(1).strip();

    // Pattern 2: Simple -m "message" or -m 'message'
    Matcher simpleMatcher = SIMPLE_MESSAGE_PATTERN.matcher(command);
    if (simpleMatcher.find())
      return simpleMatcher.group(1);

    // -m flag present but couldn't parse - suspicious in Claude Code context
    if (M_FLAG_PATTERN.matcher(command).find())
      return "";

    // No -m flag at all (interactive mode) - unusual for Claude Code
    return null;
  }

  /**
   * Normalizes the commit message by stripping leading whitespace from each line.
   *
   * @param message the raw commit message
   * @return the normalized message
   */
  private String normalizeMessage(String message)
  {
    String[] messageLines = message.split("\n");
    StringBuilder normalizedMessage = new StringBuilder();
    for (int i = 0; i < messageLines.length; ++i)
    {
      if (i > 0)
        normalizedMessage.append('\n');
      normalizedMessage.append(messageLines[i].strip());
    }
    return normalizedMessage.toString();
  }

  /**
   * Checks if staged files include Claude-facing files that shouldn't use docs: commit type.
   *
   * @return a block result if Claude-facing files found, null otherwise
   * @throws IOException if the git command fails
   */
  private Result checkClaudeFacingFiles() throws IOException
  {
    List<String> stagedFiles = GitCommands.getStagedFiles();
    if (stagedFiles.isEmpty())
      return null;
    for (String pattern : CLAUDE_FACING_PATTERNS)
    {
      for (String filePath : stagedFiles)
      {
        if (filePath.contains(pattern))
        {
          return Result.block(String.format("""
            **BLOCKED: 'docs:' used for Claude-facing file**

            Staged file '%s' matches Claude-facing pattern '%s'

            Claude-facing files should use 'config:', not 'docs:':
            - docs: = user-facing (README, API docs)
            - config: = Claude-facing (CLAUDE.md, plugin/, skills/)

            Fix: Change 'docs:' to 'config:' in your commit message

            See CLAUDE.md "Commit Types" section for reference (M255, M306).""",
            filePath, pattern));
        }
      }
    }
    return null;
  }
}

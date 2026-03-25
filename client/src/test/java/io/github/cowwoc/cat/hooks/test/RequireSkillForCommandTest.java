/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.RequireSkillForCommand;
import io.github.cowwoc.cat.hooks.util.GetSkill;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link RequireSkillForCommand}.
 * <p>
 * Each test is fully self-contained, creating its own temporary directories and cleaning them up.
 */
public final class RequireSkillForCommandTest
{
  private static final String REGISTRY_JSON = """
    {
      "guards": [
        {"pattern": "\\\\bgit\\\\s+rebase\\\\b", "skill": "cat:git-rebase-agent"},
        {"pattern": "\\\\brm\\\\s+-[a-zA-Z]*r[a-zA-Z]*", "skill": "cat:safe-rm-agent"}
      ]
    }
    """;

  /**
   * Writes the test registry to {@code tempPluginRoot/config/skill-triggers.json}.
   *
   * @param tempPluginRoot the temporary plugin root directory
   * @throws IOException if writing fails
   */
  private static void writeRegistry(Path tempPluginRoot) throws IOException
  {
    Path configDir = tempPluginRoot.resolve("config");
    Files.createDirectories(configDir);
    Files.writeString(configDir.resolve("skill-triggers.json"), REGISTRY_JSON,
      UTF_8);
  }

  /**
   * Writes the loaded markers for the given session ID under the scope's session base path.
   *
   * @param scope the Claude hook scope providing the session base path
   * @param sessionId the session ID (used as the agent directory name for main agents)
   * @param skills the newline-separated skill names to write
   * @throws IOException if writing fails
   */
  private static void writeSkillsLoaded(ClaudeHook scope, String sessionId, String skills) throws IOException
  {
    Path agentDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize().resolve(sessionId);
    Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
    Files.createDirectories(loadedDir);
    for (String line : skills.split("\n"))
    {
      String trimmed = line.strip();
      if (!trimmed.isEmpty())
      {
        String encodedName = URLEncoder.encode(trimmed, UTF_8);
        Files.writeString(loadedDir.resolve(encodedName), "", UTF_8);
      }
    }
  }

  /**
   * Writes the loaded markers for a subagent under the scope's session base path.
   * <p>
   * Creates marker files in {@code sessionBasePath/{sessionId}/subagents/{nativeAgentId}/loaded/}.
   *
   * @param scope the Claude hook scope providing the session base path
   * @param sessionId the session ID
   * @param nativeAgentId the native (non-composite) agent ID
   * @param skills the newline-separated skill names to write
   * @throws IOException if writing fails
   */
  private static void writeSubagentSkillsLoaded(ClaudeHook scope, String sessionId, String nativeAgentId,
    String skills) throws IOException
  {
    Path agentDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize().
      resolve(sessionId).resolve("subagents").resolve(nativeAgentId);
    Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
    Files.createDirectories(loadedDir);
    for (String line : skills.split("\n"))
    {
      String trimmed = line.strip();
      if (!trimmed.isEmpty())
      {
        String encodedName = URLEncoder.encode(trimmed, UTF_8);
        Files.writeString(loadedDir.resolve(encodedName), "", UTF_8);
      }
    }
  }

  /**
   * Builds a hook payload embedding the command in tool_input.command for the given session ID.
   *
   * @param command the bash command
   * @param sessionId the session ID
   * @return the JSON payload string
   */
  private static String buildPayload(String command, String sessionId)
  {
    String escaped = command.replace("\\", "\\\\").replace("\"", "\\\"");
    return """
      {
        "session_id": "%s",
        "tool_name": "Bash",
        "tool_input": {"command": "%s"}
      }""".formatted(sessionId, escaped);
  }

  /**
   * Builds a hook payload embedding the command and agent_id for subagent simulation.
   *
   * @param command the bash command
   * @param sessionId the session ID
   * @param agentId the agent ID
   * @return the JSON payload string
   */
  private static String buildSubagentPayload(String command, String sessionId, String agentId)
  {
    String escaped = command.replace("\\", "\\\\").replace("\"", "\\\"");
    return """
      {
        "session_id": "%s",
        "agent_id": "%s",
        "tool_name": "Bash",
        "tool_input": {"command": "%s"}
      }""".formatted(sessionId, agentId, escaped);
  }

  /**
   * Verifies that a guarded command is blocked when the required skill has not been loaded.
   * <p>
   * The {@code skills-loaded} marker file is absent; the handler must block the command and include the
   * skill name in the reason.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void guardedCommandBlockedWhenSkillNotLoaded() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      // No skills-loaded marker written — skill is not loaded
      String payload = buildPayload("git rebase origin/main", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("BLOCKED");
        requireThat(result.reason(), "reason").contains("cat:git-rebase-agent");
        requireThat(result.reason(), "reason").contains("/cat:");
        requireThat(result.reason(), "reason").contains("Then retry");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that a guarded command is allowed when the required skill has been loaded.
   * <p>
   * The {@code skills-loaded} marker file contains the required skill name; the handler must allow the command.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void guardedCommandAllowedWhenSkillLoaded() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      String payload = buildPayload("git rebase origin/main", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        writeSkillsLoaded(scope, "test-session-id", "cat:git-rebase-agent\n");
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that unguarded commands (matching no registry pattern) are always allowed.
   * <p>
   * A command like {@code git status} must pass through without any skill check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void unguardedCommandAlwaysAllowed() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      // No skills-loaded marker — but command doesn't match any guard
      String payload = buildPayload("git status", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that a guarded command run by a subagent is blocked when the required skill is not loaded.
   * <p>
   * Uses a non-empty native agent ID to simulate a subagent context. The handler must check the per-subagent
   * marker file and block when the skill is absent.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void subagentCommandBlockedWhenSkillNotLoaded() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      // No subagent skills-loaded marker written
      String payload = buildSubagentPayload("git rebase origin/v2.1", "test-session-id", "subagent-abc123");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("BLOCKED");
        requireThat(result.reason(), "reason").contains("cat:git-rebase-agent");
        requireThat(result.reason(), "reason").contains("/cat:");
        requireThat(result.reason(), "reason").contains("Then retry");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that a guarded command run by a subagent is allowed when the required skill has been loaded.
   * <p>
   * Uses a non-empty native agent ID to simulate a subagent context. The handler must check the per-subagent
   * marker file and allow when the skill is present.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void subagentCommandAllowedWhenSkillLoaded() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      String payload = buildSubagentPayload("git rebase origin/v2.1", "test-session-id", "subagent-abc123");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        writeSubagentSkillsLoaded(scope, "test-session-id", "subagent-abc123", "cat:git-rebase-agent\n");
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
        requireThat(result.reason(), "reason").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that a guarded command is blocked when the {@code skills-loaded} marker file is empty.
   * <p>
   * An empty marker file contains no skill names; the handler must treat it the same as a missing file
   * and block the command.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void emptyMarkerFileBlocksCommand() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      String payload = buildPayload("git rebase origin/main", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        writeSkillsLoaded(scope, "test-session-id", "");
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("cat:git-rebase-agent");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that a guarded command is blocked when the {@code skills-loaded} marker file contains only
   * whitespace.
   * <p>
   * A whitespace-only marker file yields no valid skill names after stripping; the handler must block.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void whitespaceOnlyMarkerFileBlocksCommand() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      String payload = buildPayload("git rebase origin/main", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        writeSkillsLoaded(scope, "test-session-id", "   \n  \n\t\n");
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("cat:git-rebase-agent");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }

  /**
   * Verifies that an empty command string is always allowed without any skill check.
   * <p>
   * The handler must return an allow result immediately for blank or empty commands.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void emptyCommandAlwaysAllowed() throws IOException
  {
    Path tempPluginRoot = Files.createTempDirectory("plugin-");
    Path tempProjectDir = Files.createTempDirectory("project-");
    try
    {
      writeRegistry(tempPluginRoot);
      String payload = buildPayload("", "test-session-id");
      try (TestClaudeHook scope = new TestClaudeHook(payload, tempProjectDir, tempPluginRoot, tempProjectDir))
      {
        RequireSkillForCommand handler = new RequireSkillForCommand(scope);

        BashHandler.Result result = handler.check(scope);

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempPluginRoot);
      TestUtils.deleteDirectoryRecursively(tempProjectDir);
    }
  }
}

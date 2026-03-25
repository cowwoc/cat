/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeHook;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Persists Claude environment variables into CLAUDE_ENV_FILE for Bash tool invocations.
 * <p>
 * Appends {@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_PLUGIN_ROOT}, and
 * {@code CLAUDE_SESSION_ID} to the env file so they are available in all subsequent
 * Bash tool calls.
 * <p>
 * Writes for new sessions (source="startup"), cleared sessions (source="clear"), and resumed sessions
 * (source="resume"). On compacted (source="compact") sessions, the env file already has the correct content.
 */
public final class InjectEnv implements SessionStartHandler
{
  private final ClaudeHook scope;
  private final Path envFile;

  /**
   * Creates a new InjectEnv handler.
   *
   * @param scope the hook scope
   * @param envFile the path to the env file
   * @throws NullPointerException if {@code scope} or {@code envFile} are null
   */
  public InjectEnv(ClaudeHook scope, Path envFile)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(envFile, "envFile").isNotNull();
    this.scope = scope;
    this.envFile = envFile;
  }

  /**
   * Writes environment variables to CLAUDE_ENV_FILE.
   * <p>
   * Writes for new sessions (source="startup"), cleared sessions (source="clear"), and resumed sessions
   * (source="resume"). On compacted (source="compact") sessions, the env file already has the correct content
   * and re-appending would cause duplicates.
   * <p>
   * For source="resume", writes directly to the resumed session's env directory (identified by session_id from
   * stdin JSON) using TRUNCATE_EXISTING to overwrite any previously written content.
   *
   * @return a result with a warning if a symlink was skipped, otherwise empty
   * @throws AssertionError if required environment variables are not set (CLAUDE_PROJECT_DIR,
   *   CLAUDE_PLUGIN_ROOT) or if session_id is not found in hook input
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters, or if
   *   {@code source} is not one of "startup", "clear", "resume", or "compact"
   * @throws WrappedCheckedException if writing to the env file fails
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    if (Files.isSymbolicLink(envFile))
      return Result.context("InjectEnv: CLAUDE_ENV_FILE is a symlink - skipping for security");

    // CLAUDE_SESSION_ID is empty in the hook environment. Read from stdin JSON instead.
    String sessionId = scope.getSessionId();
    requireThat(sessionId, "session_id").isNotEmpty();

    // Only write for new sessions (startup), cleared sessions (clear), or resumed sessions (resume).
    // Skip for compacted (compact) sessions — the env file is already correct after compaction.
    String source = scope.getString("source");
    switch (source)
    {
      case "resume" ->
      {
        return handleResume(sessionId);
      }
      case "startup", "clear" ->
      {
        return handleStartup(sessionId);
      }
      case "compact" ->
      {
        return Result.empty();
      }
      default -> throw new IllegalArgumentException("Unexpected source value: \"" + source + "\"");
    }
  }

  /**
   * Handles source="resume" by writing env vars directly to the resumed session's directory.
   * <p>
   * Writes directly to {@code sessionEnvBase/sessionId} unconditionally — both when the upstream
   * bug (https://github.com/anthropics/claude-code/issues/24775) is active (dirs differ) and when it
   * is fixed (dirs are the same). The write is still required after the fix because no source="startup"
   * event fires for resumed sessions.
   *
   * @param sessionId the session ID from stdin JSON
   * @return a result with a warning if the env file is a symlink, otherwise empty
   * @throws WrappedCheckedException if writing to the env file fails
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters
   */
  private Result handleResume(String sessionId)
  {
    String projectPath = scope.getProjectPath().toString();
    String pluginRoot = scope.getPluginRoot().toString();
    validateEnvValue(projectPath, "CLAUDE_PROJECT_DIR");
    validateEnvValue(pluginRoot, "CLAUDE_PLUGIN_ROOT");
    validateEnvValue(sessionId, "CLAUDE_SESSION_ID");
    String content = buildEnvContent(projectPath, pluginRoot, sessionId);
    try
    {
      Path sessionEnvBase = envFile.getParent().getParent();
      Path resumedSessionDir = sessionEnvBase.resolve(sessionId);
      Files.createDirectories(resumedSessionDir);
      String warning = writeEnvFileToDir(resumedSessionDir, envFile.getFileName(), content,
        "InjectEnv: resumed session env file is a symlink - skipping for security", true);
      if (!warning.isEmpty())
        return Result.context(warning);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return Result.empty();
  }

  /**
   * Handles source="startup" and source="clear" by writing env vars to CLAUDE_ENV_FILE's directory.
   * <p>
   * For source="clear", CLAUDE_ENV_FILE already points to the new session's correct directory, so the
   * write is identical to source="startup".
   *
   * @param sessionId the session ID from stdin JSON
   * @return an empty result
   * @throws WrappedCheckedException if writing to the env file fails
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters
   */
  private Result handleStartup(String sessionId)
  {
    String projectPath = scope.getProjectPath().toString();
    String pluginRoot = scope.getPluginRoot().toString();
    validateEnvValue(projectPath, "CLAUDE_PROJECT_DIR");
    validateEnvValue(pluginRoot, "CLAUDE_PLUGIN_ROOT");
    validateEnvValue(sessionId, "CLAUDE_SESSION_ID");
    String content = buildEnvContent(projectPath, pluginRoot, sessionId);
    try
    {
      Files.createDirectories(envFile.getParent());
      writeEnvFileToDir(envFile.getParent(), envFile.getFileName(), content, "", false);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return Result.empty();
  }

  /**
   * Builds the env file content string for the three CAT environment variables.
   *
   * @param projectPath the value for CLAUDE_PROJECT_DIR
   * @param pluginRoot  the value for CLAUDE_PLUGIN_ROOT
   * @param sessionId   the value for CLAUDE_SESSION_ID
   * @return the export statements to write
   */
  private static String buildEnvContent(String projectPath, String pluginRoot, String sessionId)
  {
    return "export CLAUDE_PROJECT_DIR=\"" + projectPath + "\"\n" +
      "export CLAUDE_PLUGIN_ROOT=\"" + pluginRoot + "\"\n" +
      "export CLAUDE_SESSION_ID=\"" + sessionId + "\"\n";
  }

  /**
   * Validates that an environment variable value does not contain dangerous shell characters.
   *
   * @param value the value to validate
   * @param variableName the name of the variable (used in the error message)
   * @throws IllegalArgumentException if {@code value} contains {@code "}, {@code $}, a backtick, or a newline
   */
  private static void validateEnvValue(String value, String variableName)
  {
    for (int i = 0; i < value.length(); ++i)
    {
      char c = value.charAt(i);
      if (c == '"' || c == '$' || c == '`' || c == '\n')
      {
        throw new IllegalArgumentException(variableName + " contains a dangerous shell character '" + c +
          "' at index " + i + ": " + value);
      }
    }
  }

  /**
   * Writes the env content to a single session directory, skipping symlinks.
   * <p>
   * The target directory must already exist before calling this method.
   *
   * @param targetDir        the directory to write the env file into
   * @param envFileName      the filename of the env file (e.g. {@code sessionstart-hook-N.sh})
   * @param content          the export statements to write
   * @param warningIfSymlink the warning message to return if the env file is a symlink; pass empty string
   *                         if no warning should be returned in that case
   * @param overwrite        if {@code true}, truncates the file before writing (TRUNCATE_EXISTING);
   *                         if {@code false}, appends to the file (APPEND)
   * @return {@code warningIfSymlink} if the env file is a symlink, otherwise empty string
   * @throws IOException if writing fails
   */
  private String writeEnvFileToDir(Path targetDir, Path envFileName, String content, String warningIfSymlink,
    boolean overwrite)
    throws IOException
  {
    Path targetFile = targetDir.resolve(envFileName);
    if (Files.isSymbolicLink(targetFile))
      return warningIfSymlink;
    StandardOpenOption writeMode;
    if (overwrite)
      writeMode = StandardOpenOption.TRUNCATE_EXISTING;
    else
      writeMode = StandardOpenOption.APPEND;
    Files.writeString(targetFile, content, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, writeMode);
    return "";
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * Persists CAT environment variables into CLAUDE_ENV_FILE for Bash tool invocations.
 * <p>
 * Appends the engine-neutral {@code CAT_*} aliases that skills need directly to the env file so they
 * are available in all subsequent Bash tool calls.
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
   * <p>
   * The env file path is obtained from the constructor parameter.
   *
   * @return a result with a warning if a symlink was skipped, otherwise empty
   * @throws AssertionError if required environment variables are not set or if session_id is not found in
   *   hook input
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters, or if
   *   {@code source} is not one of "startup", "clear", "resume", or "compact"
   * @throws WrappedCheckedException if writing to the env file fails
   */
  @Override
  public Result handle()
  {
    if (Files.isSymbolicLink(envFile))
      return Result.context("InjectEnv: CLAUDE_ENV_FILE is a symlink - skipping for security");

    // The hook environment does not provide a reliable session ID. Read from stdin JSON instead.
    String sessionId = scope.getSessionId();
    requireThat(sessionId, "session_id").isNotEmpty();

    // Only write for new sessions (startup), cleared sessions (clear), or resumed sessions (resume).
    // Skip for compacted (compact) sessions — the env file is already correct after compaction.
    String source = scope.getStringInput("source");
    switch (source)
    {
      case "resume" ->
      {
        return handleResume(envFile, sessionId);
      }
      case "startup", "clear" ->
      {
        return handleStartup(envFile, sessionId);
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
   * @param envPath   the CLAUDE_ENV_FILE path
   * @param sessionId the session ID from stdin JSON
   * @return a result with a warning if the env file is a symlink, otherwise empty
   * @throws WrappedCheckedException if writing to the env file fails
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters
   */
  private Result handleResume(Path envPath, String sessionId)
  {
    String envContent = buildEnvContent(sessionId);
    try
    {
      Path sessionEnvBase = envPath.getParent().getParent();
      Path resumedSessionDir = sessionEnvBase.resolve(sessionId);
      Files.createDirectories(resumedSessionDir);
      String warning = writeEnvFileToDir(resumedSessionDir, envPath.getFileName(), envContent,
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
   * @param envPath   the CLAUDE_ENV_FILE path
   * @param sessionId the session ID from stdin JSON
   * @return an empty result
   * @throws WrappedCheckedException if writing to the env file fails
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters
   */
  private Result handleStartup(Path envPath, String sessionId)
  {
    String envContent = buildEnvContent(sessionId);
    try
    {
      Files.createDirectories(envPath.getParent());
      writeEnvFileToDir(envPath.getParent(), envPath.getFileName(), envContent, "", false);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return Result.empty();
  }

  /**
   * Builds the env file content string for LLM-facing CAT aliases.
   *
   * @param sessionId the value for CAT_SESSION_ID
   * @return the export statements to write
   */
  private String buildEnvContent(String sessionId)
  {
    String projectPath = scope.getProjectPath().toString();
    String pluginRoot = scope.getPluginRoot().toString();
    String pluginData = scope.getPluginData().toString();
    String configPath = scope.getClaudeConfigPath().toString();
    validateEnvValue(projectPath, "CAT_PROJECT_DIR");
    validateEnvValue(pluginRoot, "CAT_PLUGIN_ROOT");
    validateEnvValue(pluginData, "CAT_PLUGIN_DATA");
    validateEnvValue(configPath, "CAT_CONFIG_DIR");
    validateEnvValue(sessionId, "CAT_SESSION_ID");
    return "export CAT_PROJECT_DIR=\"" + projectPath + "\"\n" +
      "export CAT_PLUGIN_ROOT=\"" + pluginRoot + "\"\n" +
      "export CAT_PLUGIN_DATA=\"" + pluginData + "\"\n" +
      "export CAT_CONFIG_DIR=\"" + configPath + "\"\n" +
      "export CAT_ENGINE=\"claude\"\n" +
      "export CAT_SESSION_ID=\"" + sessionId + "\"\n";
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
   * @param envFileName      the filename of the env file (e.g. {@code session-start-hook-N.sh})
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
    Path envFile = targetDir.resolve(envFileName);
    StandardOpenOption writeMode;
    if (overwrite)
      writeMode = StandardOpenOption.TRUNCATE_EXISTING;
    else
      writeMode = StandardOpenOption.APPEND;
    Set<OpenOption> options = Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, writeMode,
      LinkOption.NOFOLLOW_LINKS);
    try (SeekableByteChannel channel = Files.newByteChannel(envFile, options))
    {
      channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
    }
    catch (IOException e)
    {
      if (!warningIfSymlink.isEmpty() && Files.isSymbolicLink(envFile))
        return warningIfSymlink;
      throw e;
    }
    return "";
  }
}

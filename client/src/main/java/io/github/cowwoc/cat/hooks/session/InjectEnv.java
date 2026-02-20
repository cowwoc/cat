/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
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
 * Workaround for <a href="https://github.com/anthropics/claude-code/issues/24775">#24775</a>:
 * On resumed sessions, CLAUDE_ENV_FILE points to a startup session directory, but the env
 * loader reads from the resumed session directory. To fix this, we also write the env file
 * to the resumed session's directory using the session_id from stdin JSON.
 */
public final class InjectEnv implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new InjectEnv handler.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectEnv(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Writes environment variables to CLAUDE_ENV_FILE and the resumed session's env directory.
   *
   * @param input the hook input
   * @return a result with warnings if symlinks were skipped, otherwise empty
   * @throws NullPointerException if input is null
   * @throws AssertionError if required environment variables are not set
   * @throws WrappedCheckedException if writing to the env file fails
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    Path envPath = scope.getClaudeEnvFile();
    if (Files.isSymbolicLink(envPath))
      return Result.context("InjectEnv: CLAUDE_ENV_FILE is a symlink - skipping for security");

    // CLAUDE_SESSION_ID is empty in the hook environment. Read from stdin JSON instead.
    String sessionId = input.getSessionId();
    if (sessionId.isEmpty())
      throw new AssertionError("session_id not found in hook input");

    String projectDir = scope.getClaudeProjectDir().toString();
    String pluginRoot = scope.getClaudePluginRoot().toString();
    String content = "export CLAUDE_PROJECT_DIR=\"" + projectDir + "\"\n" +
      "export CLAUDE_PLUGIN_ROOT=\"" + pluginRoot + "\"\n" +
      "export CLAUDE_SESSION_ID=\"" + sessionId + "\"\n";

    try
    {
      writeEnvFile(envPath, content);
      String warning = writeToResumedSessionDir(envPath, sessionId, content);
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
   * Writes the env content to the provided CLAUDE_ENV_FILE path.
   *
   * @param envPath the env file path
   * @param content the export statements to write
   * @throws IOException if writing fails
   */
  private void writeEnvFile(Path envPath, String content) throws IOException
  {
    Files.createDirectories(envPath.getParent());
    Files.writeString(envPath, content, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  /**
   * Writes the env content to the resumed session's env directory.
   * <p>
   * Workaround for https://github.com/anthropics/claude-code/issues/24775:
   * CLAUDE_ENV_FILE may point to a startup session directory that differs from the resumed
   * session's directory. The env loader reads from the resumed session's sibling directory,
   * so we write there too.
   *
   * @param envPath the CLAUDE_ENV_FILE path (used to derive the session-env base directory)
   * @param sessionId the session ID from stdin JSON
   * @param content the export statements to write
   * @return warning message if symlink was skipped, empty string otherwise
   * @throws IOException if writing fails
   */
  private String writeToResumedSessionDir(Path envPath, String sessionId, String content)
    throws IOException
  {
    // CLAUDE_ENV_FILE path structure: .../STARTUP_ID/sessionstart-hook-N.sh
    // Go up two levels to find the parent of all session directories.
    Path sessionEnvBase = envPath.getParent().getParent();
    Path resumedSessionDir = sessionEnvBase.resolve(sessionId);

    // Only write if this is a different directory than what CLAUDE_ENV_FILE already points to
    if (resumedSessionDir.equals(envPath.getParent()))
      return "";

    Path resumedEnvFile = resumedSessionDir.resolve(envPath.getFileName());
    if (Files.isSymbolicLink(resumedEnvFile))
      return "InjectEnv: resumed session env file is a symlink - skipping for security";
    Files.createDirectories(resumedSessionDir);
    Files.writeString(resumedEnvFile, content, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    return "";
  }
}

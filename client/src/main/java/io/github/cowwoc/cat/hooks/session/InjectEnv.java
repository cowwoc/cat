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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

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
  private static final Pattern SESSION_ID_PATTERN =
    Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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
   * Writes environment variables to CLAUDE_ENV_FILE, the resumed session's env directory, and all sibling session
   * directories.
   *
   * @param input the hook input
   * @return a result with warnings if symlinks were skipped, otherwise empty
   * @throws NullPointerException if {@code input} is null
   * @throws AssertionError if required environment variables are not set
   * @throws IllegalArgumentException if any environment value contains dangerous shell characters
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
    validateEnvValue(projectDir, "CLAUDE_PROJECT_DIR");
    validateEnvValue(pluginRoot, "CLAUDE_PLUGIN_ROOT");
    validateEnvValue(sessionId, "CLAUDE_SESSION_ID");
    String content = "export CLAUDE_PROJECT_DIR=\"" + projectDir + "\"\n" +
      "export CLAUDE_PLUGIN_ROOT=\"" + pluginRoot + "\"\n" +
      "export CLAUDE_SESSION_ID=\"" + sessionId + "\"\n";

    try
    {
      Files.createDirectories(envPath.getParent());
      writeEnvFileToDir(envPath.getParent(), envPath.getFileName(), content, "");
      Path sessionEnvBase = envPath.getParent().getParent();
      String resumedWarning = writeToResumedSessionDir(sessionEnvBase, envPath, sessionId, content);
      List<String> allWarnings = writeToAllSessionDirs(sessionEnvBase, envPath, content);
      String combinedWarnings = aggregateWarnings(resumedWarning, allWarnings);
      if (!combinedWarnings.isEmpty())
        return Result.context(combinedWarnings);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return Result.empty();
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
   * Combines a single warning string and a list of additional warnings into one newline-delimited string.
   *
   * @param resumedWarning a warning from the resumed-session write, or empty string
   * @param allWarnings additional warnings from sibling-directory writes
   * @return a single newline-delimited string of all non-empty warnings, or empty string if none
   */
  private static String aggregateWarnings(String resumedWarning, List<String> allWarnings)
  {
    StringJoiner warnings = new StringJoiner("\n");
    if (!resumedWarning.isEmpty())
      warnings.add(resumedWarning);
    for (String warning : allWarnings)
      warnings.add(warning);
    return warnings.toString();
  }

  /**
   * Writes the env content to a single session directory, skipping symlinks.
   * <p>
   * The target directory must already exist before calling this method.
   *
   * @param targetDir       the directory to write the env file into
   * @param envFileName     the filename of the env file (e.g. {@code sessionstart-hook-N.sh})
   * @param content         the export statements to write
   * @param warningIfSymlink the warning message to return if the env file is a symlink; pass empty string
   *                        if no warning should be returned in that case
   * @return {@code warningIfSymlink} if the env file is a symlink, otherwise empty string
   * @throws IOException if writing fails
   */
  private String writeEnvFileToDir(Path targetDir, Path envFileName, String content, String warningIfSymlink)
    throws IOException
  {
    Path envFile = targetDir.resolve(envFileName);
    if (Files.isSymbolicLink(envFile))
      return warningIfSymlink;
    Files.writeString(envFile, content, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    return "";
  }

  /**
   * Writes the env content to the resumed session's env directory.
   * <p>
   * Workaround for https://github.com/anthropics/claude-code/issues/24775:
   * CLAUDE_ENV_FILE may point to a startup session directory that differs from the resumed
   * session's directory. The env loader reads from the resumed session's sibling directory,
   * so we write there too.
   *
   * @param sessionEnvBase the parent directory containing all session-env subdirectories
   * @param envPath the CLAUDE_ENV_FILE path (used to identify the already-written directory)
   * @param sessionId the session ID from stdin JSON
   * @param content the export statements to write
   * @return warning message if symlink was skipped, empty string otherwise
   * @throws IOException if writing fails
   */
  private String writeToResumedSessionDir(Path sessionEnvBase, Path envPath, String sessionId, String content)
    throws IOException
  {
    Path resumedSessionDir = sessionEnvBase.resolve(sessionId);

    // Only write if this is a different directory than what CLAUDE_ENV_FILE already points to
    if (resumedSessionDir.equals(envPath.getParent()))
      return "";

    Files.createDirectories(resumedSessionDir);
    return writeEnvFileToDir(resumedSessionDir, envPath.getFileName(), content,
      "InjectEnv: resumed session env file is a symlink - skipping for security");
  }

  // WORKAROUND: https://github.com/anthropics/claude-code/issues/14433
  /**
   * Writes the env content to all existing session-env sibling directories.
   * <p>
   * After {@code /clear}, Claude Code's session env loader may cache files from an old session directory and not
   * re-read from the new session directory. Writing to all sibling directories ensures that whichever directory the
   * cache reads from, it gets the current {@code CLAUDE_SESSION_ID}.
   *
   * @param sessionEnvBase the parent directory containing all session-env subdirectories
   * @param envPath the CLAUDE_ENV_FILE path (used to identify the already-written directory)
   * @param content the export statements to write
   * @return a list of warning messages for any symlinks that were skipped, empty if none
   * @throws IOException if reading the directory listing or writing fails
   */
  private List<String> writeToAllSessionDirs(Path sessionEnvBase, Path envPath, String content)
    throws IOException
  {
    List<String> warnings = new ArrayList<>();
    if (!Files.isDirectory(sessionEnvBase))
      return warnings;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionEnvBase))
    {
      for (Path siblingDir : stream)
      {
        if (!Files.isDirectory(siblingDir) || Files.isSymbolicLink(siblingDir))
          continue;
        // Skip directories whose name does not look like a session ID
        if (!SESSION_ID_PATTERN.matcher(siblingDir.getFileName().toString()).matches())
          continue;
        // Skip the directory that CLAUDE_ENV_FILE already points to (written by writeEnvFile)
        if (siblingDir.equals(envPath.getParent()))
          continue;
        String warning = writeEnvFileToDir(siblingDir, envPath.getFileName(), content,
          "InjectEnv: sibling session env file is a symlink - skipping for security: " +
            siblingDir.resolve(envPath.getFileName()));
        if (!warning.isEmpty())
          warnings.add(warning);
      }
    }
    return warnings;
  }
}

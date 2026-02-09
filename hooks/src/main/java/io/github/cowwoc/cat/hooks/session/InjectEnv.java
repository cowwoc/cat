package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
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
 */
public final class InjectEnv implements SessionStartHandler
{
  /**
   * Creates a new InjectEnv handler.
   */
  public InjectEnv()
  {
  }

  /**
   * Writes environment variables to CLAUDE_ENV_FILE.
   *
   * @param input the hook input
   * @return an empty result (silent operation)
   * @throws NullPointerException if input is null
   * @throws WrappedCheckedException if writing to the env file fails
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    String envFile = System.getenv("CLAUDE_ENV_FILE");
    if (envFile == null || envFile.isEmpty())
      throw new AssertionError("CLAUDE_ENV_FILE is not set");

    Path envPath = Path.of(envFile);
    if (Files.isSymbolicLink(envPath))
    {
      System.err.println("InjectEnv: CLAUDE_ENV_FILE is a symlink - skipping for security");
      return Result.empty();
    }

    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");

    String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (pluginRoot == null || pluginRoot.isEmpty())
      throw new AssertionError("CLAUDE_PLUGIN_ROOT is not set");

    String sessionId = System.getenv("CLAUDE_SESSION_ID");
    if (sessionId == null || sessionId.isEmpty())
      throw new AssertionError("CLAUDE_SESSION_ID is not set");

    String content = "export CLAUDE_PROJECT_DIR=\"" + projectDir + "\"\n" +
      "export CLAUDE_PLUGIN_ROOT=\"" + pluginRoot + "\"\n" +
      "export CLAUDE_SESSION_ID=\"" + sessionId + "\"\n";

    try
    {
      Files.writeString(Path.of(envFile), content, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return Result.empty();
  }
}

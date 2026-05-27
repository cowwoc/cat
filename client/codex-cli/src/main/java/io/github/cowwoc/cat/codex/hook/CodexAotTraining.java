/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Exercises Codex hook entrypoint paths during AOT training.
 */
public final class CodexAotTraining
{
  private static final byte[] PAYLOAD = """
    {"session_id":"aot-training-session","agent_id":"aot-training-agent"}
    """.getBytes(StandardCharsets.UTF_8);

  /**
   * Prevents construction.
   */
  private CodexAotTraining()
  {
  }

  /**
   * Runs Codex AOT training from the command line.
   *
   * @param args command line arguments (unused)
   * @throws Exception if training fails
   */
  public static void main(String[] args) throws Exception
  {
    System.exit(run());
  }

  /**
   * Runs Codex AOT training.
   *
   * @return 0 on success
   * @throws Exception if training fails
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static int run() throws Exception
  {
    runEntrypoints();
    return 0;
  }

  /**
   * Exercises Codex hook entrypoints that are included in the engine image.
   *
   * @throws IOException if the temporary SessionStart fixture cannot be created or deleted
   */
  private static void runEntrypoints() throws IOException
  {
    String[] args = {};
    try (PrintStream out = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8))
    {
      PreBashHook.run(args, input(), out);
    }
    runSessionStart();
    runSubagentStart();
  }

  /**
   * Returns a fresh input stream for each hook entrypoint invocation.
   *
   * @return the training payload
   */
  private static InputStream input()
  {
    return new ByteArrayInputStream(PAYLOAD);
  }

  /**
   * Exercises the Codex SessionStart entrypoint against an isolated fixture.
   *
   * @throws IOException if the temporary fixture cannot be created or deleted
   */
  private static void runSessionStart() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-codex-aot-");
    try
    {
      Path projectRoot = tempDir.resolve("project");
      Path pluginRoot = tempDir.resolve("plugin");
      Path pluginData = tempDir.resolve("plugin-data");
      Files.createDirectories(projectRoot.resolve(".cat/rules/common"));
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
      Files.createDirectories(pluginRoot.resolve("rules/common"));
      Files.createDirectories(pluginRoot.resolve("rules/codex"));
      Files.createDirectories(pluginData);
      Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
        StandardCharsets.UTF_8);
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", pluginRoot.toString(),
        "CAT_PLUGIN_DATA", pluginData.toString(),
        "TZ", "UTC");
      String nativeInput = "{\"cwd\":\"" + projectRoot.toString().replace("\\", "\\\\") + "\"}";
      SessionStartHook hook = new SessionStartHook();
      try (CodexHookScope scope = hook.createScope(
        new ByteArrayInputStream(nativeInput.getBytes(StandardCharsets.UTF_8)), environment,
        Path.of(System.getProperty("user.dir"))))
      {
        hook.run(scope);
      }
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Exercises the Codex SubagentStart entrypoint against an isolated fixture.
   *
   * @throws IOException if the temporary fixture cannot be created or deleted
   */
  private static void runSubagentStart() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-codex-aot-");
    try
    {
      Path projectRoot = tempDir.resolve("project");
      Path pluginRoot = tempDir.resolve("plugin");
      Path pluginData = tempDir.resolve("plugin-data");
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
      Files.createDirectories(pluginRoot.resolve("rules/codex"));
      Files.createDirectories(pluginData);
      Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
        StandardCharsets.UTF_8);
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", pluginRoot.toString(),
        "CAT_PLUGIN_DATA", pluginData.toString(),
        "TZ", "UTC");
      String nativeInput = "{\"cwd\":\"" + projectRoot.toString().replace("\\", "\\\\") +
        "\",\"hook_event_name\":\"SubagentStart\",\"agent_type\":\"cat:work-execute\"}";
      SubagentStartHook hook = new SubagentStartHook();
      try (CodexHookScope scope = hook.createScope(
        new ByteArrayInputStream(nativeInput.getBytes(StandardCharsets.UTF_8)), environment,
        Path.of(System.getProperty("user.dir"))))
      {
        hook.run(scope);
      }
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Deletes a directory tree if it exists.
   *
   * @param directory the directory to delete
   * @throws IOException if deletion fails
   */
  private static void deleteRecursively(Path directory) throws IOException
  {
    if (!Files.exists(directory))
      return;
    try (Stream<Path> paths = Files.walk(directory))
    {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
        Files.delete(path);
    }
  }
}

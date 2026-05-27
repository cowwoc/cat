/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.client.test.TestUtils;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Verifies Codex hooks execute through Codex/common code paths rather than Claude implementation classes.
 */
public final class CodexHookEngineIsolationTest
{
  /**
   * Verifies that the Codex pre-bash hook can process a native Codex payload even when Claude
   * implementation classes are unavailable.
   *
   * @throws Exception if the reflective hook invocation fails
   */
  @Test
  public void preBashRunsWithoutClaudeImplementation() throws Exception
  {
    Path tempDir = Files.createTempDirectory("codex-hook-engine-isolation-");
    try
    {
      String output = invokeHook("io.github.cowwoc.cat.codex.hook.PreBashHook", """
        {
          "cwd": "%s",
          "thread_id": "thread-1",
          "tool_name": "functions.exec_command",
          "arguments": {
            "cmd": "git config user.email codex@example.com"
          }
        }
        """.formatted(tempDir.toString().replace("\\", "\\\\")));
      requireThat(output, "stdout").contains("\"decision\":\"block\"");
      requireThat(output, "stdout").contains("git config user.email");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Codex SessionStart hook can process native Codex input even when Claude
   * implementation classes are unavailable.
   *
   * @throws Exception if the reflective hook invocation fails
   */
  @Test
  public void sessionStartRunsWithoutClaude() throws Exception
  {
    Path tempDir = Files.createTempDirectory("codex-hook-engine-isolation-");
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
      Files.writeString(projectRoot.resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);

      String output = invokeSessionStartHook("""
        {
          "cwd": "%s",
          "hook_event_name": "SessionStart"
        }
        """.formatted(projectRoot.toString().replace("\\", "\\\\")), Map.of(
        "CAT_PLUGIN_ROOT", pluginRoot.toString(),
        "CAT_PLUGIN_DATA", pluginData.toString(),
        "TZ", "UTC"));

      requireThat(output, "stdout").contains("\"hookSpecificOutput\"");
      requireThat(output, "stdout").contains("Java Common");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Invokes a Codex hook through a class loader that rejects Claude implementation classes.
   *
   * @param hookClassName the fully qualified hook class name
   * @param payload the native Codex hook payload
   * @return the hook standard output
   * @throws Exception if hook loading or invocation fails
   */
  private static String invokeHook(String hookClassName, String payload) throws Exception
  {
    ByteArrayInputStream input = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    try (URLClassLoader loader = new RejectClaudeClassLoader(getEngineClasspath());
         PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8))
    {
      Class<?> hook = Class.forName(hookClassName, true, loader);
      Method run = hook.getMethod("run", String[].class, InputStream.class, PrintStream.class);
      run.invoke(null, new String[0], input, out);
    }
    catch (InvocationTargetException e)
    {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception)
        throw exception;
      if (cause instanceof Error error)
        throw error;
      throw e;
    }
    return stdout.toString(StandardCharsets.UTF_8);
  }

  /**
   * Invokes Codex SessionStart through a class loader that rejects Claude implementation classes.
   *
   * @param payload the native Codex hook payload
   * @param environment the environment values visible to the hook
   * @return the hook output
   * @throws Exception if hook loading or invocation fails
   */
  private static String invokeSessionStartHook(String payload, Map<String, String> environment)
    throws Exception
  {
    ByteArrayInputStream input = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
    try (URLClassLoader loader = new RejectClaudeClassLoader(getEngineClasspath()))
    {
      Class<?> hook = Class.forName("io.github.cowwoc.cat.codex.hook.SessionStartHook", true, loader);
      Class<?> scopeType = Class.forName("io.github.cowwoc.cat.codex.hook.CodexHookScope", true,
        loader);
      Object hookInstance = hook.getConstructor().newInstance();
      Method createScope = hook.getMethod("createScope", InputStream.class, Map.class, Path.class);
      Method run = hook.getMethod("run", scopeType);
      Object scope = createScope.invoke(hookInstance, input, environment,
        Path.of(System.getProperty("user.dir")));
      try
      {
        Object result = run.invoke(hookInstance, scope);
        return (String) result.getClass().getMethod("output").invoke(result);
      }
      finally
      {
        scopeType.getMethod("close").invoke(scope);
      }
    }
    catch (InvocationTargetException e)
    {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception)
        throw exception;
      if (cause instanceof Error error)
        throw error;
      throw e;
    }
  }

  /**
   * Builds the classpath used by the isolation class loader.
   *
   * @return the current engine classpath as URLs
   */
  private static URL[] getEngineClasspath()
  {
    Path clientDir = Path.of(System.getProperty("user.dir")).getParent();
    List<Path> paths = new ArrayList<>(Arrays.stream(System.getProperty("java.class.path").
        split(System.getProperty("path.separator"))).
      map(Path::of).
      map(path -> path.toAbsolutePath().normalize()).
      toList());
    paths.add(clientDir.resolve("common-cli/target/classes").toAbsolutePath().normalize());
    paths.add(clientDir.resolve("codex-cli/target/classes").toAbsolutePath().normalize());
    return paths.stream().
      distinct().
      map(Path::toUri).
      map(uri ->
      {
        try
        {
          return uri.toURL();
        }
        catch (Exception e)
        {
          throw new IllegalStateException("Invalid classpath entry: " + uri, e);
        }
      }).
      toArray(URL[]::new);
  }

  private static final class RejectClaudeClassLoader extends URLClassLoader
  {
    /**
     * Creates a class loader that blocks Claude implementation classes.
     *
     * @param urls the classpath URLs
     */
    private RejectClaudeClassLoader(URL[] urls)
    {
      super(urls, getPlatformClassLoader());
    }

    /**
     * Loads classes while rejecting Claude implementation packages.
     *
     * @param name the binary class name
     * @param resolve whether to resolve the class
     * @return the loaded class
     * @throws ClassNotFoundException if the class is blocked or cannot be loaded
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
      if (name.startsWith("io.github.cowwoc.cat.claude."))
        throw new ClassNotFoundException(name);
      return super.loadClass(name, resolve);
    }
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verifies Claude runtime path configuration uses the bundled plugin-root runtime.
 */
public final class ClaudeRuntimePathConfigTest
{
  /**
   * Verifies that Claude session-start launches Java directly from the plugin-root runtime and does
   * not keep runtime acquisition/install functions for plugin-data copies.
   *
   * @throws IOException if the script cannot be read
   */
  @Test
  public void sessionStartScriptUsesPluginRootRuntimeDirectly() throws IOException
  {
    String script = Files.readString(repoRoot().resolve("plugin/hooks/claude/session-start.sh"),
      StandardCharsets.UTF_8);

    requireThat(script, "script").contains("\"${plugin_root}/client/bin/java\"");
    requireThat(script, "script").doesNotContain("install_bundled_runtime()");
    requireThat(script, "script").doesNotContain("try_acquire_runtime()");
    requireThat(script, "script").doesNotContain("${CLAUDE_PLUGIN_DATA}/client");
  }

  /**
   * Verifies that statusline first-use invokes launchers from CLAUDE_PLUGIN_ROOT instead of
   * CLAUDE_PLUGIN_DATA.
   *
   * @throws IOException if the skill file cannot be read
   */
  @Test
  public void statuslineSkillUsesPluginRootLaunchers() throws IOException
  {
    String firstUse = Files.readString(repoRoot().resolve("plugin/skills/claude/statusline/first-use.md"),
      StandardCharsets.UTF_8);

    requireThat(firstUse, "firstUse").contains("\"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output\" statusline");
    requireThat(firstUse, "firstUse").contains("\"${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-install\"");
    requireThat(firstUse, "firstUse").doesNotContain("${CLAUDE_PLUGIN_DATA}/client/bin/statusline-install");
  }

  /**
   * Verifies that plugin resources invoke bundled runtime launchers from the plugin root, not plugin
   * data directories.
   *
   * @throws IOException if plugin resources cannot be read
   */
  @Test
  public void pluginResourcesDoNotInvokeLaunchersFromPluginData() throws IOException
  {
    Path pluginRoot = repoRoot().resolve("plugin");
    try (Stream<Path> paths = Files.walk(pluginRoot))
    {
      List<Path> files = paths.filter(Files::isRegularFile).toList();
      for (Path file: files)
      {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        requireThat(content, pluginRoot.relativize(file).toString()).
          doesNotContain("${CLAUDE_PLUGIN_DATA}/client/bin");
        requireThat(content, pluginRoot.relativize(file).toString()).
          doesNotContain("${CAT_PLUGIN_DATA}/client/bin");
      }
    }
  }

  /**
   * Verifies shell tests do not keep assertions for deleted session-start runtime acquisition
   * functions.
   *
   * @throws IOException if hook tests cannot be read
   */
  @Test
  public void sessionStartHookTestsDoNotReferenceDeletedRuntimeAcquisition() throws IOException
  {
    Path hookTests = repoRoot().getParent().resolve("tests/hooks");
    try (Stream<Path> paths = Files.walk(hookTests))
    {
      List<Path> files = paths.filter(Files::isRegularFile).toList();
      for (Path file: files)
      {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        requireThat(content, hookTests.relativize(file).toString()).doesNotContain("try_acquire_runtime");
        requireThat(content, hookTests.relativize(file).toString()).doesNotContain("acquire_runtime_lock");
        requireThat(content, hookTests.relativize(file).toString()).doesNotContain("release_runtime_lock");
        requireThat(content, hookTests.relativize(file).toString()).doesNotContain("download_runtime");
      }
    }
  }

  /**
   * Returns repository root path (one level above Maven module directory).
   *
   * @return absolute repository root path
   */
  private static Path repoRoot()
  {
    return Path.of("").toAbsolutePath().normalize().getParent();
  }
}

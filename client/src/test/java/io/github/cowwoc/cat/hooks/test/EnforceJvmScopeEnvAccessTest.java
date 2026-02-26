/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enforces that only MainJvmScope.java, ClaudeEnv.java, and TerminalType.java call System.getenv() directly.
 * <p>
 * Hook handlers must access session-specific values (session ID, env file path) from HookInput JSON,
 * not from environment variables. Non-hook CLI commands use ClaudeEnv to read environment variables.
 * TerminalType.detect() is allowed because it wraps System.getenv() for terminal detection.
 */
public final class EnforceJvmScopeEnvAccessTest
{
  /**
   * Verifies that no Java file except MainJvmScope.java, ClaudeEnv.java, and TerminalType.java contains
   * System.getenv().
   *
   * @throws IOException if scanning source files fails
   */
  @Test
  public void onlyAllowedFilesCallSystemGetenv() throws IOException
  {
    Path sourceRoot = Paths.get("src/main/java");
    requireThat(sourceRoot.toFile().exists(), "sourceRoot").isEqualTo(true);

    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(sourceRoot))
    {
      paths.filter(Files::isRegularFile).
        filter(path -> path.toString().endsWith(".java")).
        filter(path -> !path.toString().endsWith("MainJvmScope.java")).
        filter(path -> !path.toString().endsWith("ClaudeEnv.java")).
        filter(path -> !path.toString().endsWith("TerminalType.java")).
        forEach(path ->
        {
          try
          {
            String content = Files.readString(path);
            if (content.contains("System.getenv("))
            {
              violations.add(sourceRoot.relativize(path).toString());
            }
          }
          catch (IOException e)
          {
            throw WrappedCheckedException.wrap(e);
          }
        });
    }

    if (!violations.isEmpty())
    {
      String message = """
        System.getenv() found in files other than MainJvmScope.java, ClaudeEnv.java, and TerminalType.java.

        REQUIREMENT: Hooks must read session-specific values from HookInput JSON (not environment variables).
        Non-hook CLI commands must use ClaudeEnv to read environment variables.

        Violations found in:
        """ + String.join("\n", violations.stream().map(v -> "  - " + v).toList()) + """


        FIX: For hook handlers, use HookInput.getSessionId() instead of System.getenv("CLAUDE_SESSION_ID").
        FIX: For CLI commands, use new ClaudeEnv().getClaudeSessionId() instead of System.getenv().
        """;
      throw new AssertionError(message);
    }
  }
}

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
 * Enforces that only MainJvmScope.java and TerminalType.java call System.getenv() directly.
 * <p>
 * All other code must access environment variables through JvmScope methods.
 * TerminalType.detect() is allowed because it wraps System.getenv() for terminal detection.
 */
public final class EnforceJvmScopeEnvAccessTest
{
  /**
   * Verifies that no Java file except MainJvmScope.java and TerminalType.java contains System.getenv().
   *
   * @throws IOException if scanning source files fails
   */
  @Test
  public void onlyMainJvmScopeCallsSystemGetenv() throws IOException
  {
    Path sourceRoot = Paths.get("src/main/java");
    requireThat(sourceRoot.toFile().exists(), "sourceRoot").isEqualTo(true);

    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(sourceRoot))
    {
      paths.filter(Files::isRegularFile).
        filter(path -> path.toString().endsWith(".java")).
        filter(path -> !path.toString().endsWith("MainJvmScope.java")).
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
        System.getenv() found in files other than MainJvmScope.java and TerminalType.java.

        REQUIREMENT: All environment variable access must go through JvmScope methods.

        Violations found in:
        """ + String.join("\n", violations.stream().map(v -> "  - " + v).toList()) + """


        FIX: Replace System.getenv("VAR_NAME") with scope.getVarName() calls.
        """;
      throw new AssertionError(message);
    }
  }
}

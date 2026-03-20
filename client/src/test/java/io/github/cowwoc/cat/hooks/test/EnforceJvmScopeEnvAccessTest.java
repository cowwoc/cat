/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enforces that only MainJvmScope.java, ClaudeEnv.java, GetSkill.java, and TerminalType.java call
 * System.getenv() directly.
 * <p>
 * Hook handlers must access session-specific values (session ID, env file path) from HookInput JSON,
 * not from environment variables. Non-hook CLI commands use ClaudeEnv to read environment variables.
 * GetSkill.java is allowed because it is a CLI tool that does generic variable expansion (reading
 * arbitrary environment variables for directive string substitution).
 * TerminalType.detect() is allowed because it wraps System.getenv() for terminal detection.
 */
public final class EnforceJvmScopeEnvAccessTest
{
  /**
   * Verifies that no Java file except the four whitelisted files contains System.getenv().
   * <p>
   * Uses fully-qualified path matching to prevent files with matching names in other packages from
   * bypassing the restriction.
   * <p>
   * This test scans {@code src/main/java} (production code) but intentionally excludes
   * {@code src/test/java} (test code). Test code never runs in hook handler contexts where
   * environment variable access is restricted, so test helper methods may call
   * {@code System.getenv()} without restriction.
   *
   * @throws IOException if scanning source files fails
   */
  @Test
  public void onlyAllowedFilesCallSystemGetenv() throws IOException
  {
    // Maven sets user.dir to the module directory (client/) during test execution.
    Path sourceRoot = Paths.get(System.getProperty("user.dir"), "src/main/java");
    requireThat(sourceRoot.toFile().exists(), "sourceRoot").isTrue();

    List<String> violations = new ArrayList<>();

    List<Path> javaFiles;
    try (Stream<Path> paths = Files.walk(sourceRoot))
    {
      javaFiles = paths.filter(Files::isRegularFile).
        filter(path -> path.toString().endsWith(".java")).
        filter(path -> !sourceRoot.relativize(path).toString().equals(
          "io/github/cowwoc/cat/hooks/MainJvmScope.java")).
        filter(path -> !sourceRoot.relativize(path).toString().equals(
          "io/github/cowwoc/cat/hooks/ClaudeEnv.java")).
        filter(path -> !sourceRoot.relativize(path).toString().equals(
          "io/github/cowwoc/cat/hooks/util/GetSkill.java")).
        filter(path -> !sourceRoot.relativize(path).toString().equals(
          "io/github/cowwoc/cat/hooks/skills/TerminalType.java")).
        toList();
    }
    for (Path path : javaFiles)
    {
      String content = Files.readString(path);
      // Use word-boundary regex to match actual method invocations, not occurrences in
      // comments, Javadoc, or string literals that happen to contain the text.
      if (content.matches("(?s).*\\bSystem\\.getenv\\s*\\(.*"))
        violations.add(sourceRoot.relativize(path).toString());
    }

    if (!violations.isEmpty())
    {
      String message = """
        System.getenv() found in files other than MainJvmScope.java, ClaudeEnv.java, \
        GetSkill.java, and TerminalType.java.

        REQUIREMENT: Hooks must read session-specific values from HookInput JSON (not environment variables).
        Non-hook CLI commands must use ClaudeEnv to read environment variables.

        Violations found in:
        """ + String.join("\n", violations.stream().map(v -> "  - " + v).toList()) + """


        FIX depends on context:
          In CLI main() methods: new ClaudeEnv().getSessionId() or new ClaudeEnv().getEnvFile() \
        are acceptable — ClaudeEnv wraps System.getenv() for non-hook commands.
          In hook handlers / business logic: read session-specific values from HookInput JSON, \
        not environment variables — hooks must not call System.getenv() directly.
        """;
      throw new AssertionError(message);
    }
  }

  /**
   * Verifies that each of the four whitelisted files exists and contains at least one System.getenv()
   * call, confirming the whitelist entries are accurate and not stale.
   *
   * @throws IOException if reading source files fails
   */
  @Test
  public void whitelistedFilesCallSystemGetenv() throws IOException
  {
    // Maven sets user.dir to the module directory (client/) during test execution.
    Path sourceRoot = Paths.get(System.getProperty("user.dir"), "src/main/java");
    String[] whitelistedFiles = {
      "io/github/cowwoc/cat/hooks/MainJvmScope.java",
      "io/github/cowwoc/cat/hooks/ClaudeEnv.java",
      "io/github/cowwoc/cat/hooks/util/GetSkill.java",
      "io/github/cowwoc/cat/hooks/skills/TerminalType.java"
    };

    for (String relativePath : whitelistedFiles)
    {
      Path filePath = sourceRoot.resolve(relativePath);
      boolean fileExists = Files.exists(filePath);
      requireThat(fileExists, "fileExists").withContext(relativePath, "whitelistedFile").isTrue();
      String content = Files.readString(filePath);
      boolean hasGetenv = content.contains("System.getenv(");
      requireThat(hasGetenv, "hasGetenv").withContext(relativePath, "whitelistedFile").isTrue();
    }
  }
}

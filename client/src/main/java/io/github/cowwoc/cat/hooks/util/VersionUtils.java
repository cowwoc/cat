/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudePluginScope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Utility methods for plugin version reading and semantic version comparison.
 */
public final class VersionUtils
{
  private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+){0,2}$");

  /**
   * Prevents instantiation.
   */
  private VersionUtils()
  {
  }

  /**
   * Reads the plugin version from {@code pluginRoot/.claude-plugin/plugin.json}.
   *
   * @param scope the Claude plugin scope providing the plugin root directory and JSON parsing
   * @return the version string
   * @throws NullPointerException if {@code scope} is null
   * @throws AssertionError if the plugin.json file is not found, missing the version field, or has an invalid
   *   format
   * @throws IOException if reading the plugin.json file fails
   */
  public static String getPluginVersion(ClaudePluginScope scope) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    Path pluginJsonFile = scope.getPluginRoot().resolve(".claude-plugin/plugin.json");
    if (!Files.isRegularFile(pluginJsonFile))
    {
      throw new AssertionError("Plugin version not found: " + pluginJsonFile + "\n" +
        "Run /cat-update-client to build and install the jlink runtime.");
    }
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(pluginJsonFile));
    JsonNode versionNode = root.get("version");
    if (versionNode == null || !versionNode.isString())
    {
      throw new AssertionError("Invalid plugin.json: missing or non-string 'version' field in " +
        pluginJsonFile);
    }
    String version = versionNode.asString().strip();
    if (version.isEmpty() || !VERSION_PATTERN.matcher(version).matches())
    {
      throw new AssertionError("Invalid version format in " + pluginJsonFile + ": '" + version +
        "'. Expected X.Y or X.Y.Z");
    }
    return version;
  }

  /**
   * Returns {@code true} if the given version string is a valid numeric version.
   * <p>
   * A valid version matches the pattern {@code X}, {@code X.Y}, or {@code X.Y.Z} where each component
   * is a non-negative integer. Pre-release components (e.g., {@code "-SNAPSHOT"}, {@code "-beta"}) are not
   * supported and will cause this method to return {@code false}.
   *
   * @param version the version string to validate
   * @return {@code true} if the version matches the expected pattern, {@code false} otherwise
   */
  public static boolean isValidVersion(String version)
  {
    if (version == null || version.isBlank())
      return false;
    return VERSION_PATTERN.matcher(version.strip()).matches();
  }

  /**
   * Compares two semantic version strings.
   * <p>
   * Splits each version on dots and compares numeric parts left to right.
   * Missing parts are treated as 0. Non-numeric parts are treated as 0.
   * Null or empty versions are treated as "0.0.0".
   * <p>
   * Pre-release version components (e.g., {@code "-SNAPSHOT"}, {@code "-beta"}) are not supported
   * and will produce undefined comparison results.
   *
   * @param v1 the first version
   * @param v2 the second version
   * @return negative if v1 &lt; v2, 0 if equal, positive if v1 &gt; v2
   */
  public static int compareVersions(String v1, String v2)
  {
    String version1;
    if (v1 == null || v1.isEmpty())
      version1 = "0.0.0";
    else
      version1 = v1;

    String version2;
    if (v2 == null || v2.isEmpty())
      version2 = "0.0.0";
    else
      version2 = v2;

    String[] parts1 = version1.split("\\.");
    String[] parts2 = version2.split("\\.");
    int maxLength = Math.max(parts1.length, parts2.length);

    for (int i = 0; i < maxLength; ++i)
    {
      int num1 = 0;
      int num2 = 0;
      if (i < parts1.length)
      {
        try
        {
          num1 = Integer.parseInt(parts1[i]);
        }
        catch (NumberFormatException _)
        {
          // Treat non-numeric parts as 0
        }
      }
      if (i < parts2.length)
      {
        try
        {
          num2 = Integer.parseInt(parts2[i]);
        }
        catch (NumberFormatException _)
        {
          // Treat non-numeric parts as 0
        }
      }
      if (num1 != num2)
        return Integer.compare(num1, num2);
    }
    return 0;
  }
}

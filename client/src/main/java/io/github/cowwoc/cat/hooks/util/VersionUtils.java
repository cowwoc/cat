/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

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
   * Reads the plugin version from {@code pluginRoot/client/VERSION}.
   *
   * @param pluginRoot the plugin root directory
   * @return the version string
   * @throws NullPointerException if {@code pluginRoot} is null
   * @throws AssertionError if the VERSION file is not found, empty, or has an invalid format
   * @throws IOException if reading the VERSION file fails
   */
  public static String getPluginVersion(Path pluginRoot) throws IOException
  {
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    Path versionFile = pluginRoot.resolve("client/VERSION");
    if (!Files.isRegularFile(versionFile))
    {
      throw new AssertionError("Plugin version not found: " + versionFile + "\n" +
        "Run /cat-update-client to build and install the jlink runtime.");
    }
    String version = Files.readString(versionFile).strip();
    if (version.isEmpty() || !VERSION_PATTERN.matcher(version).matches())
    {
      throw new AssertionError("Invalid version format in " + versionFile + ": '" + version +
        "'. Expected X.Y or X.Y.Z");
    }
    return version;
  }

  /**
   * Compares two semantic version strings.
   * <p>
   * Splits each version on dots and compares numeric parts left to right.
   * Missing parts are treated as 0. Non-numeric parts are treated as 0.
   * Null or empty versions are treated as "0.0.0".
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

package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility methods for plugin version reading and semantic version comparison.
 */
public final class VersionUtils
{
  /**
   * Prevents instantiation.
   */
  private VersionUtils()
  {
  }

  /**
   * Reads the plugin version from plugin.json.
   * <p>
   * Searches first in {@code pluginRoot/plugin.json}, then falls back to
   * {@code pluginRoot/.claude-plugin/plugin.json}.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param pluginRoot the plugin root directory
   * @return the version string
   * @throws NullPointerException if mapper or pluginRoot is null
   * @throws AssertionError if plugin.json is not found or the version field is missing/invalid
   * @throws IOException if reading plugin.json fails
   */
  public static String getPluginVersion(JsonMapper mapper, Path pluginRoot) throws IOException
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    Path pluginFile = pluginRoot.resolve("plugin.json");
    if (!Files.isRegularFile(pluginFile))
      pluginFile = pluginRoot.resolve(".claude-plugin/plugin.json");
    if (!Files.isRegularFile(pluginFile))
      throw new AssertionError("plugin.json not found in " + pluginRoot + " or " +
        pluginRoot.resolve(".claude-plugin"));

    JsonNode root = mapper.readTree(Files.readString(pluginFile));
    JsonNode versionNode = root.get("version");
    if (versionNode == null || !versionNode.isString())
      throw new AssertionError("version field missing or invalid in plugin.json");
    return versionNode.asString();
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

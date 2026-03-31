/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.util.ProcessRunner;

import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Resolves short model names from SKILL.md frontmatter to fully-qualified model identifiers.
 * <p>
 * Mappings are version-aware: each Claude Code version range maps short names to specific model IDs.
 * When a new Claude Code version is released, the latest known mapping is used automatically via
 * {@code NavigableMap.floorEntry()}.
 */
public final class ModelIdResolver
{
  /**
   * Pattern matching Claude Code version output: {@code "X.Y.Z (Claude Code)"}.
   */
  private static final Pattern VERSION_OUTPUT_PATTERN =
    Pattern.compile("(\\d+\\.\\d+\\.\\d+)\\s+\\(Claude Code\\)");

  /**
   * Version-aware mappings from short names to fully-qualified model IDs. Each entry represents the
   * mappings that became effective starting at that Claude Code version.
   */
  private static final NavigableMap<Version, Map<String, String>> VERSION_MAPPINGS;

  /**
   * The minimum Claude Code version supported by this resolver.
   */
  private static final Version MINIMUM_VERSION;

  static
  {
    VERSION_MAPPINGS = new TreeMap<>();
    Version version2dot1dot0 = new Version(2, 1, 0);
    VERSION_MAPPINGS.put(version2dot1dot0, Map.of(
      "haiku", "claude-haiku-4-5-20251001",
      "sonnet", "claude-sonnet-4-6",
      "opus", "claude-opus-4-6"));
    MINIMUM_VERSION = version2dot1dot0;
  }

  /**
   * Prevents instantiation.
   */
  private ModelIdResolver()
  {
  }

  /**
   * Resolves a short model name to its fully-qualified model identifier for a specific Claude Code
   * version.
   * <p>
   * If the input is already a fully-qualified model ID (starts with {@code "claude-"}), it is returned
   * unchanged. Otherwise, the short name is looked up in the version-appropriate model mappings.
   *
   * @param claudeCodeVersion the Claude Code version string (e.g., {@code "2.1.87"})
   * @param shortName         the short model name (e.g., {@code "haiku"}, {@code "sonnet"},
   *                          {@code "opus"}) or a fully-qualified model ID
   * @return the fully-qualified model identifier
   * @throws NullPointerException     if {@code claudeCodeVersion} or {@code shortName} are null
   * @throws IllegalArgumentException if {@code claudeCodeVersion} or {@code shortName} are blank, or if
   *                                  the version is below the minimum supported version, or if
   *                                  {@code shortName} is unknown
   */
  public static String resolve(String claudeCodeVersion, String shortName)
  {
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    requireThat(shortName, "shortName").isNotBlank();
    String lower = shortName.toLowerCase(Locale.ROOT);
    if (lower.startsWith("claude-"))
      return lower;

    Version version = Version.parse(claudeCodeVersion);
    if (version.compareTo(MINIMUM_VERSION) < 0)
    {
      throw new IllegalArgumentException(
        "ModelIdResolver: Claude Code version '" + claudeCodeVersion + "' is below the minimum " +
        "supported version '" + MINIMUM_VERSION + "'.");
    }

    Map.Entry<Version, Map<String, String>> entry = VERSION_MAPPINGS.floorEntry(version);
    if (entry == null)
    {
      throw new IllegalArgumentException(
        "ModelIdResolver: no mappings found for Claude Code version '" + claudeCodeVersion + "'.");
    }

    Map<String, String> mappings = entry.getValue();
    String fullId = mappings.get(lower);
    if (fullId == null)
    {
      throw new IllegalArgumentException(
        "ModelIdResolver: unknown model short name '" + shortName + "' for Claude Code version '" +
        claudeCodeVersion + "'. Known short names: " + mappings.keySet());
    }
    return fullId;
  }

  /**
   * Detects the installed Claude Code version by running {@code claude --version}.
   *
   * @return the version string (e.g., {@code "2.1.87"})
   * @throws IllegalStateException if the command fails or the output is unexpected
   */
  public static String detectClaudeCodeVersion()
  {
    ProcessRunner.Result result = ProcessRunner.run("claude", "--version");
    if (result.exitCode() != 0)
    {
      throw new IllegalStateException(
        "ModelIdResolver.detectClaudeCodeVersion: 'claude --version' failed with exit code " +
        result.exitCode() + ". stdout: " + result.stdout());
    }

    String stdout = result.stdout().strip();
    for (String line : stdout.split("\n"))
    {
      Matcher matcher = VERSION_OUTPUT_PATTERN.matcher(line.strip());
      if (matcher.matches())
        return matcher.group(1);
    }
    throw new IllegalStateException(
      "ModelIdResolver.detectClaudeCodeVersion: unexpected output from 'claude --version': " +
      stdout);
  }

  /**
   * A semantic version with major, minor, and patch components.
   *
   * @param major the major version number
   * @param minor the minor version number
   * @param patch the patch version number
   */
  private record Version(int major, int minor, int patch) implements Comparable<Version>
  {
    /**
     * Creates a new version.
     *
     * @param major the major version number
     * @param minor the minor version number
     * @param patch the patch version number
     * @throws IllegalArgumentException if {@code major}, {@code minor}, or {@code patch} are negative
     */
    Version
    {
      requireThat(major, "major").isGreaterThanOrEqualTo(0);
      requireThat(minor, "minor").isGreaterThanOrEqualTo(0);
      requireThat(patch, "patch").isGreaterThanOrEqualTo(0);
    }

    /**
     * Parses a version string in {@code "major.minor.patch"} format.
     *
     * @param versionString the version string to parse
     * @return the parsed version
     * @throws NullPointerException     if {@code versionString} is null
     * @throws IllegalArgumentException if {@code versionString} is blank or not in the expected format
     */
    static Version parse(String versionString)
    {
      requireThat(versionString, "versionString").isNotBlank();
      String[] parts = versionString.split("\\.");
      if (parts.length != 3)
      {
        throw new IllegalArgumentException(
          "ModelIdResolver.Version.parse: expected 'major.minor.patch' format, got '" +
          versionString + "'.");
      }
      try
      {
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        return new Version(major, minor, patch);
      }
      catch (NumberFormatException e)
      {
        throw new IllegalArgumentException(
          "ModelIdResolver.Version.parse: non-numeric component in version '" +
          versionString + "'.", e);
      }
    }

    @Override
    public int compareTo(Version other)
    {
      int result = Integer.compare(major, other.major);
      if (result != 0)
        return result;
      result = Integer.compare(minor, other.minor);
      if (result != 0)
        return result;
      return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString()
    {
      return major + "." + minor + "." + patch;
    }
  }
}

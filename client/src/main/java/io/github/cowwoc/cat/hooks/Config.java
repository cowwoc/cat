/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Unified configuration loader for CAT plugin.
 *
 * Implements three-layer config loading:
 * 1. Defaults (hardcoded)
 * 2. cat-config.json (project settings)
 * 3. cat-config.local.json (user overrides, gitignored)
 */
public final class Config
{
  // Type reference for JSON deserialization (avoids unchecked cast)
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  /**
   * Default autofix level for the stakeholder review loop.
   * <p>
   * Controls which concern severity levels trigger automatic fix attempts before presenting results to the
   * user. {@code "high_and_above"} means CRITICAL and HIGH concerns are auto-fixed, while MEDIUM and LOW
   * concerns are passed through to the user approval gate.
   *
   * @see #getAutofixLevel()
   */
  public static final String DEFAULT_AUTOFIX_LEVEL = "high_and_above";

  /**
   * Default per-severity limits on how many unresolved concerns are allowed before the review is rejected.
   * <p>
   * After auto-fix attempts complete, if the remaining concern count at a given severity exceeds its limit,
   * the workflow rejects the review instead of proceeding to user approval.
   * {@link Integer#MAX_VALUE} means unlimited (never reject for that severity).
   * {@code 0} means zero tolerance.
   * <p>
   * Defaults: all severities=0 (reject on any unresolved concern).
   *
   * @see #getProceedLimit(String)
   */
  public static final Map<String, Integer> DEFAULT_PROCEED_LIMITS = Map.of(
    "critical", 0, "high", 0, "medium", 0, "low", 0);

  /**
   * Combined default review thresholds, composed from {@link #DEFAULT_AUTOFIX_LEVEL} and
   * {@link #DEFAULT_PROCEED_LIMITS}.
   */
  public static final Map<String, Object> DEFAULT_REVIEW_THRESHOLDS = Map.of(
    "autofix", DEFAULT_AUTOFIX_LEVEL,
    "proceed", DEFAULT_PROCEED_LIMITS);

  // Default configuration values
  private static final Map<String, Object> DEFAULTS;

  static
  {
    Map<String, Object> defaults = new HashMap<>();
    defaults.put("autoRemoveWorktrees", true);
    defaults.put("trust", "medium");
    defaults.put("verify", "changed");
    defaults.put("curiosity", "low");
    defaults.put("patience", "high");
    defaults.put("terminalWidth", 120);
    defaults.put("completionWorkflow", "merge");
    defaults.put("reviewThresholds", DEFAULT_REVIEW_THRESHOLDS);
    DEFAULTS = Map.copyOf(defaults);
  }

  private final Map<String, Object> values;

  /**
   * Creates a new Config with the given values.
   *
   * @param values the merged configuration values
   */
  private Config(Map<String, Object> values)
  {
    this.values = values;
  }

  /**
   * Load configuration with three-layer override.
   * <p>
   * Loading order (later overrides earlier):
   * <ol>
   * <li>Default values</li>
   * <li>cat-config.json (project settings)</li>
   * <li>cat-config.local.json (user overrides)</li>
   * </ol>
   *
   * @param mapper the JSON mapper to use for parsing
   * @param projectDir the project root directory containing .claude/cat/
   * @return the loaded configuration
   * @throws IOException if a config file exists but cannot be read or contains invalid JSON
   * @throws NullPointerException if {@code mapper} or {@code projectDir} are null
   */
  public static Config load(JsonMapper mapper, Path projectDir) throws IOException
  {
    Map<String, Object> merged = new HashMap<>(DEFAULTS);

    Path configDir = projectDir.resolve(".claude").resolve("cat");

    // Layer 2: Load cat-config.json
    Path baseConfigPath = configDir.resolve("cat-config.json");
    if (Files.exists(baseConfigPath))
    {
      Map<String, Object> baseConfig = loadJsonFile(mapper, baseConfigPath);
      merged.putAll(baseConfig);
    }

    // Layer 3: Load cat-config.local.json (overrides base)
    Path localConfigPath = configDir.resolve("cat-config.local.json");
    if (Files.exists(localConfigPath))
    {
      Map<String, Object> localConfig = loadJsonFile(mapper, localConfigPath);
      merged.putAll(localConfig);
    }

    return new Config(merged);
  }

  private static Map<String, Object> loadJsonFile(JsonMapper mapper, Path path) throws IOException
  {
    String content = Files.readString(path);
    return mapper.readValue(content, MAP_TYPE);
  }

  /**
   * Get a configuration value.
   *
   * @param key Configuration key
   * @return Value or null if not found
   */
  public Object get(String key)
  {
    return values.get(key);
  }

  /**
   * Get a configuration value with default.
   *
   * @param key Configuration key
   * @param defaultValue Default if key not found
   * @return Value or default
   */
  public Object get(String key, Object defaultValue)
  {
    return values.getOrDefault(key, defaultValue);
  }

  /**
   * Get a string configuration value.
   *
   * @param key the configuration key
   * @return the value as a string, or empty string if not found
   */
  public String getString(String key)
  {
    Object value = values.get(key);
    if (value != null)
      return value.toString();
    return "";
  }

  /**
   * Get a string configuration value with default.
   *
   * @param key the configuration key
   * @param defaultValue the default value if key not found
   * @return the value as a string, or defaultValue if not found
   */
  public String getString(String key, String defaultValue)
  {
    Object value = values.get(key);
    if (value != null)
      return value.toString();
    return defaultValue;
  }

  /**
   * Get a boolean configuration value.
   * <p>
   * Accepts both {@code Boolean} values and strings ({@code "true"}/{@code "false"}).
   *
   * @param key the configuration key
   * @param defaultValue the default value if key not found
   * @return the value as a boolean, or defaultValue if not found
   * @throws IllegalArgumentException if the value exists but is not a boolean or a string representing a boolean
   */
  public boolean getBoolean(String key, boolean defaultValue)
  {
    Object value = values.get(key);
    if (value == null)
      return defaultValue;
    return switch (value)
    {
      case Boolean b -> b;
      case String s when s.equals("true") -> true;
      case String s when s.equals("false") -> false;
      case String s ->
        throw new IllegalArgumentException("Expected boolean for key \"" + key + "\", got string: \"" + s + "\"");
      default ->
        throw new IllegalArgumentException("Expected boolean for key \"" + key + "\", got: " +
          value.getClass().getSimpleName());
    };
  }

  /**
   * Get an integer configuration value.
   *
   * @param key the configuration key
   * @param defaultValue the default value if key not found or not a number
   * @return the value as an integer, or defaultValue if not found
   */
  public int getInt(String key, int defaultValue)
  {
    Object value = values.get(key);
    if (value instanceof Number n)
      return n.intValue();
    if (value instanceof String s)
    {
      try
      {
        return Integer.parseInt(s);
      }
      catch (NumberFormatException _)
      {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * Get the review thresholds configuration.
   * <p>
   * Returns the {@code reviewThresholds} object from config, or the defaults if not configured.
   * The returned map contains:
   * <ul>
   * <li>{@code autofix} — string: "all", "high_and_above", "critical", or "none"</li>
   * <li>{@code proceed} — map with integer values for "critical", "high", "medium", "low"
   *   ({@code 0} means reject any, {@link Integer#MAX_VALUE} means unlimited)</li>
   * </ul>
   *
   * @return the review thresholds map (never null)
   */
  public Map<String, Object> getReviewThresholds()
  {
    Object value = values.get("reviewThresholds");
    if (value instanceof Map<?, ?> map)
    {
      @SuppressWarnings("unchecked")
      Map<String, Object> typedMap = (Map<String, Object>) map;
      return typedMap;
    }
    return DEFAULT_REVIEW_THRESHOLDS;
  }

  /**
   * Get the autofix level from review thresholds.
   * <p>
   * Controls which severity levels trigger automatic fix loops:
   * <ul>
   * <li>{@code "all"} — fix CRITICAL, HIGH, and MEDIUM before presenting to user</li>
   * <li>{@code "high_and_above"} — fix CRITICAL and HIGH; proceed with MEDIUM (default)</li>
   * <li>{@code "critical"} — fix only CRITICAL; proceed with HIGH and MEDIUM</li>
   * <li>{@code "none"} — never auto-fix; always proceed to user approval</li>
   * </ul>
   *
   * @return the autofix level (defaults to "high_and_above" if not configured)
   */
  public String getAutofixLevel()
  {
    Map<String, Object> thresholds = getReviewThresholds();
    Object value = thresholds.get("autofix");
    String level;
    if (value != null)
      level = value.toString();
    else
      level = DEFAULT_AUTOFIX_LEVEL;
    Set<String> allowed = Set.of("all", "high_and_above", "critical", "none");
    if (!allowed.contains(level))
    {
      throw new IllegalArgumentException("Invalid autofix level: '" + level +
        "'. Expected one of: " + allowed);
    }
    return level;
  }

  /**
   * Get the maximum number of concerns at a given severity allowed to proceed to user approval.
   * <p>
   * After auto-fix attempts, if remaining concerns at the given severity exceed this limit,
   * the workflow blocks rather than proceeding to user approval.
   * {@link Integer#MAX_VALUE} means unlimited (always proceed regardless of count).
   * {@code 0} means none are allowed.
   * <p>
   *
   * @param severity the concern severity: "critical", "high", "medium", or "low"
   * @return the maximum allowed count ({@link Integer#MAX_VALUE} for unlimited), or the default for that
   *   severity
   * @throws IllegalArgumentException if {@code severity} is blank or the configured value is negative
   * @throws NullPointerException if {@code severity} is null
   */
  public int getProceedLimit(String severity)
  {
    requireThat(severity, "severity").isNotBlank();
    Map<String, Object> thresholds = getReviewThresholds();
    Object proceedObj = thresholds.get("proceed");
    if (proceedObj instanceof Map<?, ?> proceedMap)
    {
      @SuppressWarnings("unchecked")
      Map<String, Object> typedProceedMap = (Map<String, Object>) proceedMap;
      Object value = typedProceedMap.get(severity);
      if (value instanceof Number n)
      {
        int result = n.intValue();
        if (result < 0)
        {
          throw new IllegalArgumentException("Invalid proceed limit for severity '" + severity + "': " +
            result + ". Must be >= 0, or use " + Integer.MAX_VALUE + " for unlimited.");
        }
        return result;
      }
    }
    // Fall back to default for this severity
    Integer defaultValue = DEFAULT_PROCEED_LIMITS.get(severity);
    if (defaultValue != null)
      return defaultValue;
    return Integer.MAX_VALUE;
  }

  /**
   * Get the entire configuration as a map.
   *
   * @return a copy of the configuration map
   */
  public Map<String, Object> asMap()
  {
    return new HashMap<>(values);
  }
}

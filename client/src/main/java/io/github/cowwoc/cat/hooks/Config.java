/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.util.CuriosityLevel;
import io.github.cowwoc.cat.hooks.util.PatienceLevel;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.cat.hooks.util.VerifyLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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
  /**
   * Default autofix level for the stakeholder review loop.
   * <p>
   * Controls which concern severity levels trigger automatic fix attempts before presenting results to the
   * user. {@code "low"} means all concerns (CRITICAL, HIGH, MEDIUM, and LOW) are auto-fixed before
   * presenting results to the user approval gate.
   *
   * @see #getAutofixLevel()
   */
  public static final String DEFAULT_AUTOFIX_LEVEL = "low";

  // Type reference for JSON deserialization (avoids unchecked cast)
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

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
    defaults.put("reviewThreshold", DEFAULT_AUTOFIX_LEVEL);
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
   * Get the trust level.
   *
   * @return the parsed {@link TrustLevel} (defaults to {@link TrustLevel#MEDIUM} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized trust level
   */
  public TrustLevel getTrust()
  {
    return TrustLevel.fromString(getString("trust", "medium"));
  }

  /**
   * Get the verify level.
   *
   * @return the parsed {@link VerifyLevel} (defaults to {@link VerifyLevel#CHANGED} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized verify level
   */
  public VerifyLevel getVerify()
  {
    return VerifyLevel.fromString(getString("verify", "changed"));
  }

  /**
   * Get the curiosity level.
   *
   * @return the parsed {@link CuriosityLevel} (defaults to {@link CuriosityLevel#LOW} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized curiosity level
   */
  public CuriosityLevel getCuriosity()
  {
    return CuriosityLevel.fromString(getString("curiosity", "low"));
  }

  /**
   * Get the patience level.
   *
   * @return the parsed {@link PatienceLevel} (defaults to {@link PatienceLevel#HIGH} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized patience level
   */
  public PatienceLevel getPatience()
  {
    return PatienceLevel.fromString(getString("patience", "high"));
  }

  /**
   * Get the autofix level from review thresholds.
   * <p>
   * Controls the minimum severity level that triggers automatic fix loops. The value is the minimum severity
   * at which the agent will automatically iterate to fix concerns:
   * <ul>
   * <li>{@code "low"} — fix all concerns (CRITICAL, HIGH, MEDIUM, and LOW) before presenting to user (default)</li>
   * <li>{@code "medium"} — fix CRITICAL, HIGH, and MEDIUM; present LOW to user</li>
   * <li>{@code "high"} — fix CRITICAL and HIGH; present MEDIUM and LOW to user</li>
   * <li>{@code "critical"} — fix CRITICAL only; present HIGH, MEDIUM, and LOW to user</li>
   * </ul>
   *
   * @return the autofix level (defaults to "low" if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized severity level
   */
  public String getAutofixLevel()
  {
    Object value = values.get("reviewThreshold");
    String level;
    if (value instanceof String s)
      level = s;
    else
      level = DEFAULT_AUTOFIX_LEVEL;
    Set<String> allowed = Set.of("low", "medium", "high", "critical");
    if (!allowed.contains(level))
    {
      throw new IllegalArgumentException("Invalid autofix level: '" + level +
        "'. Expected one of: " + allowed);
    }
    return level;
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

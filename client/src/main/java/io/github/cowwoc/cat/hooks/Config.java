/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.util.ConcernSeverity;
import io.github.cowwoc.cat.hooks.util.EffortLevel;
import io.github.cowwoc.cat.hooks.util.PatienceLevel;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.cat.hooks.util.VerifyLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unified configuration loader for CAT plugin.
 *
 * Implements three-layer config loading:
 * 1. Defaults (hardcoded)
 * 2. config.json (project settings)
 * 3. config.local.json (user overrides, gitignored)
 */
public final class Config
{
  /**
   * The name of the CAT directory at the project root.
   */
  public static final String CAT_DIR_NAME = ".cat";
  // Type reference for JSON deserialization (avoids unchecked cast)
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  // Default configuration values
  private static final Map<String, Object> DEFAULTS;

  static
  {
    Map<String, Object> defaults = new HashMap<>();
    defaults.put("trust", "medium");
    defaults.put("verify", "changed");
    defaults.put("effort", "medium");
    defaults.put("patience", "high");
    defaults.put("fileWidth", 120);
    defaults.put("displayWidth", 120);
    defaults.put("completionWorkflow", "merge");
    defaults.put("minSeverity", "low");
    defaults.put("license", "");
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
   * <li>config.json (project settings)</li>
   * <li>config.local.json (user overrides)</li>
   * </ol>
   *
   * @param mapper the JSON mapper to use for parsing
   * @param projectDir the project root directory containing .cat/
   * @return the loaded configuration
   * @throws IOException if a config file exists but cannot be read or contains invalid JSON
   * @throws NullPointerException if {@code mapper} or {@code projectDir} are null
   * @throws IllegalArgumentException if the configuration contains unknown keys
   */
  public static Config load(JsonMapper mapper, Path projectDir) throws IOException
  {
    Map<String, Object> merged = new HashMap<>(DEFAULTS);

    Path configDir = projectDir.resolve(CAT_DIR_NAME);

    // Layer 2: Load config.json
    Path baseConfigPath = configDir.resolve("config.json");
    if (Files.exists(baseConfigPath))
    {
      Map<String, Object> baseConfig = loadJsonFile(mapper, baseConfigPath);
      if (baseConfig.containsKey("license"))
      {
        throw new IllegalArgumentException(
          "\"license\" is a user-specific value. Move it to config.local.json instead.");
      }
      merged.putAll(baseConfig);
    }

    // Layer 3: Load config.local.json (overrides base)
    Path localConfigPath = configDir.resolve("config.local.json");
    if (Files.exists(localConfigPath))
    {
      Map<String, Object> localConfig = loadJsonFile(mapper, localConfigPath);
      merged.putAll(localConfig);
    }

    // Validate that no unknown keys exist in merged configuration.
    Set<String> unknownKeys = new HashSet<>(merged.keySet());
    unknownKeys.removeAll(DEFAULTS.keySet());

    if (!unknownKeys.isEmpty())
    {
      List<String> sortedUnknown = unknownKeys.stream().sorted().toList();
      List<String> sortedKnown = DEFAULTS.keySet().stream().sorted().toList();
      throw new IllegalArgumentException("Unknown configuration keys found: " +
        String.join(", ", sortedUnknown) +
        ". Known keys are: " + String.join(", ", sortedKnown));
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
   * Get the effort level.
   *
   * @return the parsed {@link EffortLevel} (defaults to {@link EffortLevel#MEDIUM} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized effort level
   */
  public EffortLevel getEffort()
  {
    return EffortLevel.fromString(getString("effort", "medium"));
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
   * Get the minimum concern severity level.
   * <p>
   * Controls which concerns are visible at all. Concerns below the minimum severity are silently ignored —
   * not fixed, not deferred, not tracked. This is a hard floor that determines which concerns exist at all.
   * <ul>
   * <li>{@code "low"} — all concerns (CRITICAL, HIGH, MEDIUM, LOW) are visible (default)</li>
   * <li>{@code "medium"} — MEDIUM, HIGH, and CRITICAL concerns are visible; LOW are ignored</li>
   * <li>{@code "high"} — HIGH and CRITICAL concerns are visible; MEDIUM and LOW are ignored</li>
   * <li>{@code "critical"} — only CRITICAL concerns are visible; HIGH, MEDIUM, and LOW are ignored</li>
   * </ul>
   *
   * @return the minimum concern severity (defaults to {@link ConcernSeverity#LOW} if not configured)
   * @throws IllegalArgumentException if the configured value is not a recognized severity level
   */
  public ConcernSeverity getMinSeverity()
  {
    return ConcernSeverity.fromString(getString("minSeverity", "low"));
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

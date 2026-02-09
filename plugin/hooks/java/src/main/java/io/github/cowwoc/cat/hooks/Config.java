package io.github.cowwoc.cat.hooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
  // Type reference for JSON deserialization (avoids unchecked cast)
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  // Default configuration values
  private static final Map<String, Object> DEFAULTS = Map.of(
    "autoRemoveWorktrees", true,
    "trust", "medium",
    "verify", "changed",
    "curiosity", "low",
    "patience", "high",
    "terminalWidth", 120,
    "completionWorkflow", "merge");

  private final Map<String, Object> values;

  private Config(Map<String, Object> values)
  {
    this.values = values;
  }

  /**
   * Load configuration with three-layer override.
   *
   * Loading order (later overrides earlier):
   * 1. Default values
   * 2. cat-config.json (project settings)
   * 3. cat-config.local.json (user overrides)
   *
   * @param projectDir Project root directory containing .claude/cat/
   * @return Loaded configuration
   */
  public static Config load(Path projectDir)
  {
    Map<String, Object> merged = new HashMap<>(DEFAULTS);

    Path configDir = projectDir.resolve(".claude").resolve("cat");

    // Layer 2: Load cat-config.json
    Path baseConfigPath = configDir.resolve("cat-config.json");
    if (Files.exists(baseConfigPath))
    {
      try
      {
        Map<String, Object> baseConfig = loadJsonFile(baseConfigPath);
        merged.putAll(baseConfig);
      }
      catch (IOException _)
      {
        // Invalid or unreadable base config - continue with defaults
      }
    }

    // Layer 3: Load cat-config.local.json (overrides base)
    Path localConfigPath = configDir.resolve("cat-config.local.json");
    if (Files.exists(localConfigPath))
    {
      try
      {
        Map<String, Object> localConfig = loadJsonFile(localConfigPath);
        merged.putAll(localConfig);
      }
      catch (IOException _)
      {
        // Invalid or unreadable local config - continue with base
      }
    }

    return new Config(merged);
  }

  /**
   * Load configuration from current directory.
   *
   * @return the loaded configuration
   */
  public static Config load()
  {
    return load(Path.of("."));
  }

  private static Map<String, Object> loadJsonFile(Path path) throws IOException
  {
    String content = Files.readString(path);
    return JsonMapper.builder().build().readValue(content, MAP_TYPE);
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
   *
   * @param key the configuration key
   * @param defaultValue the default value if key not found
   * @return the value as a boolean, or defaultValue if not found
   */
  public boolean getBoolean(String key, boolean defaultValue)
  {
    Object value = values.get(key);
    if (value instanceof Boolean b)
      return b;
    if (value instanceof String s)
      return Boolean.parseBoolean(s);
    return defaultValue;
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
   * Get the entire configuration as a map.
   *
   * @return a copy of the configuration map
   */
  public Map<String, Object> asMap()
  {
    return new HashMap<>(values);
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.cat.claude.hook.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.Config;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI tool that atomically updates {@code .cat/config.json} with one or more {@code key=value} pairs.
 * <p>
 * Each argument must be of the form {@code key=value}. The tool validates each key against the known key set
 * and validates each value by type before merging into the existing config file.
 */
public final class UpdateConfig
{
  /**
   * Valid values for the {@code completionWorkflow} key.
   */
  private static final Set<String> COMPLETION_WORKFLOW_VALUES = Set.of("merge", "pr");
  /**
   * Minimum valid value for integer width keys.
   */
  private static final int MIN_WIDTH = 40;
  /**
   * Maximum valid value for integer width keys.
   */
  private static final int MAX_WIDTH = 200;
  /**
   * Type reference for deserializing config.json into a {@code Map<String, Object>}.
   */
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private UpdateConfig()
  {
    // Utility class
  }

  /**
   * Runs the update-config CLI tool.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (RuntimeException | AssertionError e)
      {
        LoggerFactory.getLogger(UpdateConfig.class).error("Unexpected error", e);
        System.out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(UpdateConfig.class).error("Failed to initialize scope", e);
      System.err.println("Failed to initialize scope: " +
        Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
    }
  }

  /**
   * Executes the update-config logic with a caller-provided output stream.
   * <p>
   * Accepts one or more {@code key=value} positional arguments. For each argument: validates the key is
   * known, validates the value by key type, then atomically merges all validated pairs into
   * {@code .cat/config.json}.
   *
   * @param scope the JVM scope providing the project path and JSON mapper
   * @param args  the {@code key=value} arguments to process
   * @param out   the output stream to write the JSON result to
   * @throws NullPointerException if {@code scope}, {@code args}, or {@code out} are null
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out)
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0)
    {
      out.println(errorJson(scope, "At least one key=value argument is required"));
      return;
    }

    // Parse and validate all key=value pairs before touching the filesystem
    Map<String, Object> updates = new LinkedHashMap<>();
    for (String arg : args)
    {
      int equalsIndex = arg.indexOf('=');
      if (equalsIndex <= 0)
      {
        out.println(errorJson(scope, "Invalid argument '" + arg + "': expected key=value format"));
        return;
      }
      String key = arg.substring(0, equalsIndex);
      String value = arg.substring(equalsIndex + 1);

      if (key.isBlank())
      {
        out.println(errorJson(scope, "Invalid argument '" + arg + "': key must not be blank"));
        return;
      }

      Set<String> knownKeys = Config.knownKeys();
      if (!knownKeys.contains(key))
      {
        List<String> sortedKnown = knownKeys.stream().sorted().collect(Collectors.toList());
        out.println(errorJson(scope, "Unknown key '" + key + "'. Known keys: " +
          String.join(", ", sortedKnown)));
        return;
      }

      // Validate value by key type
      Object parsedValue = validateValue(scope, key, value, out);
      if (parsedValue == null)
        return;

      updates.put(key, parsedValue);
    }

    // All args validated — now read, merge, and write config.json atomically
    Path configPath = scope.getProjectPath().resolve(Config.CAT_DIR_NAME).resolve("config.json");
    try
    {
      mergeAndWrite(scope.getJsonMapper(), configPath, updates);
    }
    catch (IOException e)
    {
      out.println(errorJson(scope, "Failed to write config.json: " +
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return;
    }
    out.println("{\"status\":\"OK\"}");
  }

  /**
   * Validates a single value for the given key.
   * <p>
   * Returns the parsed value object on success, or writes an error JSON to {@code out} and returns
   * {@code null} on failure.
   *
   * @param scope the JVM scope (used to format error JSON)
   * @param key   the configuration key
   * @param value the raw string value
   * @param out   the output stream for error messages
   * @return the parsed value, or {@code null} if validation failed
   */
  private static Object validateValue(ClaudeTool scope, String key, String value, PrintStream out)
  {
    switch (key)
    {
      case "trust" ->
      {
        try
        {
          TrustLevel.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'trust': '" + value +
            "'. Expected one of: low, medium, high"));
          return null;
        }
      }
      case "caution" ->
      {
        try
        {
          CautionLevel.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'caution': '" + value +
            "'. Expected one of: low, medium, high"));
          return null;
        }
      }
      case "curiosity" ->
      {
        try
        {
          CuriosityLevel.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'curiosity': '" + value +
            "'. Expected one of: low, medium, high"));
          return null;
        }
      }
      case "perfection" ->
      {
        try
        {
          PerfectionLevel.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'perfection': '" + value +
            "'. Expected one of: low, medium, high"));
          return null;
        }
      }
      case "verbosity" ->
      {
        try
        {
          VerbosityLevel.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'verbosity': '" + value +
            "'. Expected one of: low, medium, high"));
          return null;
        }
      }
      case "minSeverity" ->
      {
        try
        {
          ConcernSeverity.fromString(value);
          return value;
        }
        catch (IllegalArgumentException _)
        {
          out.println(errorJson(scope, "Invalid value for 'minSeverity': '" + value +
            "'. Expected one of: low, medium, high, critical"));
          return null;
        }
      }
      case "completionWorkflow" ->
      {
        if (!COMPLETION_WORKFLOW_VALUES.contains(value))
        {
          out.println(errorJson(scope, "Invalid value for 'completionWorkflow': '" + value +
            "'. Expected one of: merge, pr"));
          return null;
        }
        return value;
      }
      case "fileWidth", "displayWidth" ->
      {
        int intValue;
        try
        {
          intValue = Integer.parseInt(value);
        }
        catch (NumberFormatException _)
        {
          out.println(errorJson(scope, "Invalid value for '" + key + "': '" + value +
            "' is not an integer"));
          return null;
        }
        if (intValue < MIN_WIDTH || intValue > MAX_WIDTH)
        {
          out.println(errorJson(scope, "Invalid value for '" + key + "': " + intValue +
            " is out of range [" + MIN_WIDTH + ", " + MAX_WIDTH + "]"));
          return null;
        }
        return intValue;
      }
      case "license" ->
      {
        // Any non-null string is accepted
        return value;
      }
      default ->
      {
        // This should never occur since we already validated against knownKeys()
        out.println(errorJson(scope, "Unknown key '" + key + "'"));
        return null;
      }
    }
  }

  /**
   * Reads the existing config.json (if present), merges the given updates, and writes the result
   * atomically using a temporary file in the same directory followed by an atomic rename.
   *
   * @param mapper     the JSON mapper to use for reading and writing
   * @param configPath the path to {@code .cat/config.json}
   * @param updates    the validated key-value pairs to merge in
   * @throws IOException if the file cannot be read or written
   */
  private static void mergeAndWrite(JsonMapper mapper, Path configPath,
    Map<String, Object> updates) throws IOException
  {
    // Load existing raw config (no defaults applied) to avoid clobbering config.local.json overrides
    Map<String, Object> existing = new LinkedHashMap<>();
    if (Files.exists(configPath))
    {
      String content = Files.readString(configPath);
      Map<String, Object> loaded = mapper.readValue(content, MAP_TYPE);
      existing.putAll(loaded);
    }
    else
    {
      // Ensure the parent directory exists so the write succeeds
      Files.createDirectories(configPath.getParent());
    }

    // Merge updates into existing
    existing.putAll(updates);

    // Write to a temp file in the same directory, then atomically rename
    Path tempPath = configPath.resolveSibling("config.json.tmp");
    Files.writeString(tempPath, mapper.writeValueAsString(existing));
    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Formats an error JSON string using the scope's JSON mapper to escape the message.
   *
   * @param scope   the JVM scope providing a JSON mapper
   * @param message the error message
   * @return a JSON string of the form {@code {"status":"ERROR","message":"..."}}
   */
  private static String errorJson(ClaudeTool scope, String message)
  {
    try
    {
      String escapedMessage = scope.getJsonMapper().writeValueAsString(message);
      return "{\"status\":\"ERROR\",\"message\":" + escapedMessage + "}";
    }
    catch (JacksonException _)
    {
      return "{\"status\":\"ERROR\",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
  }
}

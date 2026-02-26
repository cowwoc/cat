/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.Strings;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the CAT statusline configuration.
 * <p>
 * Creates or updates the project's {@code .claude/settings.json} to configure the {@code statusLine}
 * entry to invoke the Java-based statusline command from the CAT jlink bundle.
 * <p>
 * The installed command path is: {@code <pluginRoot>/client/bin/statusline-command}
 */
public final class StatuslineInstall
{
  private final JsonMapper mapper;

  /**
   * Creates a new StatuslineInstall.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public StatuslineInstall(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Installs the CAT statusline configuration into the specified project directory.
   * <p>
   * Creates the {@code .claude} directory if it does not exist. Creates or updates
   * {@code .claude/settings.json} with the {@code statusLine} configuration pointing to the
   * Java statusline-command tool in the plugin's jlink bundle.
   *
   * @param projectDir the Claude project directory where the statusline is to be installed
   * @param pluginRoot the CAT plugin root directory containing the jlink bundle
   * @return a JSON string with status "OK" and the settings/script paths, or status "ERROR" with a message
   * @throws NullPointerException if {@code projectDir} or {@code pluginRoot} are null
   * @throws IOException          if an I/O error occurs
   */
  public String install(Path projectDir, Path pluginRoot) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();

    Path claudeDir = projectDir.resolve(".claude");
    try
    {
      Files.createDirectories(claudeDir);
    }
    catch (IOException e)
    {
      return buildError("Failed to create directory: " + claudeDir + ": " + e.getMessage());
    }

    // The statusline-command binary in the jlink bundle
    Path statuslineCommandPath = pluginRoot.resolve("client/bin/statusline-command");
    Path settingsFile = claudeDir.resolve("settings.json");

    ObjectNode root;

    if (!Files.exists(settingsFile))
    {
      root = mapper.createObjectNode();
    }
    else
    {
      String existingContent;
      try
      {
        existingContent = Files.readString(settingsFile);
      }
      catch (IOException e)
      {
        return buildError("Failed to read settings.json: " + settingsFile + ": " + e.getMessage());
      }

      try
      {
        root = (ObjectNode) mapper.readTree(existingContent);
      }
      catch (JacksonException e)
      {
        return buildError("Existing settings.json is not valid JSON: " + settingsFile + ": " + e.getMessage());
      }
      catch (ClassCastException _)
      {
        return buildError("settings.json does not contain a JSON object: " + settingsFile);
      }
    }

    // Set statusLine configuration
    ObjectNode statusLine = mapper.createObjectNode();
    statusLine.put("type", "command");
    statusLine.put("command", statuslineCommandPath.toString());
    root.set("statusLine", statusLine);

    // Write updated settings.json with pretty printing (no spaces around colons)
    String updatedContent;
    try
    {
      String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
      // Remove spaces around colons: " : " -> ":"
      updatedContent = prettyJson.replaceAll(" : ", ": ");
      // Remove spaces inside empty objects: "{ }" -> "{}"
      updatedContent = updatedContent.replaceAll("\\{ \\}", "{}");
    }
    catch (JacksonException e)
    {
      return buildError("Failed to serialize settings.json: " + e.getMessage());
    }

    Path tempFile = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
    try
    {
      Files.writeString(tempFile, updatedContent);
      Files.move(tempFile, settingsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e)
    {
      Files.deleteIfExists(tempFile);
      return buildError("Failed to write settings.json: " + settingsFile + ": " + e.getMessage());
    }

    return """
      {
        "status": "OK",
        "script_path": "%s",
        "settings_path": "%s"
      }""".formatted(
      Strings.escapeJson(statuslineCommandPath.toString()),
      Strings.escapeJson(settingsFile.toString()));
  }

  /**
   * Builds an error JSON response.
   *
   * @param message the error message
   * @return a JSON string with status "ERROR" and the message
   * @throws NullPointerException if {@code message} is null
   */
  private String buildError(String message)
  {
    requireThat(message, "message").isNotNull();
    return """
      {
        "status": "ERROR",
        "message": "%s"
      }""".formatted(Strings.escapeJson(message));
  }

  /**
   * Main entry point.
   * <p>
   * Usage: statusline-install {@code <projectDir>} {@code <pluginRoot>}
   *
   * @param args command-line arguments: project directory, plugin root
   */
  public static void main(String[] args)
  {
    if (args.length < 2)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Usage: statusline-install <projectDir> <pluginRoot>"
        }""");
      System.exit(1);
    }

    Path projectDir = Path.of(args[0]);
    Path pluginRoot = Path.of(args[1]);

    try (JvmScope scope = new MainJvmScope())
    {
      StatuslineInstall installer = new StatuslineInstall(scope);
      try
      {
        String result = installer.install(projectDir, pluginRoot);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "ERROR",
            "message": "%s"
          }""".formatted(Strings.escapeJson(e.getMessage())));
        System.exit(1);
      }
    }
  }
}

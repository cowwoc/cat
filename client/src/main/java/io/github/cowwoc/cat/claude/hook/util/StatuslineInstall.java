/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import static io.github.cowwoc.cat.claude.hook.Strings.block;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import io.github.cowwoc.cat.claude.hook.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Installs the CAT statusline configuration.
 * <p>
 * Creates or updates the project's {@code .claude/settings.json} to configure the {@code statusLine}
 * entry to invoke the Java-based statusline command from the CAT jlink bundle.
 * <p>
 * The installed command path is: {@code <pluginData>/client/bin/statusline-command}
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
   * @param projectPath the Claude project directory where the statusline is to be installed
   * @param pluginData the CAT plugin root directory containing the jlink bundle
   * @return a JSON string with status "OK" and the settings/script paths, or status "ERROR" with a message
   * @throws NullPointerException if {@code projectPath} or {@code pluginData} are null
   * @throws IOException          if an I/O error occurs
   */
  public String install(Path projectPath, Path pluginData) throws IOException
  {
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginData, "pluginData").isNotNull();

    Path claudeDir = projectPath.resolve(".claude");
    try
    {
      Files.createDirectories(claudeDir);
    }
    catch (IOException e)
    {
      return buildError("StatuslineInstall: Failed to create directory: " + claudeDir + ": " + e.getMessage());
    }

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
        return buildError("StatuslineInstall: Failed to read settings.json: " + settingsFile + ": " + e.getMessage());
      }

      try
      {
        root = (ObjectNode) mapper.readTree(existingContent);
      }
      catch (JacksonException e)
      {
        return buildError("StatuslineInstall: Existing settings.json is not valid JSON: " +
          settingsFile + ": " + e.getMessage());
      }
      catch (ClassCastException _)
      {
        return buildError("StatuslineInstall: settings.json does not contain a JSON object: " + settingsFile);
      }
    }

    // Set statusLine configuration
    ObjectNode statusLine = mapper.createObjectNode();
    statusLine.put("type", "command");
    // ${CLAUDE_PLUGIN_DATA} does not expand in settings.json so we're forced to expand the path at installation time.
    Path statuslineCommand = pluginData.resolve("client/bin/statusline-command");
    statusLine.put("command", statuslineCommand.toString());
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
      return buildError("StatuslineInstall: Failed to serialize settings.json: " + e.getMessage());
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
      return buildError("StatuslineInstall: Failed to write settings.json: " + settingsFile + ": " + e.getMessage());
    }

    return """
      {
        "status": "OK",
        "script_path": "%s",
        "settings_path": "%s"
      }""".formatted(
      Strings.escapeJson(statuslineCommand.toString()),
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
   *
   * @param args command-line arguments: project directory, plugin root
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException e)
      {
        System.out.println(block(scope, "StatuslineInstall: " +
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(StatuslineInstall.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope, "StatuslineInstall: " +
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the statusline installation.
   * <p>
   * Usage: {@code statusline-install <projectPath> <pluginData>}
   *
   * @param scope the JVM scope
   * @param args  command-line arguments: project directory, plugin root
   * @param out   the output stream to write to
   * @throws NullPointerException if any of {@code scope}, {@code args}, or {@code out} are null
   */
  public static void run(JvmScope scope, String[] args, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length < 2)
    {
      out.println(block(scope, "Usage: statusline-install <projectPath> <pluginData>"));
      return;
    }
    if (args.length > 2)
    {
      throw new IllegalArgumentException(
        "Expected exactly 2 arguments (projectPath, pluginData), got " + args.length + ". " +
          "Usage: statusline-install <projectPath> <pluginData>");
    }

    Path projectPath = Path.of(args[0]);
    Path pluginData = Path.of(args[1]);

    StatuslineInstall installer = new StatuslineInstall(scope);
    try
    {
      String result = installer.install(projectPath, pluginData);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(block(scope, "StatuslineInstall: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}

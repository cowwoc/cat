/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:config skill.
 * <p>
 * Reads configuration and provides config box outputs for all configuration-related display boxes.
 */
public final class GetConfigOutput implements SkillOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetConfigOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetConfigOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Get current settings display box using project root from environment.
   * <p>
   * This method supports direct preprocessing pattern - it collects all
   * necessary data from the environment without requiring LLM-provided arguments.
   *
   * @return the formatted settings box, or null if CLAUDE_PROJECT_DIR not set or config not found
   * @throws IOException if the config file cannot be read or contains invalid JSON
   */
  public String getCurrentSettings() throws IOException
  {
    return getCurrentSettings(scope.getClaudeProjectDir());
  }

  /**
   * Get current settings display box.
   *
   * @param projectRoot the project root path
   * @return the formatted settings box, or null if config file not found
   * @throws IOException if the config file cannot be read or contains invalid JSON
   * @throws NullPointerException if {@code projectRoot} is null
   */
  public String getCurrentSettings(Path projectRoot) throws IOException
  {
    requireThat(projectRoot, "projectRoot").isNotNull();

    Path configFile = projectRoot.resolve(".claude").resolve("cat").resolve("cat-config.json");
    if (!Files.exists(configFile))
      return null;

    // Load config using the Config class
    Config config = Config.load(scope.getJsonMapper(), projectRoot);

    String trust = config.getString("trust", "medium");
    String verify = config.getString("verify", "changed");
    String curiosity = config.getString("curiosity", "low");
    String patience = config.getString("patience", "high");
    boolean autoRemove = config.getBoolean("autoRemoveWorktrees", true);

    String cleanupDisplay;
    if (autoRemove)
      cleanupDisplay = "Auto-remove";
    else
      cleanupDisplay = "Keep";

    return buildSimpleHeaderBox(
      "⚙️",
      "CURRENT SETTINGS",
      List.of(
        "",
        "  🤝 Trust: " + trust,
        "  ✅ Verify: " + verify,
        "  🔍 Curiosity: " + curiosity,
        "  ⏳ Patience: " + patience,
        "  🧹 Cleanup: " + cleanupDisplay,
        ""));
  }

  /**
   * Get version gates overview box.
   *
   * @return the formatted overview box
   */
  public String getVersionGatesOverview()
  {
    return buildSimpleHeaderBox(
      "📊",
      "VERSION GATES",
      List.of(
        "",
        "Entry and exit gates control version dependencies.",
        "",
        "Select a version to configure its gates,",
        "or choose 'Apply defaults to all'."));
  }

  /**
   * Get gates for version box.
   *
   * @param version the version number
   * @param entryGateDescription the entry gate description
   * @param exitGateDescription the exit gate description
   * @return the formatted gates box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getGatesForVersion(String version, String entryGateDescription, String exitGateDescription)
  {
    requireThat(version, "version").isNotBlank();
    requireThat(entryGateDescription, "entryGateDescription").isNotBlank();
    requireThat(exitGateDescription, "exitGateDescription").isNotBlank();

    return buildSimpleHeaderBox(
      "🚧",
      "GATES FOR " + version,
      List.of(
        "",
        "Entry: " + entryGateDescription,
        "Exit: " + exitGateDescription));
  }

  /**
   * Get gates updated confirmation box.
   *
   * @param version the version number
   * @param newEntryGate the new entry gate description
   * @param newExitGate the new exit gate description
   * @return the formatted confirmation box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getGatesUpdated(String version, String newEntryGate, String newExitGate)
  {
    requireThat(version, "version").isNotBlank();
    requireThat(newEntryGate, "newEntryGate").isNotBlank();
    requireThat(newExitGate, "newExitGate").isNotBlank();

    return buildSimpleHeaderBox(
      "✅",
      "GATES UPDATED",
      List.of(
        "",
        "Version: " + version,
        "Entry: " + newEntryGate,
        "Exit: " + newExitGate));
  }

  /**
   * Get setting updated confirmation box.
   *
   * @param settingName the setting name
   * @param oldValue the previous value
   * @param newValue the new value
   * @return the formatted confirmation box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getSettingUpdated(String settingName, String oldValue, String newValue)
  {
    requireThat(settingName, "settingName").isNotBlank();
    requireThat(oldValue, "oldValue").isNotBlank();
    requireThat(newValue, "newValue").isNotBlank();

    return buildSimpleHeaderBox(
      "✅",
      "SETTING UPDATED",
      List.of(
        "",
        settingName + ": " + oldValue + " → " + newValue));
  }

  /**
   * Get configuration saved confirmation box.
   *
   * @return the formatted confirmation box
   */
  public String getConfigurationSaved()
  {
    return buildSimpleHeaderBox(
      "✅",
      "CONFIGURATION SAVED",
      List.of(
        "",
        "Changes committed to cat-config.json"));
  }

  /**
   * Get no changes box.
   *
   * @return the formatted no changes box
   */
  public String getNoChanges()
  {
    return buildSimpleHeaderBox(
      "ℹ️",
      "NO CHANGES",
      List.of(
        "",
        "Configuration unchanged."));
  }

  /**
   * Generates all config output boxes as a labeled string for use by the skill preprocessor.
   * <p>
   * Each box is labeled with its section name so the skill can reference it by name.
   *
   * @param args the arguments from the preprocessor directive (must be empty)
   * @return the formatted config output containing all boxes, or an error message if the project
   *   directory is not available
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if {@code args} is not empty
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").length().isEqualTo(0);
    String currentSettings = getCurrentSettings();
    String versionGatesOverview = getVersionGatesOverview();
    String configurationSaved = getConfigurationSaved();
    String noChanges = getNoChanges();

    StringBuilder output = new StringBuilder();
    output.append("SKILL OUTPUT CONFIG BOXES\n\n");
    output.append("CURRENT_SETTINGS:\n");
    if (currentSettings != null)
      output.append(currentSettings).append('\n');
    output.append("\nVERSION_GATES_OVERVIEW:\n");
    output.append(versionGatesOverview).append('\n');
    output.append("\nCONFIGURATION_SAVED:\n");
    output.append(configurationSaved).append('\n');
    output.append("\nNO_CHANGES:\n");
    output.append(noChanges).append('\n');
    return output.toString();
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetConfigOutput generator = new GetConfigOutput(scope);
      String output = generator.getOutput(args);
      System.out.println(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating config output: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(GetConfigOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Builds a simple header box with icon prefix.
   *
   * @param icon the icon character(s)
   * @param title the title text
   * @param contentLines the content lines
   * @return the formatted box
   */
  private String buildSimpleHeaderBox(String icon, String title, List<String> contentLines)
  {
    DisplayUtils display = scope.getDisplayUtils();
    String header = icon + " " + title;
    int headerWidth = display.displayWidth(header);

    // Calculate max width from content
    int maxWidth = headerWidth;
    for (String line : contentLines)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    StringBuilder sb = new StringBuilder();

    // Header top with embedded title
    String prefix = DisplayUtils.HORIZONTAL_LINE + DisplayUtils.HORIZONTAL_LINE + DisplayUtils.HORIZONTAL_LINE + " ";
    int suffixDashCount = maxWidth - prefix.length() - headerWidth + 2;
    if (suffixDashCount < 1)
      suffixDashCount = 1;
    sb.append('╭').append(prefix).append(header).append(' ').
      append(DisplayUtils.HORIZONTAL_LINE.repeat(suffixDashCount)).append("╮\n");

    // Content lines
    for (String content : contentLines)
      sb.append(display.buildLine(content, maxWidth)).append('\n');

    // Bottom border
    sb.append(display.buildBottomBorder(maxWidth));

    return sb.toString();
  }
}

package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:config skill.
 *
 * Reads configuration and provides config box outputs.
 */
public final class GetConfigOutput
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
   *
   * This method supports direct preprocessing pattern - it collects all
   * necessary data from the environment without requiring LLM-provided arguments.
   *
   * @return the formatted settings box, or null if CLAUDE_PROJECT_DIR not set or config not found
   */
  public String getCurrentSettings()
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isBlank())
      return null;
    return getCurrentSettings(Path.of(projectDir));
  }

  /**
   * Get current settings display box.
   *
   * @param projectRoot the project root path
   * @return the formatted settings box, or null if config file not found
   * @throws NullPointerException if projectRoot is null
   */
  public String getCurrentSettings(Path projectRoot)
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

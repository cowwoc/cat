package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:init skill.
 *
 * Generates init boxes using DisplayUtils.
 */
public final class GetInitOutput
{
  /**
   * Standard internal width for init boxes.
   * Total box width = 70 characters (68 internal + 2 borders).
   */
  private static final int BOX_WIDTH = 68;

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetInitOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetInitOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Build the default gates configured box.
   *
   * @param versionCount the number of versions with gates configured
   * @return the formatted box
   * @throws IllegalArgumentException if versionCount is negative
   */
  public String getDefaultGatesConfigured(int versionCount)
  {
    requireThat(versionCount, "versionCount").isNotNegative();

    return scope.getDisplayUtils().buildHeaderBox(
      "📊 Default gates configured for " + versionCount + " versions",
      List.of(
        "                                                                  ",
        "  Entry gates: Work proceeds sequentially                         ",
        "  - Each minor waits for previous minor to complete               ",
        "  - Each major waits for previous major to complete               ",
        "                                                                  ",
        "  Exit gates: Standard completion criteria                        ",
        "  - Minor versions: all tasks must complete                       ",
        "  - Major versions: all minor versions must complete              ",
        "                                                                  ",
        "  To customize gates for any version:                             ",
        "  → /cat:config → 📊 Version Gates                                "),
      BOX_WIDTH);
  }

  /**
   * Build the research skipped box.
   *
   * @param exampleVersion example version number to show in help text
   * @return the formatted box
   * @throws NullPointerException if exampleVersion is null
   * @throws IllegalArgumentException if exampleVersion is blank
   */
  public String getResearchSkipped(String exampleVersion)
  {
    requireThat(exampleVersion, "exampleVersion").isNotBlank();

    return scope.getDisplayUtils().buildHeaderBox(
      "ℹ️ RESEARCH SKIPPED",
      List.of(
        "                                                                  ",
        "  Stakeholder research was skipped during import.                 ",
        "                                                                  ",
        "  To research a pending version later:                            ",
        "  → /cat:research {version}                                       ",
        "                                                                  ",
        "  Example: /cat:research " + exampleVersion + "                                      "),
      BOX_WIDTH);
  }

  /**
   * Build the choose your partner box.
   *
   * @return the formatted box
   */
  public String getChooseYourPartner()
  {
    return scope.getDisplayUtils().buildHeaderBox(
      "🎮 CHOOSE YOUR PARTNER",
      List.of(
        "                                                                  ",
        "  Every developer has a style. These questions shape how your     ",
        "  AI partner approaches the work ahead.                           ",
        "                                                                  ",
        "  Choose wisely - your preferences guide every decision.          "),
      BOX_WIDTH);
  }

  /**
   * Build the CAT initialized box.
   *
   * @param trust the trust preference value
   * @param curiosity the curiosity preference value
   * @param patience the patience preference value
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getCatInitialized(String trust, String curiosity, String patience)
  {
    requireThat(trust, "trust").isNotBlank();
    requireThat(curiosity, "curiosity").isNotBlank();
    requireThat(patience, "patience").isNotBlank();

    String pad = " ".repeat(50);
    String trustLine = "  🤝 Trust: " + trust + pad.substring(0, Math.max(1, 53 - trust.length()));
    String curiosityLine = "  🔍 Curiosity: " + curiosity +
      pad.substring(0, Math.max(1, 49 - curiosity.length()));
    String patienceLine = "  ⏳ Patience: " + patience +
      pad.substring(0, Math.max(1, 51 - patience.length()));

    return scope.getDisplayUtils().buildHeaderBox(
      "🚀 CAT INITIALIZED",
      List.of(
        "                                                                  ",
        trustLine,
        curiosityLine,
        patienceLine,
        "                                                                  ",
        "  Your partner is ready. Let's build something solid.             ",
        "  Adjust anytime: /cat:config                                     "),
      BOX_WIDTH);
  }

  /**
   * Build the first issue walkthrough box.
   *
   * @return the formatted box
   */
  public String getFirstIssueWalkthrough()
  {
    return scope.getDisplayUtils().buildHeaderBox(
      "📋 FIRST ISSUE WALKTHROUGH",
      List.of(
        "                                                                  ",
        "  Great! Let's create your first issue together.                  ",
        "  I'll ask a few questions to understand what you want to build.  "),
      BOX_WIDTH);
  }

  /**
   * Build the first issue created box.
   *
   * @param issueName the sanitized issue name from user input
   * @return the formatted box
   * @throws NullPointerException if issueName is null
   * @throws IllegalArgumentException if issueName is blank
   */
  public String getFirstIssueCreated(String issueName)
  {
    requireThat(issueName, "issueName").isNotBlank();

    // Pad issue name and location to fit box width
    String issueLine = "  Issue: " + issueName;
    String locationLine = "  Location: .claude/cat/issues/v0/v0.0/" + issueName + "/";

    return scope.getDisplayUtils().buildHeaderBox(
      "✅ FIRST ISSUE CREATED",
      List.of(
        "                                                                  ",
        padToWidth(issueLine, 66),
        padToWidth(locationLine, 66),
        "                                                                  ",
        "  Files created:                                                  ",
        "  - PLAN.md - What needs to be done                               ",
        "  - STATE.md - Progress tracking                                  "),
      BOX_WIDTH);
  }

  /**
   * Build the all set box.
   *
   * @return the formatted box
   */
  public String getAllSet()
  {
    return scope.getDisplayUtils().buildHeaderBox(
      "👋 ALL SET",
      List.of(
        "                                                                  ",
        "  Your project is ready. When you want to start:                  ",
        "                                                                  ",
        "  → /cat:work         Execute your first issue                    ",
        "  → /cat:status       See project overview                        ",
        "  → /cat:add          Add more tasks or versions                  ",
        "  → /cat:help         Full command reference                      "),
      BOX_WIDTH);
  }

  /**
   * Build the explore at your own pace box.
   *
   * @return the formatted box
   */
  public String getExploreAtYourOwnPace()
  {
    return scope.getDisplayUtils().buildHeaderBox(
      "👋 EXPLORE AT YOUR OWN PACE",
      List.of(
        "                                                                  ",
        "  Essential commands to get started:                              ",
        "                                                                  ",
        "  → /cat:status       See what's happening                        ",
        "  → /cat:add          Add versions and tasks                      ",
        "  → /cat:work         Execute tasks                               ",
        "  → /cat:help         Full command reference                      ",
        "                                                                  ",
        "  Tip: Run /cat:status anytime to see suggested next steps.       "),
      BOX_WIDTH);
  }

  /**
   * Pads a string with spaces to reach the target width.
   *
   * @param text the text to pad
   * @param width the target width
   * @return the padded string
   */
  private String padToWidth(String text, int width)
  {
    if (text.length() >= width)
      return text;
    return text + " ".repeat(width - text.length());
  }
}

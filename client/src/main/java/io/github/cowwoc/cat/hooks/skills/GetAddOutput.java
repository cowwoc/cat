/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:add skill.
 * <p>
 * Generates box displays for issue and version creation completion.
 * Supports single issues, multiple comma-separated issues, and versions.
 */
public final class GetAddOutput implements SkillOutput
{
  /**
   * The minimum width of the display box.
   */
  private static final int BOX_MIN_WIDTH = 40;
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetAddOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetAddOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates the output for this skill.
   * <p>
   * Parses {@code --project-dir PATH} from {@code args} if present; otherwise falls back to
   * {@code scope.getClaudeProjectDir()}.
   *
   * @param args the arguments from the preprocessor directive
   * @return the generated output
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if an unknown argument is provided or {@code --project-dir} lacks a value
   * @throws IOException              if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    Path projectDir = null;
    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--project-dir"))
      {
        if (i + 1 >= args.length)
          throw new IllegalArgumentException("Missing PATH argument for --project-dir");
        projectDir = Path.of(args[i + 1]);
        ++i;
      }
      else
        throw new IllegalArgumentException("Unknown argument: " + args[i]);
    }
    if (projectDir == null)
      projectDir = scope.getClaudeProjectDir();
    return "GetAddOutput: project-dir=" + projectDir;
  }

  /**
   * Generates output display for add completion.
   *
   * @param itemType     the type of item to create
   * @param itemNames    the names of the created items (one per issue, or a single version name)
   * @param version      the version number
   * @param issueType    the issue type (for issues only, may be null to default to FEATURE)
   * @param dependencies the issue dependencies (for issues only, empty list if none)
   * @param parentInfo   the parent version info (for versions only, may be empty)
   * @param path         the filesystem path to the created item (for versions only, may be empty)
   * @return the formatted output
   * @throws NullPointerException     if {@code itemType}, {@code itemNames}, {@code version},
   *                                  or {@code dependencies} are null
   * @throws IllegalArgumentException if {@code itemNames} is empty or {@code version} is blank
   */
  public String getOutput(ItemType itemType, List<String> itemNames, String version,
                          IssueType issueType, List<String> dependencies,
                          String parentInfo, String path)
  {
    requireThat(itemType, "itemType").isNotNull();
    requireThat(itemNames, "itemNames").isNotNull().isNotEmpty();
    requireThat(version, "version").isNotBlank();
    requireThat(dependencies, "dependencies").isNotNull();

    if (itemType == ItemType.ISSUE)
      return buildIssueDisplay(itemNames, version, issueType, dependencies);
    return buildVersionDisplay(itemNames, version, parentInfo, path);
  }

  /**
   * Builds the issue display box.
   * <p>
   * When {@code itemNames} contains more than one element, each name is displayed as a numbered list item
   * with a plural "Issues Created" header.
   *
   * @param itemNames    the issue names
   * @param version      the version number
   * @param issueType    the issue type
   * @param dependencies the issue dependencies
   * @return the formatted issue box with next command hint
   */
  private String buildIssueDisplay(List<String> itemNames, String version, IssueType issueType,
                                   List<String> dependencies)
  {
    IssueType effectiveIssueType = issueType;
    if (effectiveIssueType == null)
      effectiveIssueType = IssueType.FEATURE;

    String issueTypeName = effectiveIssueType.name();
    String issueTypeDisplay = issueTypeName.charAt(0) +
      issueTypeName.substring(1).toLowerCase(Locale.ROOT);

    String dependenciesDisplay;
    if (dependencies.isEmpty())
      dependenciesDisplay = "None";
    else
      dependenciesDisplay = String.join(", ", dependencies);

    DisplayUtils display = scope.getDisplayUtils();

    if (itemNames.size() > 1)
      return buildMultiIssueDisplay(itemNames, version, issueTypeDisplay, dependenciesDisplay, display);
    return buildSingleIssueDisplay(itemNames.get(0), version, issueTypeDisplay, dependenciesDisplay, display);
  }

  /**
   * Builds a single-issue display box.
   *
   * @param itemName         the issue name
   * @param version          the version number
   * @param issueTypeDisplay the formatted issue type string
   * @param dependenciesDisplay the formatted dependencies string
   * @param display          the display utils instance
   * @return the formatted single-issue box with next command hint
   */
  private String buildSingleIssueDisplay(String itemName, String version, String issueTypeDisplay,
                                         String dependenciesDisplay, DisplayUtils display)
  {
    List<String> contentItems = List.of(
      itemName,
      "",
      "Version: " + version,
      "Type: " + issueTypeDisplay,
      "Dependencies: " + dependenciesDisplay);

    String header = "✅ Issue Created";
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), BOX_MIN_WIDTH,
      DisplayUtils.HORIZONTAL_LINE + " ");

    String nextCmd = "/cat:work " + version + "-" + itemName;

    return finalBox + "\n" +
      "\n" +
      "Next: /clear, then " + nextCmd;
  }

  /**
   * Builds a multi-issue display box with a numbered list.
   *
   * @param itemNames        the issue names
   * @param version          the version number
   * @param issueTypeDisplay the formatted issue type string
   * @param dependenciesDisplay the formatted dependencies string
   * @param display          the display utils instance
   * @return the formatted multi-issue box with next command hint
   */
  private String buildMultiIssueDisplay(List<String> itemNames, String version, String issueTypeDisplay,
                                        String dependenciesDisplay, DisplayUtils display)
  {
    List<String> contentItems = new ArrayList<>();

    for (int i = 0; i < itemNames.size(); ++i)
      contentItems.add((i + 1) + ". " + itemNames.get(i));

    contentItems.add("");
    contentItems.add("Version: " + version);
    contentItems.add("Type: " + issueTypeDisplay);
    contentItems.add("Dependencies: " + dependenciesDisplay);

    String header = "✅ Issues Created";
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), BOX_MIN_WIDTH,
      DisplayUtils.HORIZONTAL_LINE + " ");

    String nextCmd = "/cat:work " + version + "-" + itemNames.get(0);

    return finalBox + "\n" +
      "\n" +
      "Next: /clear, then " + nextCmd;
  }

  /**
   * Builds the version display box.
   *
   * @param itemNames  the version name(s)
   * @param version    the version number
   * @param parentInfo the parent version info
   * @param path       the filesystem path
   * @return the formatted version box with next command hint
   */
  private String buildVersionDisplay(List<String> itemNames, String version, String parentInfo, String path)
  {
    List<String> contentItems = new ArrayList<>();
    contentItems.add("v" + version + ": " + String.join(", ", itemNames));
    contentItems.add("");

    if (parentInfo != null && !parentInfo.isEmpty())
      contentItems.add("Parent: " + parentInfo);
    if (path != null && !path.isEmpty())
      contentItems.add("Path: " + path);

    String header = "✅ Version Created";
    DisplayUtils display = scope.getDisplayUtils();
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), BOX_MIN_WIDTH,
      DisplayUtils.HORIZONTAL_LINE + " ");

    return finalBox + "\n" +
      "\n" +
      "Next: /clear, then /cat:add (to add issues)";
  }

  /**
   * CLI entry point for generating add-completion boxes.
   * <p>
   * Accepts the same arguments as {@code render-add-complete.sh}:
   * {@code --type issue|version --name NAME --version VER [--issue-type TYPE]
   * [--dependencies DEPENDENCIES] [--version-type TYPE] [--parent INFO] [--path PATH]}
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    String itemTypeStr = "";
    String nameStr = "";
    String version = "";
    String issueTypeStr = "Feature";
    String dependenciesStr = "";
    String parentInfo = "";
    String itemPath = "";

    for (int i = 0; i + 1 < args.length; i += 2)
    {
      switch (args[i])
      {
        case "--type" -> itemTypeStr = args[i + 1];
        case "--name" -> nameStr = args[i + 1];
        case "--version" -> version = args[i + 1];
        case "--issue-type" -> issueTypeStr = args[i + 1];
        case "--dependencies" -> dependenciesStr = args[i + 1];
        case "--parent" -> parentInfo = args[i + 1];
        case "--path" -> itemPath = args[i + 1];
        default ->
        {
          System.err.println("Error: unknown argument: " + args[i]);
          System.exit(1);
        }
      }
    }

    if (itemTypeStr.isEmpty())
    {
      System.err.println("Error: --type required (issue or version)");
      System.exit(1);
    }

    ItemType itemType;
    switch (itemTypeStr.toLowerCase(Locale.ROOT))
    {
      case "issue" -> itemType = ItemType.ISSUE;
      case "version" -> itemType = ItemType.VERSION;
      default ->
      {
        System.err.println("Error: --type must be 'issue' or 'version', got: " + itemTypeStr);
        System.exit(1);
        return;
      }
    }

    if (nameStr.isEmpty())
    {
      System.err.println("Error: --name is required");
      System.exit(1);
    }
    if (version.isEmpty())
    {
      System.err.println("Error: --version is required");
      System.exit(1);
    }

    List<String> itemNames = new ArrayList<>();
    for (String part : nameStr.split(","))
    {
      String stripped = part.strip();
      if (!stripped.isEmpty())
        itemNames.add(stripped);
    }

    IssueType issueType;
    switch (issueTypeStr.toLowerCase(Locale.ROOT))
    {
      case "bugfix" -> issueType = IssueType.BUGFIX;
      default -> issueType = IssueType.FEATURE;
    }

    List<String> dependencies;
    if (dependenciesStr.isEmpty())
      dependencies = List.of();
    else
    {
      String[] parts = dependenciesStr.split(",");
      dependencies = new ArrayList<>();
      for (String part : parts)
      {
        String stripped = part.strip();
        if (!stripped.isEmpty())
          dependencies.add(stripped);
      }
    }

    try (JvmScope scope = new MainJvmScope())
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result = output.getOutput(itemType, itemNames, version, issueType, dependencies,
        parentInfo, itemPath);
      System.out.println(result);
    }
    catch (RuntimeException e)
    {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }
}

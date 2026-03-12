/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for the /cat:add skill. Generates planning data JSON (version status, summaries,
 * existing issues) when invoked with no arguments, and box displays for issue/version creation completion
 * when invoked with CLI arguments.
 */
public final class GetAddOutput implements SkillOutput
{
  /**
   * The minimum width of the display box.
   */
  private static final int BOX_MIN_WIDTH = 40;
  private static final String BRANCH_STRATEGY = "feature";
  private static final String BRANCH_PATTERN = "v{version}/{issue-name}";
  private static final int SUMMARY_MAX_LENGTH = 120;
  private static final Pattern STATUS_PATTERN = Pattern.compile("^- \\*\\*Status:\\*\\*\\s*(.+)$",
    Pattern.MULTILINE);
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
   * Generates planning data for the /cat:add skill. Returns raw JSON containing
   * version data (status, summary, existing issues) used by first-use.md.
   * <p>
   * The JSON is wrapped in {@code <output type="add">} tags by GetOutput (the centralized dispatcher).
   * <p>
   * Parses {@code --project-dir PATH} from {@code args} if present; otherwise falls back to
   * {@code scope.getClaudeProjectDir()}.
   *
   * @param args the arguments from the preprocessor directive
   * @return the generated output as raw JSON (output tag wrapping happens in GetOutput)
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
    return buildPlanningDataJson(projectDir);
  }

  /**
   * Builds the planning data JSON by scanning the planning directory structure.
   *
   * @param projectDir the project directory to scan
   * @return the planning data JSON (without output tag wrapping; GetOutput handles the wrapper)
   * @throws IOException if an I/O error occurs
   */
  private String buildPlanningDataJson(Path projectDir) throws IOException
  {
    Path issuesDir = projectDir.resolve(Config.CAT_DIR_NAME).resolve("issues");
    if (!Files.isDirectory(issuesDir))
    {
      ObjectNode root = scope.getJsonMapper().createObjectNode();
      root.put("planning_valid", false);
      root.put("error_message",
        "Planning structure not found: " + Config.CAT_DIR_NAME + "/issues. Run /cat:init to initialize.");
      root.put("branch_strategy", "");
      root.put("branch_pattern", "");
      root.set("versions", scope.getJsonMapper().createArrayNode());
      return scope.getJsonMapper().writeValueAsString(root);
    }

    List<VersionData> versions = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(issuesDir, 2))
    {
      List<Path> versionDirs = stream.
        filter(Files::isDirectory).
        filter(p ->
        {
          Path rel = issuesDir.relativize(p);
          return rel.getNameCount() == 2 &&
            rel.getName(0).toString().matches("v\\d+") &&
            rel.getName(1).toString().matches("v\\d+\\.\\d+");
        }).
        sorted().
        toList();

      for (Path versionDir : versionDirs)
      {
        VersionData vd = readVersionData(versionDir);
        if (!vd.status().equals("closed"))
          versions.add(vd);
      }
    }

    ObjectNode root = scope.getJsonMapper().createObjectNode();
    root.put("planning_valid", true);
    root.put("error_message", "");
    root.put("branch_strategy", BRANCH_STRATEGY);
    root.put("branch_pattern", BRANCH_PATTERN);

    ArrayNode versionsArray = scope.getJsonMapper().createArrayNode();
    for (VersionData vd : versions)
    {
      ObjectNode versionNode = scope.getJsonMapper().createObjectNode();
      versionNode.put("version", vd.version());
      versionNode.put("status", vd.status());
      versionNode.put("summary", vd.summary());
      ArrayNode issuesArray = scope.getJsonMapper().createArrayNode();
      for (String issue : vd.existingIssues())
        issuesArray.add(issue);
      versionNode.set("existing_issues", issuesArray);
      versionNode.put("issue_count", vd.existingIssues().size());
      versionsArray.add(versionNode);
    }
    root.set("versions", versionsArray);

    return scope.getJsonMapper().writeValueAsString(root);
  }

  /**
   * Reads version data from a version directory.
   *
   * @param versionDir the version directory (e.g., {@code .cat/issues/v2/v2.1})
   * @return the version data; returns a version with status {@code "closed"} if STATE.md is missing
   * @throws IOException if an I/O error occurs
   */
  private VersionData readVersionData(Path versionDir) throws IOException
  {
    String version = versionDir.getFileName().toString().substring(1);

    Path stateMd = versionDir.resolve("STATE.md");
    if (!Files.isRegularFile(stateMd))
      return new VersionData(version, "closed", "", List.of());

    String stateContent = Files.readString(stateMd);
    String status = parseStatus(stateContent);

    Path planMd = versionDir.resolve("PLAN.md");
    String summary = "";
    if (Files.isRegularFile(planMd))
    {
      String planContent = Files.readString(planMd);
      summary = parseGoalSummary(planContent);
    }

    try (Stream<Path> entries = Files.list(versionDir))
    {
      List<String> existingIssues = entries.
        filter(Files::isDirectory).
        map(p -> p.getFileName().toString()).
        filter(name -> !name.equals("STATE.md") && !name.equals("PLAN.md") &&
          !name.equals("CHANGELOG.md")).
        sorted().
        toList();
      return new VersionData(version, status, summary, existingIssues);
    }
  }

  /**
   * Parses the status value from STATE.md content.
   *
   * @param content the STATE.md file content
   * @return the status string, or {@code "open"} if not found
   */
  private String parseStatus(String content)
  {
    java.util.regex.Matcher matcher = STATUS_PATTERN.matcher(content);
    if (matcher.find())
      return matcher.group(1).strip();
    return "open";
  }

  /**
   * Parses the first non-blank line after the {@code ## Goal} heading in PLAN.md content.
   *
   * @param content the PLAN.md file content
   * @return the summary string (truncated to 120 characters), or {@code ""} if not found
   */
  private String parseGoalSummary(String content)
  {
    String[] lines = content.split("\n", -1);
    boolean inGoal = false;
    for (String line : lines)
    {
      if (!inGoal)
      {
        if (line.strip().equals("## Goal"))
          inGoal = true;
        continue;
      }
      String trimmed = line.strip();
      if (!trimmed.isEmpty())
      {
        if (trimmed.length() > SUMMARY_MAX_LENGTH)
          return trimmed.substring(0, SUMMARY_MAX_LENGTH);
        return trimmed;
      }
    }
    return "";
  }

  /**
   * Version data record holding the parsed state of a minor version directory.
   *
   * @param version        the version string (e.g., {@code "2.1"})
   * @param status         the version status (e.g., {@code "in-progress"}, {@code "closed"})
   * @param summary        the first non-blank line from the Goal section of PLAN.md
   * @param existingIssues the bare names of issue directories under this version
   */
  private record VersionData(String version, String status, String summary,
    List<String> existingIssues)
  {
    private VersionData
    {
      requireThat(version, "version").isNotNull();
      requireThat(status, "status").isNotNull();
      requireThat(summary, "summary").isNotNull();
      requireThat(existingIssues, "existingIssues").isNotNull();
    }
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
   * CLI entry point for generating add-completion boxes or planning data.
   * <p>
   * When invoked with no arguments or with {@code --project-dir}, generates planning data JSON.
   * When invoked with {@code --type}, generates add-completion box displays.
   * <p>
   * Accepts the same arguments as {@code render-add-complete.sh}:
   * {@code --type issue|version --name NAME --version VER [--issue-type TYPE]
   * [--dependencies DEPENDENCIES] [--version-type TYPE] [--parent INFO] [--path PATH]}
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    // If no CLI args or first arg is --project-dir, produce planning data
    if (args.length == 0 || args[0].equals("--project-dir"))
    {
      try (JvmScope scope = new MainJvmScope())
      {
        GetAddOutput output = new GetAddOutput(scope);
        String result = output.getOutput(args);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.err.println("Error: " + e.getMessage());
        System.exit(1);
      }
      return;
    }

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

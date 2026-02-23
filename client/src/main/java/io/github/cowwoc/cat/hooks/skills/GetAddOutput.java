/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output generator for /cat:add skill.
 *
 * Generates box displays for issue and version creation completion.
 */
public final class GetAddOutput
{
  private static final String USAGE = """
    Usage:
      get-add-output issue <name> <version> <issue-type> <dependencies>
      get-add-output version <name> <version> <parent> <path>

    Issue arguments:
      name        issue name (comma-separated for multiple issues)
      version     version string (e.g., "2.0")
      issue-type  issue type (Feature, Bugfix, etc.)
      dependencies  comma-separated dependencies (empty string for none)

    Version arguments:
      name        version name/title
      version     version string (e.g., "2.1")
      parent      parent version info (empty string for none)
      path        filesystem path to the created version""";

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetAddOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetAddOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generate output display for add completion.
   *
   * @param itemType the type of item to create
   * @param itemNames the names of the created items (one or more)
   * @param version the version number
   * @param issueType the issue type (for issues only, may be null to default to FEATURE)
   * @param dependencies the issue dependencies (for issues only, empty list if none)
   * @param parentInfo the parent version info (for versions only, may be empty)
   * @param path the filesystem path to the created item (for versions only, may be empty)
   * @return the formatted output
   * @throws NullPointerException if {@code itemType}, {@code itemNames}, {@code version}, or {@code dependencies}
   *   are null
   * @throws IllegalArgumentException if {@code itemNames} is empty or {@code version} is blank
   */
  public String getOutput(ItemType itemType, List<String> itemNames, String version,
                          TaskType issueType, List<String> dependencies,
                          String parentInfo, String path)
  {
    requireThat(itemType, "itemType").isNotNull();
    requireThat(itemNames, "itemNames").isNotEmpty();
    requireThat(version, "version").isNotBlank();
    requireThat(dependencies, "dependencies").isNotNull();

    switch (itemType)
    {
      case ISSUE ->
      {
        return buildIssueDisplay(itemNames, version, issueType, dependencies);
      }
      case VERSION ->
      {
        requireThat(itemNames, "itemNames").size().isEqualTo(1);
        return buildVersionDisplay(itemNames.get(0), version, parentInfo, path);
      }
    }
    throw new AssertionError("Unexpected itemType: " + itemType);
  }

  /**
   * Builds the issue display box.
   * <p>
   * When {@code itemNames} contains more than one entry, issues are listed with numbered entries.
   *
   * @param itemNames the issue names (one or more)
   * @param version the version number
   * @param issueType the issue type
   * @param dependencies the issue dependencies
   * @return the formatted issue box with next command hint
   */
  private String buildIssueDisplay(List<String> itemNames, String version, TaskType issueType,
                                   List<String> dependencies)
  {
    TaskType effectiveIssueType = issueType;
    if (effectiveIssueType == null)
      effectiveIssueType = TaskType.FEATURE;

    String issueTypeDisplay = effectiveIssueType.name().charAt(0) +
                             effectiveIssueType.name().substring(1).toLowerCase(Locale.ROOT);

    String depsStr;
    if (dependencies.isEmpty())
      depsStr = "None";
    else
      depsStr = String.join(", ", dependencies);

    DisplayUtils display = scope.getDisplayUtils();
    String header;
    List<String> contentItems;
    String nextCmd;

    if (itemNames.size() > 1)
    {
      contentItems = new ArrayList<>();
      int index = 1;
      for (String name : itemNames)
      {
        contentItems.add(index + ". " + name);
        ++index;
      }
      contentItems.add("");
      contentItems.add("Version: " + version);
      contentItems.add("Type: " + issueTypeDisplay);
      contentItems.add("Dependencies: " + depsStr);
      header = "✅ Issues Created";
      nextCmd = "/cat:work " + version + "-" + itemNames.get(0);
    }
    else
    {
      String itemName = itemNames.get(0);
      contentItems = List.of(
        itemName,
        "",
        "Version: " + version,
        "Type: " + issueTypeDisplay,
        "Dependencies: " + depsStr);
      header = "✅ Issue Created";
      nextCmd = "/cat:work " + version + "-" + itemName;
    }

    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), 40, DisplayUtils.HORIZONTAL_LINE + " ");

    return finalBox + "\n" +
           "\n" +
           "Next: /clear, then " + nextCmd;
  }

  /**
   * Builds the version display box.
   *
   * @param itemName the version name
   * @param version the version number
   * @param parentInfo the parent version info
   * @param path the filesystem path
   * @return the formatted version box with next command hint
   */
  private String buildVersionDisplay(String itemName, String version, String parentInfo, String path)
  {
    List<String> contentItems = new ArrayList<>();
    contentItems.add("v" + version + ": " + itemName);
    contentItems.add("");

    if (parentInfo != null && !parentInfo.isEmpty())
      contentItems.add("Parent: " + parentInfo);
    if (path != null && !path.isEmpty())
      contentItems.add("Path: " + path);

    String header = "✅ Version Created";
    DisplayUtils display = scope.getDisplayUtils();
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), 40, DisplayUtils.HORIZONTAL_LINE + " ");

    return finalBox + "\n" +
           "\n" +
           "Next: /clear, then /cat:add (to add issues)";
  }

  /**
   * CLI entry point for generating add completion boxes.
   * <p>
   * Usage:
   * <pre>
   *   get-add-output issue &lt;name&gt; &lt;version&gt; &lt;issue-type&gt; &lt;deps&gt;
   *   get-add-output version &lt;name&gt; &lt;version&gt; &lt;parent&gt; &lt;path&gt;
   * </pre>
   * <p>
   * Issue arguments:
   * <ul>
   *   <li>{@code name} the issue name (may be comma-separated for multiple issues)</li>
   *   <li>{@code version} the version string</li>
   *   <li>{@code issue-type} the issue type (Feature, Bugfix, etc.)</li>
   *   <li>{@code dependencies} comma-separated dependencies</li>
   * </ul>
   * Version arguments:
   * <ul>
   *   <li>{@code name} the version name/title</li>
   *   <li>{@code version} the version string</li>
   *   <li>{@code parent} the parent version info</li>
   *   <li>{@code path} the filesystem path</li>
   * </ul>
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args)
  {
    int exitCode = run(args, System.out, System.err);
    if (exitCode != 0)
      System.exit(exitCode);
  }

  /**
   * Runs the get-add-output logic with injectable streams for testability.
   *
   * @param args the command-line arguments
   * @param out the output stream to write results to
   * @param err the error stream to write error messages to
   * @return 0 on success, non-zero on failure
   */
  public static int run(String[] args, PrintStream out, PrintStream err)
  {
    if (args.length < 1)
    {
      err.println(USAGE);
      return 1;
    }

    String type = args[0];
    switch (type)
    {
      case "issue" ->
      {
        if (args.length != 5)
        {
          err.println("Error: issue requires 4 arguments: <name> <version> <issue-type> <dependencies>");
          err.println(USAGE);
          return 1;
        }
      }
      case "version" ->
      {
        if (args.length != 5)
        {
          err.println("Error: version requires 4 arguments: <name> <version> <parent> <path>");
          err.println(USAGE);
          return 1;
        }
      }
      default ->
      {
        err.println("Error: type must be 'issue' or 'version', got: " + type);
        err.println(USAGE);
        return 1;
      }
    }

    String name = args[1];
    String version = args[2];

    try (JvmScope scope = new MainJvmScope())
    {
      GetAddOutput output = new GetAddOutput(scope);
      String result;

      switch (type)
      {
        case "issue" -> result = output.buildIssueOutput(name, version, args[3], args[4]);
        case "version" -> result = output.buildVersionOutput(name, version, args[3], args[4]);
        default -> throw new AssertionError("Unexpected type: " + type);
      }
      out.println(result);
      return 0;
    }
    catch (RuntimeException e)
    {
      Logger log = LoggerFactory.getLogger(GetAddOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
    catch (Exception e)
    {
      err.println("Error: " + e.getMessage());
      return 1;
    }
  }

  /**
   * Builds the output string for an issue creation from CLI arguments.
   *
   * @param name the issue name (may be comma-separated for multiple issues)
   * @param version the version string
   * @param issueType the issue type string (Feature, Bugfix, etc.)
   * @param dependencies the comma-separated dependency string
   * @return the formatted output string
   */
  private String buildIssueOutput(String name, String version, String issueType, String dependencies)
  {
    List<String> nameList = Arrays.stream(name.split(",")).
      map(String::strip).
      filter(s -> !s.isEmpty()).
      toList();
    if (nameList.isEmpty())
      throw new IllegalArgumentException("--name must contain at least one non-blank issue name");
    TaskType taskType = null;
    if (!issueType.isEmpty())
    {
      try
      {
        taskType = TaskType.valueOf(issueType.toUpperCase(Locale.ROOT));
      }
      catch (IllegalArgumentException _)
      {
        taskType = TaskType.FEATURE;
      }
    }
    List<String> dependencyList = new ArrayList<>();
    if (!dependencies.isEmpty())
    {
      for (String dep : dependencies.split(","))
      {
        String trimmed = dep.strip();
        if (!trimmed.isEmpty())
          dependencyList.add(trimmed);
      }
    }
    return getOutput(ItemType.ISSUE, nameList, version, taskType, dependencyList, "", "");
  }

  /**
   * Builds the output string for a version creation from CLI arguments.
   *
   * @param name the version name/title
   * @param version the version string
   * @param parent the parent version info
   * @param path the filesystem path
   * @return the formatted output string
   */
  private String buildVersionOutput(String name, String version, String parent, String path)
  {
    return getOutput(ItemType.VERSION, List.of(name), version, null, List.of(), parent, path);
  }
}

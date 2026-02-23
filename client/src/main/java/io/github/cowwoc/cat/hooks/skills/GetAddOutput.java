/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:add skill.
 *
 * Generates box displays for issue and version creation completion.
 */
public final class GetAddOutput
{
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
   * @param itemName the name of the created item
   * @param version the version number
   * @param issueType the issue type (for issues only, may be null to default to FEATURE)
   * @param dependencies the issue dependencies (for issues only, empty list if none)
   * @param parentInfo the parent version info (for versions only, may be empty)
   * @param path the filesystem path to the created item (for versions only, may be empty)
   * @return the formatted output
   * @throws NullPointerException if itemType, itemName, version, or dependencies is null
   * @throws IllegalArgumentException if itemName or version is blank
   */
  public String getOutput(ItemType itemType, String itemName, String version,
                          TaskType issueType, List<String> dependencies,
                          String parentInfo, String path)
  {
    requireThat(itemType, "itemType").isNotNull();
    requireThat(itemName, "itemName").isNotBlank();
    requireThat(version, "version").isNotBlank();
    requireThat(dependencies, "dependencies").isNotNull();

    if (itemType == ItemType.ISSUE)
      return buildIssueDisplay(itemName, version, issueType, dependencies);
    return buildVersionDisplay(itemName, version, parentInfo, path);
  }

  /**
   * Builds the issue display box.
   *
   * @param itemName the issue name
   * @param version the version number
   * @param issueType the issue type
   * @param dependencies the issue dependencies
   * @return the formatted issue box with next command hint
   */
  private String buildIssueDisplay(String itemName, String version, TaskType issueType, List<String> dependencies)
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

    List<String> contentItems = List.of(
      itemName,
      "",
      "Version: " + version,
      "Type: " + issueTypeDisplay,
      "Dependencies: " + depsStr);

    String header = "✅ Issue Created";
    DisplayUtils display = scope.getDisplayUtils();
    String finalBox = display.buildHeaderBox(header, contentItems, List.of(), 40, DisplayUtils.HORIZONTAL_LINE + " ");

    String nextCmd = "/cat:work " + version + "-" + itemName;

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
}

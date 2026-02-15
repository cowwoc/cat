/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates STATE.md files against the standardized schema.
 * <p>
 * Enforces mandatory keys, value formats, and prevents non-standard keys.
 * This ensures all STATE.md files follow the same structure across the project.
 */
public final class StateSchemaValidator implements FileWriteHandler
{
  private static final Pattern STATE_MD_PATTERN =
    Pattern.compile("\\.claude/cat/issues/v\\d+/v\\d+\\.\\d+/[^/]+/STATE\\.md$");
  private static final Pattern KEY_VALUE_PATTERN =
    Pattern.compile("^- \\*\\*([^:]+):\\*\\* (.+)$", Pattern.MULTILINE);
  private static final Pattern PROGRESS_FORMAT = Pattern.compile("^(\\d+)%$");
  private static final Pattern DATE_FORMAT = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final Pattern ISSUE_SLUG_FORMAT = Pattern.compile("^[a-z0-9][a-z0-9.-]*$");
  private static final Set<String> VALID_STATUSES = Set.of("open", "in-progress", "closed");
  private static final Set<String> VALID_RESOLUTION_PREFIXES =
    Set.of("implemented", "duplicate", "obsolete", "won't-fix", "not-applicable");
  private static final Set<String> MANDATORY_KEYS =
    Set.of("Status", "Progress", "Dependencies", "Blocks", "Last Updated");
  private static final Set<String> OPTIONAL_KEYS = Set.of("Resolution", "Parent");
  private static final Set<String> ALL_VALID_KEYS;

  static
  {
    Set<String> allKeys = new HashSet<>(MANDATORY_KEYS);
    allKeys.addAll(OPTIONAL_KEYS);
    ALL_VALID_KEYS = Set.copyOf(allKeys);
  }

  /**
   * Creates a new StateSchemaValidator instance.
   */
  public StateSchemaValidator()
  {
  }

  /**
   * Check if the write should be blocked due to STATE.md schema violations.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if {@code toolInput} or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    String filePath = getStringOrDefault(toolInput, "file_path", "");

    if (filePath.isEmpty())
      return FileWriteHandler.Result.allow();

    if (!STATE_MD_PATTERN.matcher(filePath).find())
      return FileWriteHandler.Result.allow();

    String content = getStringOrDefault(toolInput, "content", "");

    if (content.isEmpty())
      return FileWriteHandler.Result.allow();

    Map<String, String> fields = parseFields(content);
    return validateSchema(fields);
  }

  /**
   * Parse key-value pairs from STATE.md markdown bullet format.
   * <p>
   * Lines not matching the pattern {@code - **Key:** Value} are ignored.
   *
   * @param content the STATE.md content (may be empty)
   * @return map of field names to values; empty if no matching key-value pairs found
   */
  private Map<String, String> parseFields(String content)
  {
    Map<String, String> fields = new HashMap<>();
    Matcher matcher = KEY_VALUE_PATTERN.matcher(content);
    while (matcher.find())
    {
      String key = matcher.group(1).strip();
      String value = matcher.group(2).strip();
      fields.put(key, value);
    }
    return fields;
  }

  /**
   * Validate the parsed fields against the STATE.md schema.
   *
   * @param fields the parsed fields
   * @return validation result
   */
  private FileWriteHandler.Result validateSchema(Map<String, String> fields)
  {
    FileWriteHandler.Result result = validateMandatoryKeys(fields);
    if (result.blocked())
      return result;

    result = validateNoNonStandardKeys(fields);
    if (result.blocked())
      return result;

    String status = fields.get("Status");
    result = validateStatus(status);
    if (result.blocked())
      return result;

    String progress = fields.get("Progress");
    result = validateProgress(progress);
    if (result.blocked())
      return result;

    String dependencies = fields.get("Dependencies");
    if (dependencies != null)
    {
      result = validateListFormat(dependencies, "Dependencies");
      if (result.blocked())
        return result;
    }

    String blocks = fields.get("Blocks");
    if (blocks != null)
    {
      result = validateListFormat(blocks, "Blocks");
      if (result.blocked())
        return result;
    }

    String lastUpdated = fields.get("Last Updated");
    result = validateLastUpdated(lastUpdated);
    if (result.blocked())
      return result;

    result = validateClosedResolution(status, fields);
    if (result.blocked())
      return result;

    String parent = fields.get("Parent");
    result = validateParent(parent);
    if (result.blocked())
      return result;

    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that all mandatory keys are present.
   *
   * @param fields the parsed fields
   * @return validation result
   */
  private FileWriteHandler.Result validateMandatoryKeys(Map<String, String> fields)
  {
    for (String key : MANDATORY_KEYS)
    {
      if (!fields.containsKey(key))
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Missing mandatory key '" + key + "'.\n" +
          "\n" +
          "Mandatory keys: Status, Progress, Dependencies, Blocks, Last Updated\n" +
          "Optional keys: Resolution (required for closed issues), Parent");
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that no non-standard keys are present.
   *
   * @param fields the parsed fields
   * @return validation result
   */
  private FileWriteHandler.Result validateNoNonStandardKeys(Map<String, String> fields)
  {
    for (String key : fields.keySet())
    {
      if (!ALL_VALID_KEYS.contains(key))
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Non-standard key '" + key + "'.\n" +
          "\n" +
          "Only these keys are allowed:\n" +
          "  Mandatory: Status, Progress, Dependencies, Blocks, Last Updated\n" +
          "  Optional: Resolution, Parent");
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the Status field value.
   *
   * @param status the status value (may be null)
   * @return validation result
   */
  private FileWriteHandler.Result validateStatus(String status)
  {
    if (status != null && !VALID_STATUSES.contains(status))
    {
      return FileWriteHandler.Result.block(
        "STATE.md schema violation: Invalid Status value '" + status + "'.\n" +
        "\n" +
        "Status must be one of: open, in-progress, closed");
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the Progress field format and value range.
   *
   * @param progress the progress value (may be null)
   * @return validation result
   */
  private FileWriteHandler.Result validateProgress(String progress)
  {
    if (progress != null)
    {
      Matcher progressMatcher = PROGRESS_FORMAT.matcher(progress);
      if (!progressMatcher.matches())
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Invalid Progress format '" + progress + "'.\n" +
          "\n" +
          "Progress must be an integer 0-100 followed by % (e.g., '50%')");
      }
      int progressValue = Integer.parseInt(progressMatcher.group(1));
      if (progressValue < 0 || progressValue > 100)
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Progress value out of range: " + progressValue + ".\n" +
          "\n" +
          "Progress must be between 0 and 100");
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the Last Updated field format.
   *
   * @param lastUpdated the last updated value (may be null)
   * @return validation result
   */
  private FileWriteHandler.Result validateLastUpdated(String lastUpdated)
  {
    if (lastUpdated != null && !DATE_FORMAT.matcher(lastUpdated).matches())
    {
      return FileWriteHandler.Result.block(
        "STATE.md schema violation: Invalid 'Last Updated' format '" + lastUpdated + "'.\n" +
        "\n" +
        "Last Updated must be in YYYY-MM-DD format (e.g., '2026-02-12')");
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that closed status has a valid Resolution field.
   *
   * @param status the status value (may be null)
   * @param fields the parsed fields
   * @return validation result
   */
  private FileWriteHandler.Result validateClosedResolution(String status, Map<String, String> fields)
  {
    if (status != null && status.equals("closed"))
    {
      String resolution = fields.get("Resolution");
      if (resolution == null || resolution.isEmpty())
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Resolution is required when Status is 'closed'.\n" +
          "\n" +
          "Resolution must be one of:\n" +
          "  - implemented\n" +
          "  - duplicate (<issue-id>)\n" +
          "  - obsolete (<explanation>)\n" +
          "  - won't-fix (<explanation>)\n" +
          "  - not-applicable (<explanation>)");
      }

      boolean validResolution = false;
      for (String prefix : VALID_RESOLUTION_PREFIXES)
      {
        if (resolution.equals(prefix) || resolution.startsWith(prefix + " "))
        {
          validResolution = true;
          break;
        }
      }

      if (!validResolution)
      {
        return FileWriteHandler.Result.block(
          "STATE.md schema violation: Invalid Resolution value '" + resolution + "'.\n" +
          "\n" +
          "Resolution must start with one of: implemented, duplicate, obsolete, won't-fix, not-applicable");
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the Parent field format.
   *
   * @param parent the parent value (may be null)
   * @return validation result
   */
  private FileWriteHandler.Result validateParent(String parent)
  {
    if (parent != null && !parent.isEmpty() && !ISSUE_SLUG_FORMAT.matcher(parent).matches())
    {
      return FileWriteHandler.Result.block(
        "STATE.md schema violation: Invalid Parent format '" + parent + "'.\n" +
        "\n" +
        "Parent must be a valid issue slug (lowercase letters, numbers, hyphens only)");
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate list format for Dependencies and Blocks fields.
   *
   * @param value the field value
   * @param fieldName the field name for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateListFormat(String value, String fieldName)
  {
    if (value.equals("[]"))
      return FileWriteHandler.Result.allow();

    if (!value.startsWith("[") || !value.endsWith("]"))
    {
      return FileWriteHandler.Result.block(
        "STATE.md schema violation: Invalid " + fieldName + " format '" + value + "'.\n" +
        "\n" +
        fieldName + " must be [] or [comma-separated-values]");
    }

    return FileWriteHandler.Result.allow();
  }
}

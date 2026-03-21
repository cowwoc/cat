/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.IssueStatus;
import io.github.cowwoc.cat.hooks.JvmScope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Validates index.json files against the standardized schema.
 * <p>
 * Enforces mandatory fields, value formats, and prevents non-standard fields.
 * This ensures all index.json files follow the same structure across the project.
 */
public final class StateSchemaValidator implements FileWriteHandler
{
  private static final Pattern INDEX_JSON_PATTERN =
    Pattern.compile(Pattern.quote(Config.CAT_DIR_NAME) + "/issues/v\\d+/v\\d+\\.\\d+/[^/]+/index\\.json$");
  private static final Pattern ISSUE_SLUG_FORMAT = Pattern.compile("^[a-z0-9][a-z0-9.-]*$");
  private static final Set<String> VALID_RESOLUTION_PREFIXES =
    Set.of("implemented", "duplicate", "obsolete", "won't-fix", "not-applicable");
  private static final Set<String> MANDATORY_FIELDS = Set.of("status");
  private static final Set<String> OPTIONAL_FIELDS =
    Set.of("resolution", "targetBranch", "dependencies", "blocks", "parent", "decomposedInto");
  private static final Set<String> ALL_VALID_FIELDS;
  private static final String MANDATORY_FIELDS_LIST;
  private static final String OPTIONAL_FIELDS_LIST;

  static
  {
    Set<String> allFields = new HashSet<>(MANDATORY_FIELDS);
    allFields.addAll(OPTIONAL_FIELDS);
    ALL_VALID_FIELDS = Set.copyOf(allFields);
    MANDATORY_FIELDS_LIST = String.join(", ", new TreeSet<>(MANDATORY_FIELDS));
    OPTIONAL_FIELDS_LIST = String.join(", ", new TreeSet<>(OPTIONAL_FIELDS));
  }

  private final JsonMapper mapper;

  /**
   * Creates a new StateSchemaValidator instance.
   *
   * @param scope the JVM scope providing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public StateSchemaValidator(JvmScope scope)
  {
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Check if the write should be blocked due to index.json schema violations.
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

    if (!INDEX_JSON_PATTERN.matcher(filePath).find())
      return FileWriteHandler.Result.allow();

    String content = getStringOrDefault(toolInput, "content", "");

    if (content.isEmpty())
    {
      // "content" is only present for Write operations. For Edit operations, the tool provides
      // "old_string" and "new_string" instead. Reconstruct the expected post-edit file content by
      // applying the replacement to the on-disk file so we can validate the result.
      String newString = getStringOrDefault(toolInput, "new_string", "");
      if (newString.isEmpty())
        return FileWriteHandler.Result.allow();
      String oldString = getStringOrDefault(toolInput, "old_string", "");
      EditResult editResult = applyEdit(filePath, oldString, newString);
      if (editResult.exception != null)
      {
        // File could not be read. Block the edit so the user knows validation failed due to
        // I/O failure rather than allowing potentially invalid content through silently.
        return FileWriteHandler.Result.block("""
          Edit validation failed: Could not read %s to validate post-edit content.
          This may indicate a file system issue or permission problem.
          Error: %s: %s

          Verify the file path is correct and that you have read access. If the file was created \
          in this session, ensure the Write tool completed successfully before using the Edit tool.""".
          formatted(filePath, editResult.exception.getClass().getSimpleName(),
            editResult.exception.getMessage()));
      }
      content = editResult.content;
      if (content.isEmpty())
        return FileWriteHandler.Result.block("""
          Edit rejected: old_string not found in %s.
          The file on disk does not contain the text being replaced.

          Read the current file content first to get the exact text, then retry the Edit with \
          an old_string that matches the current file exactly.""".formatted(filePath));
    }

    return validateJson(content, filePath);
  }

  /**
   * Parse and validate index.json content against the schema.
   *
   * @param content  the JSON content to validate
   * @param filePath the file path, used in error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateJson(String content, String filePath)
  {
    ObjectNode root;
    try
    {
      JsonNode parsed = mapper.readTree(content);
      if (!parsed.isObject())
      {
        return FileWriteHandler.Result.block(
          "index.json schema violation in " + filePath + ": root element must be a JSON object.\n" +
          "\n" +
          "Mandatory fields: " + MANDATORY_FIELDS_LIST + "\n" +
          "Optional fields: " + OPTIONAL_FIELDS_LIST);
      }
      root = (ObjectNode) parsed;
    }
    catch (JacksonException e)
    {
      return FileWriteHandler.Result.block(
        "index.json schema violation in " + filePath + ": invalid JSON.\n" +
        "Parse error: " + e.getMessage());
    }

    FileWriteHandler.Result result = validateMandatoryFields(root, filePath);
    if (result.blocked())
      return result;

    result = validateNoNonStandardFields(root, filePath);
    if (result.blocked())
      return result;

    String status = root.path("status").asString();
    result = validateStatus(status, filePath);
    if (result.blocked())
      return result;

    result = validateClosedResolution(status, root, filePath);
    if (result.blocked())
      return result;

    JsonNode parentNode = root.get("parent");
    if (parentNode != null)
    {
      result = validateParent(parentNode, filePath);
      if (result.blocked())
        return result;
    }

    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that all mandatory fields are present.
   *
   * @param root     the parsed JSON root object
   * @param filePath the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateMandatoryFields(ObjectNode root, String filePath)
  {
    for (String field : MANDATORY_FIELDS)
    {
      if (!root.has(field))
      {
        return FileWriteHandler.Result.block("""
          index.json schema violation in %s: Missing mandatory field '%s'.

          Mandatory fields: %s
          Optional fields: %s (resolution required for closed issues)

          Add the missing field to the index.json content: "%s": "<value>"\
          """.formatted(filePath, field, MANDATORY_FIELDS_LIST, OPTIONAL_FIELDS_LIST, field));
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that no non-standard fields are present.
   *
   * @param root     the parsed JSON root object
   * @param filePath the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateNoNonStandardFields(ObjectNode root, String filePath)
  {
    for (Map.Entry<String, JsonNode> entry : root.properties())
    {
      String fieldName = entry.getKey();
      if (!ALL_VALID_FIELDS.contains(fieldName))
      {
        return FileWriteHandler.Result.block("""
          index.json schema violation in %s: Non-standard field '%s'.

          Only these fields are allowed:
            Mandatory: %s
            Optional: %s

          Remove the '%s' field from the index.json content or rename it to a valid field.""".
          formatted(filePath, fieldName, MANDATORY_FIELDS_LIST, OPTIONAL_FIELDS_LIST, fieldName));
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the status field value.
   *
   * @param status   the status value
   * @param filePath the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateStatus(String status, String filePath)
  {
    if (status != null && IssueStatus.fromString(status) == null)
    {
      return FileWriteHandler.Result.block(
        "index.json schema violation in " + filePath + ": Invalid status value '" + status + "'.\n" +
        "\n" +
        "status must be one of: " + IssueStatus.asCommaSeparated() + "\n" +
        "\n" +
        "If migrating from older versions, run: plugin/migrations/2.1.sh");
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate that closed status has a valid resolution field.
   *
   * @param status   the status value
   * @param root     the parsed JSON root object
   * @param filePath the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateClosedResolution(String status, ObjectNode root, String filePath)
  {
    if (IssueStatus.fromString(status) == IssueStatus.CLOSED)
    {
      JsonNode resolutionNode = root.get("resolution");
      String resolution = null;
      if (resolutionNode != null && resolutionNode.isString())
        resolution = resolutionNode.asString();

      if (resolution == null || resolution.isBlank())
      {
        return FileWriteHandler.Result.block(
          "index.json schema violation in " + filePath + ": resolution is required when status is 'closed'.\n" +
          "\n" +
          "resolution must be one of:\n" +
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
          "index.json schema violation in " + filePath + ": Invalid resolution value '" + resolution + "'.\n" +
          "\n" +
          "resolution must start with one of: implemented, duplicate, obsolete, won't-fix, not-applicable");
      }
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Validate the parent field format.
   *
   * @param parentNode the parent JSON node
   * @param filePath   the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateParent(JsonNode parentNode, String filePath)
  {
    if (!parentNode.isString())
    {
      return FileWriteHandler.Result.block(
        "index.json schema violation in " + filePath + ": parent must be a string.");
    }
    String parent = parentNode.asString();
    if (!parent.isEmpty() && !ISSUE_SLUG_FORMAT.matcher(parent).matches())
    {
      return FileWriteHandler.Result.block("""
        index.json schema violation in %s: Invalid parent format '%s'.

        parent must be a valid issue slug (lowercase letters, numbers, hyphens only).

        Example: "parent": "my-parent-issue"
        Fix: Change '%s' to use only lowercase letters, digits, and hyphens.""".
        formatted(filePath, parent, parent));
    }
    return FileWriteHandler.Result.allow();
  }

  /**
   * Container for applyEdit result with optional exception.
   * <p>
   * When content is non-null, the edit was successfully applied. When content is null and exception
   * is non-null, the file could not be read.
   */
  private static class EditResult
  {
    final String content;
    final IOException exception;

    EditResult(String content)
    {
      this.content = content;
      this.exception = null;
    }

    EditResult(IOException exception)
    {
      this.content = null;
      this.exception = exception;
    }
  }

  /**
   * Apply an Edit tool's string replacement to the on-disk file content.
   * <p>
   * Returns an EditResult containing either the post-edit content or an IOException if the file
   * could not be read. The caller is responsible for deciding whether to block (fail-fast) or
   * warn (fail-open).
   *
   * @param filePath  the path to the file on disk
   * @param oldString the substring to replace
   * @param newString the replacement string
   * @return EditResult with post-edit content if successful, or the IOException if file unreadable
   */
  private EditResult applyEdit(String filePath, String oldString, String newString)
  {
    try
    {
      String diskContent = Files.readString(Path.of(filePath), UTF_8);
      // Replace only the first occurrence to match Edit tool semantics
      int index = diskContent.indexOf(oldString);
      if (index == -1)
        return new EditResult(""); // oldString not found - fail-fast (caller blocks)
      String result = diskContent.substring(0, index) + newString +
        diskContent.substring(index + oldString.length());
      return new EditResult(result);
    }
    catch (IOException e)
    {
      return new EditResult(e); // file unreadable - exception returned to caller
    }
  }
}

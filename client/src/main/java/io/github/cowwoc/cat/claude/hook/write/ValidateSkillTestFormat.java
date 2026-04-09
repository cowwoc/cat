/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.write;

import static io.github.cowwoc.cat.claude.hook.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.claude.hook.FileWriteHandler;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates skill test case markdown files on write.
 * <p>
 * Test case files are located at {@code plugin/tests/<path>/<name>/*.md}. Each file must contain
 * YAML frontmatter with the required field {@code category}, and at minimum the sections
 * {@code ## Turn 1} and {@code ## Assertions}. Optional {@code ## System Prompt} and additional
 * {@code ## Turn N} sections (contiguous from 1) are also supported.
 * <p>
 * This hook runs as a PreToolUse handler for Write and Edit operations. Files that do not match the
 * {@code plugin/tests/} path pattern are passed through without validation.
 */
public final class ValidateSkillTestFormat implements FileWriteHandler
{
  private static final Pattern TEST_MD_PATTERN =
    Pattern.compile("(?:^|/)plugin/tests/.+\\.md$");
  private static final Pattern FRONTMATTER_PATTERN =
    Pattern.compile("\\A---\\n(.*?)\\n---\\n?", Pattern.DOTALL);
  private static final Set<String> REQUIRED_FRONTMATTER_FIELDS = Set.of("category");
  private static final Map<String, Pattern> FRONTMATTER_FIELD_PATTERNS;

  static
  {
    FRONTMATTER_FIELD_PATTERNS = new LinkedHashMap<>();
    for (String field : REQUIRED_FRONTMATTER_FIELDS)
      FRONTMATTER_FIELD_PATTERNS.put(field, Pattern.compile("^" + Pattern.quote(field) + ":\\s*(.+)$",
        Pattern.MULTILINE));
  }
  private static final Pattern TURN_SECTION_PATTERN =
    Pattern.compile("^## Turn (\\d+)\\s*$", Pattern.MULTILINE);

  /**
   * Creates a new ValidateSkillTestFormat instance.
   */
  public ValidateSkillTestFormat()
  {
  }

  /**
   * Check if the write should be blocked due to skill test file format violations.
   *
   * @param toolInput the tool input JSON
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the check result
   * @throws NullPointerException     if {@code toolInput} or {@code catAgentId} are null
   * @throws IllegalArgumentException if {@code catAgentId} is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String catAgentId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(catAgentId, "catAgentId").isNotBlank();

    String filePath = getStringOrDefault(toolInput, "file_path", "");
    if (filePath.isEmpty())
      return FileWriteHandler.Result.allow();

    if (!TEST_MD_PATTERN.matcher(filePath).find())
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

    return validateContent(content, filePath);
  }

  /**
   * Validate the content of a skill test case markdown file.
   *
   * @param content  the markdown content to validate
   * @param filePath the file path, used in error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateContent(String content, String filePath)
  {
    Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
    if (!frontmatterMatcher.find())
    {
      return FileWriteHandler.Result.block("""
        Skill test format violation in %s: missing YAML frontmatter.

        Test case files must begin with a YAML frontmatter block containing:
          category: <semantic category, e.g. routing>

        See plugin/concepts/skill-test.md for the complete format specification.""".
        formatted(filePath));
    }

    String frontmatterBody = frontmatterMatcher.group(1);
    FileWriteHandler.Result frontmatterResult = validateFrontmatter(frontmatterBody, filePath);
    if (frontmatterResult.blocked())
      return frontmatterResult;

    return validateSections(content, filePath);
  }

  /**
   * Validate YAML frontmatter fields.
   *
   * @param frontmatterBody the raw YAML frontmatter body (between the {@code ---} delimiters)
   * @param filePath        the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateFrontmatter(String frontmatterBody, String filePath)
  {
    List<String> missingFields = new ArrayList<>();

    for (Map.Entry<String, Pattern> entry : FRONTMATTER_FIELD_PATTERNS.entrySet())
    {
      String field = entry.getKey();
      Matcher matcher = entry.getValue().matcher(frontmatterBody);
      if (!matcher.find())
        missingFields.add(field);
    }

    if (!missingFields.isEmpty())
    {
      return FileWriteHandler.Result.block("""
        Skill test format violation in %s: missing required frontmatter field(s): %s.

        Required frontmatter fields:
          category: <semantic category, e.g. routing>

        See plugin/concepts/skill-test.md for the complete format specification.""".
        formatted(filePath, String.join(", ", missingFields)));
    }

    return FileWriteHandler.Result.allow();
  }

  /**
   * Strips the content inside fenced code blocks, preserving the fence lines themselves.
   * <p>
   * Lines between opening and closing {@code ```} markers are replaced with empty strings so that
   * section-heading patterns ({@code ## Turn N}, {@code ## Assertions}) inside code blocks are not
   * mistakenly detected as actual markdown sections.
   *
   * @param content the raw markdown content
   * @return the content with code block interiors blanked out
   */
  private String stripFencedCodeBlockContent(String content)
  {
    StringBuilder result = new StringBuilder(content.length());
    boolean insideCodeBlock = false;
    for (String line : content.split("\n", -1))
    {
      if (line.startsWith("```"))
      {
        insideCodeBlock = !insideCodeBlock;
        result.append(line);
      }
      else if (insideCodeBlock)
        result.append("");
      else
        result.append(line);
      result.append('\n');
    }
    return result.toString();
  }

  /**
   * Validate that all required markdown sections are present and turn sections are contiguous.
   *
   * @param content  the full markdown content
   * @param filePath the file path for error messages
   * @return validation result
   */
  private FileWriteHandler.Result validateSections(String content, String filePath)
  {
    String strippedContent = stripFencedCodeBlockContent(content);
    if (!strippedContent.contains("## Assertions"))
    {
      return FileWriteHandler.Result.block("""
        Skill test format violation in %s: missing required section '## Assertions'. \
Each test case file must contain at least ## Turn 1 and ## Assertions.

        See plugin/concepts/skill-test.md for the complete format specification.""".
        formatted(filePath));
    }

    if (!strippedContent.contains("## Turn 1"))
    {
      return FileWriteHandler.Result.block("""
        Skill test format violation in %s: missing required section '## Turn 1'. \
Each test case file must contain at least ## Turn 1 and ## Assertions.

        See plugin/concepts/skill-test.md for the complete format specification.""".
        formatted(filePath));
    }

    // Collect all ## Turn N numbers and verify they form a contiguous sequence starting at 1
    Matcher turnMatcher = TURN_SECTION_PATTERN.matcher(strippedContent);
    List<Integer> turnNumbers = new ArrayList<>();
    while (turnMatcher.find())
      turnNumbers.add(Integer.parseInt(turnMatcher.group(1)));
    turnNumbers.sort(Integer::compareTo);

    for (int index = 0; index < turnNumbers.size(); ++index)
    {
      int expected = index + 1;
      if (turnNumbers.get(index) != expected)
      {
        List<Integer> expectedList = new ArrayList<>();
        for (int number = 1; number <= turnNumbers.getLast(); ++number)
          expectedList.add(number);
        return FileWriteHandler.Result.block("""
          Skill test format violation in %s: turn sections are not contiguous: \
found turns %s, expected %s.

          See plugin/concepts/skill-test.md for the complete format specification.""".
          formatted(filePath, turnNumbers, expectedList));
      }
    }

    return FileWriteHandler.Result.allow();
  }

  /**
   * Container for applyEdit result with optional exception.
   * <p>
   * When {@code content} is non-empty, the edit was successfully applied. When {@code content} is
   * empty and {@code exception} is non-null, the file could not be read. When both are empty/null,
   * the old_string was not found in the file.
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
      this.content = "";
      this.exception = exception;
    }
  }

  /**
   * Apply an Edit tool's string replacement to the on-disk file content.
   * <p>
   * Returns an EditResult containing either the post-edit content or an IOException if the file
   * could not be read.
   *
   * @param filePath  the path to the file on disk
   * @param oldString the substring to replace
   * @param newString the replacement string
   * @return EditResult with post-edit content if successful, empty if old_string not found, or
   *   exception if file unreadable
   */
  private EditResult applyEdit(String filePath, String oldString, String newString)
  {
    try
    {
      String diskContent = Files.readString(Path.of(filePath), UTF_8);
      // Replace only the first occurrence to match Edit tool semantics
      int index = diskContent.indexOf(oldString);
      if (index == -1)
        return new EditResult(""); // oldString not found — caller blocks
      String result = diskContent.substring(0, index) + newString +
        diskContent.substring(index + oldString.length());
      return new EditResult(result);
    }
    catch (IOException e)
    {
      return new EditResult(e);
    }
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing conversation log JSONL lines.
 */
public final class ConversationLogUtils
{
  /**
   * File extensions that identify source-code files eligible for giving-up detection.
   */
  private static final Set<String> CODE_EXTENSIONS = Set.of(
    ".java", ".js", ".ts", ".tsx", ".jsx", ".py", ".rb", ".go", ".rs",
    ".c", ".cpp", ".h", ".hpp", ".cs", ".swift", ".kt", ".scala",
    ".sh", ".bash", ".zsh", ".md", ".html", ".css", ".scss", ".sql");

  /**
   * Pattern matching a token ending in a code extension (e.g., {@code Main.java} or
   * {@code /foo/bar.java}). A directory separator is not required.
   */
  private static final Pattern CODE_PATH_PATTERN = buildCodePathPattern();

  private ConversationLogUtils()
  {
  }

  private static Pattern buildCodePathPattern()
  {
    StringBuilder sb = new StringBuilder("\\S+(?:");
    boolean first = true;
    for (String ext : CODE_EXTENSIONS)
    {
      if (!first)
        sb.append('|');
      sb.append(Pattern.quote(ext));
      first = false;
    }
    sb.append(")(?:\\s|$)");
    return Pattern.compile(sb.toString());
  }

  /**
   * Extracts text-only content from an assistant JSONL line.
   * <p>
   * Handles two content formats:
   * <ul>
   *   <li>String content: {@code {"role":"assistant","content":"text"}}</li>
   *   <li>Array content with no {@code tool_use} blocks: only {@code {"type":"text","text":"..."}}
   *       blocks are included; other block types are skipped</li>
   *   <li>Compound array content (containing at least one {@code tool_use} block): returns {@code ""}
   *       because the text in these messages narrates tool invocations rather than reflecting
   *       the agent's analytical reasoning</li>
   * </ul>
   * Also handles the wrapped format where the message is under a {@code "message"} key.
   * <p>
   * <b>Design tradeoff:</b> Compound messages (those containing {@code tool_use} blocks) are
   * suppressed entirely rather than scanning only their text portions. This is intentional: the
   * text in compound messages typically describes the upcoming tool call (e.g., "Let me remove
   * this file") and is not an expression of the agent's intent to give up. Scanning it would
   * cause false positives. The tradeoff is that genuinely problematic text co-located with a
   * tool call is not detected; this is acceptable because giving-up signals in tool-adjacent
   * narration are low-confidence and the false-positive cost is higher than the miss cost.
   *
   * @param jsonlLine the raw JSONL line to parse
   * @param mapper    the JSON mapper to use for parsing
   * @return the extracted text content, or empty string if none found or parse fails
   * @throws NullPointerException if {@code jsonlLine} or {@code mapper} is null
   */
  public static String extractTextContent(String jsonlLine, JsonMapper mapper)
  {
    try
    {
      JsonNode root = mapper.readTree(jsonlLine);
      JsonNode contentNode = root.path("content");
      if (contentNode.isMissingNode())
        contentNode = root.path("message").path("content");
      if (contentNode.isMissingNode())
        return "";

      if (contentNode.isString())
        return contentNode.asString();

      if (contentNode.isArray())
      {
        // Skip compound messages (text + tool_use): the text describes the upcoming tool call,
        // not the agent's analytical reasoning. Scanning it causes false positives in
        // DetectAssistantGivingUp (e.g., "Let me remove X" → CODE_REMOVAL false positive).
        for (JsonNode block : contentNode)
        {
          if ("tool_use".equals(block.path("type").asString()))
            return "";
        }
        // Pure-text message: extract and concatenate all text blocks
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentNode)
        {
          if ("text".equals(block.path("type").asString()))
          {
            String text = block.path("text").asString();
            if (!text.isEmpty())
            {
              if (!sb.isEmpty())
                sb.append(' ');
              sb.append(text);
            }
          }
        }
        return sb.toString();
      }
      return "";
    }
    catch (JacksonException e)
    {
      Logger log = LoggerFactory.getLogger(ConversationLogUtils.class);
      log.debug("Failed to parse JSONL line as JSON: {}", e.getMessage());
      return "";
    }
  }

  /**
   * Extracts turn segments from an assistant JSONL line.
   * <p>
   * Each segment pairs a text block with the file paths of its immediately adjacent tool_use blocks
   * (at i-1 and i+1 in the content array). This enables context-aware giving-up detection: text that
   * narrates a tool call on a non-code file can be suppressed, while text adjacent to code-file
   * operations is still checked.
   * <p>
   * Handles the following cases:
   * <ul>
   *   <li>String content: returns a single {@link TurnSegment} with both paths null</li>
   *   <li>Pure-text array (no tool_use blocks): returns one segment per text block, both paths null</li>
   *   <li>Compound array (contains tool_use blocks): for each text block, extracts file paths from
   *       adjacent tool_use blocks</li>
   * </ul>
   * Also handles the wrapped format where the message is under a {@code "message"} key.
   *
   * @param jsonlLine the raw JSONL line to parse
   * @param mapper    the JSON mapper to use for parsing
   * @return the list of turn segments; empty if parsing fails or no text blocks are found
   * @throws NullPointerException if {@code jsonlLine} or {@code mapper} is null
   */
  public static List<TurnSegment> extractSegments(String jsonlLine, JsonMapper mapper)
  {
    try
    {
      JsonNode root = mapper.readTree(jsonlLine);
      JsonNode contentNode = root.path("content");
      if (contentNode.isMissingNode())
        contentNode = root.path("message").path("content");
      if (contentNode.isMissingNode())
        return List.of();

      if (contentNode.isString())
        return List.of(new TurnSegment(contentNode.asString(), null, null));

      if (!contentNode.isArray())
        return List.of();

      // Build an indexed array of content blocks for adjacency lookup
      List<JsonNode> blocks = new ArrayList<>();
      for (JsonNode block : contentNode)
        blocks.add(block);

      List<TurnSegment> segments = new ArrayList<>();
      int blockCount = blocks.size();
      for (int i = 0; i < blockCount; i = i + 1)
      {
        JsonNode block = blocks.get(i);
        if (!"text".equals(block.path("type").asString()))
          continue;
        String text = block.path("text").asString();
        if (text.isEmpty())
          continue;

        String aboveFilePath = null;
        String belowFilePath = null;

        // Look at the block immediately before this text block
        if (i > 0)
        {
          JsonNode above = blocks.get(i - 1);
          if ("tool_use".equals(above.path("type").asString()))
            aboveFilePath = extractFilePath(above);
        }
        // Look at the block immediately after this text block
        if (i < blocks.size() - 1)
        {
          JsonNode below = blocks.get(i + 1);
          if ("tool_use".equals(below.path("type").asString()))
            belowFilePath = extractFilePath(below);
        }

        segments.add(new TurnSegment(text, aboveFilePath, belowFilePath));
      }
      return segments;
    }
    catch (JacksonException e)
    {
      Logger log = LoggerFactory.getLogger(ConversationLogUtils.class);
      log.debug("Failed to parse JSONL line as JSON: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Extracts the file path from a tool_use block's input.
   * <p>
   * Extraction rules by tool name:
   * <ul>
   *   <li>Edit, Write, Read → {@code input.file_path}</li>
   *   <li>NotebookEdit → {@code input.notebook_path}</li>
   *   <li>Bash → first token in {@code input.command} that ends with a code extension</li>
   * </ul>
   *
   * @param toolUseBlock the tool_use JSON block
   * @return the extracted file path, or {@code null} if none found
   */
  private static String extractFilePath(JsonNode toolUseBlock)
  {
    String toolName = toolUseBlock.path("name").asString("");
    JsonNode input = toolUseBlock.path("input");
    return switch (toolName)
    {
      case "Edit", "Write", "Read" ->
      {
        String fp = input.path("file_path").asString("");
        if (fp.isEmpty())
          yield null;
        yield fp;
      }
      case "NotebookEdit" ->
      {
        String np = input.path("notebook_path").asString("");
        if (np.isEmpty())
          yield null;
        yield np;
      }
      case "Bash" ->
      {
        String command = input.path("command").asString("");
        yield extractCodePathFromCommand(command);
      }
      default -> null;
    };
  }

  /**
   * Extracts the first token in a Bash command that ends with a recognized code extension.
   *
   * @param command the Bash command string
   * @return the matched path token, or {@code null} if none found
   */
  static String extractCodePathFromCommand(String command)
  {
    if (command.isEmpty())
      return null;
    Matcher matcher = CODE_PATH_PATTERN.matcher(command);
    if (matcher.find())
      return matcher.group().strip();
    return null;
  }

  /**
   * Returns {@code true} if the given file path ends with a recognized code extension.
   *
   * @param filePath the file path to check (may be {@code null})
   * @return {@code true} if the file path has a code extension
   */
  static boolean hasCodeExtension(String filePath)
  {
    if (filePath == null || filePath.isEmpty())
      return false;
    for (String ext : CODE_EXTENSIONS)
    {
      if (filePath.endsWith(ext))
        return true;
    }
    return false;
  }
}

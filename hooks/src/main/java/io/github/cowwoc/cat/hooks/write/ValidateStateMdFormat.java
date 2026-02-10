package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Enforce correct STATE.md format by blocking writes that don't match the template.
 * <p>
 * This prevents manual task creation with invalid STATE.md format.
 * Agents should use /cat:add which uses templates with the correct format.
 */
public final class ValidateStateMdFormat implements FileWriteHandler
{
  private static final Pattern STATE_MD_PATTERN =
    Pattern.compile("\\.claude/cat/issues/v\\d+/v\\d+\\.\\d+/[^/]+/STATE\\.md$");
  private static final Pattern STATUS_LINE = Pattern.compile("^- \\*\\*Status:\\*\\*", Pattern.MULTILINE);
  private static final Pattern PROGRESS_LINE = Pattern.compile("^- \\*\\*Progress:\\*\\*", Pattern.MULTILINE);
  private static final Pattern DEPENDENCIES_LINE = Pattern.compile("^- \\*\\*Dependencies:\\*\\*", Pattern.MULTILINE);

  /**
   * Creates a new ValidateStateMdFormat instance.
   */
  public ValidateStateMdFormat()
  {
  }

  /**
   * Check if the write should be blocked due to invalid STATE.md format.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    if (filePath.isEmpty())
      return FileWriteHandler.Result.allow();

    if (!STATE_MD_PATTERN.matcher(filePath).find())
      return FileWriteHandler.Result.allow();

    JsonNode contentNode = toolInput.get("content");
    String content;
    if (contentNode != null)
      content = contentNode.asString();
    else
      content = "";

    if (!STATUS_LINE.matcher(content).find())
    {
      return FileWriteHandler.Result.block(
        "STATE.md format violation: Missing '- **Status:** value' line.\n" +
        "\n" +
        "STATE.md files must use bullet-point format:\n" +
        "  - **Status:** pending\n" +
        "  - **Progress:** 0%\n" +
        "  - **Dependencies:** []\n" +
        "  - **Last Updated:** YYYY-MM-DD\n" +
        "\n" +
        "Use /cat:add to create tasks with correct format, or fix the content to match the template.");
    }

    if (!PROGRESS_LINE.matcher(content).find())
    {
      return FileWriteHandler.Result.block(
        "STATE.md format violation: Missing '- **Progress:** value' line.\n" +
        "\n" +
        "STATE.md files must use bullet-point format. Use /cat:add to create tasks with correct format.");
    }

    if (!DEPENDENCIES_LINE.matcher(content).find())
    {
      return FileWriteHandler.Result.block(
        "STATE.md format violation: Missing '- **Dependencies:** [...]' line.\n" +
        "\n" +
        "STATE.md files must use bullet-point format. Use /cat:add to create tasks with correct format.");
    }

    return FileWriteHandler.Result.allow();
  }
}

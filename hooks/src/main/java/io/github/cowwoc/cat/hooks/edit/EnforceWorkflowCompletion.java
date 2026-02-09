package io.github.cowwoc.cat.hooks.edit;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.EditHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Prevent marking task as closed without completing workflow phases (M217).
 * <p>
 * This handler detects attempts to set task status to "closed" and warns if workflow
 * phases appear incomplete.
 * <p>
 * Completion bias led to skipping stakeholder_review and approval_gate phases.
 */
public final class EnforceWorkflowCompletion implements EditHandler
{
  private static final Pattern STATE_MD_PATTERN =
    Pattern.compile("\\.claude/cat/v[0-9]+/v[0-9]+\\.[0-9]+/[^/]+/STATE\\.md$");
  private static final Pattern STATUS_CLOSED_PATTERN =
    Pattern.compile("[Ss]tatus.*closed");

  /**
   * Creates a new EnforceWorkflowCompletion handler.
   */
  public EnforceWorkflowCompletion()
  {
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    if (!STATE_MD_PATTERN.matcher(filePath).find())
      return Result.allow();

    JsonNode newStringNode = toolInput.get("new_string");
    String newString;
    if (newStringNode != null)
      newString = newStringNode.asString();
    else
      newString = "";

    if (!STATUS_CLOSED_PATTERN.matcher(newString).find())
      return Result.allow();

    String taskName = extractTaskName(filePath);
    String message = buildWarningMessage(taskName);

    return Result.warn(message);
  }

  /**
   * Extract the task name from the STATE.md file path.
   *
   * @param filePath the file path
   * @return the task name
   */
  private String extractTaskName(String filePath)
  {
    int lastSlash = filePath.lastIndexOf('/');
    if (lastSlash > 0)
    {
      int secondLastSlash = filePath.lastIndexOf('/', lastSlash - 1);
      if (secondLastSlash > 0)
        return filePath.substring(secondLastSlash + 1, lastSlash);
    }
    return "unknown";
  }

  /**
   * Build the warning message.
   *
   * @param taskName the task name
   * @return the warning message
   */
  private String buildWarningMessage(String taskName)
  {
    return "⚠️ WORKFLOW COMPLETION CHECK (M217)\n" +
           "\n" +
           "You are marking task '" + taskName + "' as closed.\n" +
           "\n" +
           "Before completing a task via /cat:work, verify ALL phases are done:\n" +
           "\n" +
           "1. **Setup** ✓ (worktree created, task loaded)\n" +
           "2. **Implementation** ✓ (code written, tests pass, committed)\n" +
           "3. **Reviewing** - Did you complete:\n" +
           "   - [ ] stakeholder_review (run parallel stakeholder reviews)\n" +
           "   - [ ] approval_gate (present changes for USER approval)\n" +
           "4. **Merging** - Did you:\n" +
           "   - [ ] Ask user if they want to merge\n" +
           "   - [ ] Run squash_commits, merge, cleanup\n" +
           "\n" +
           "If you skipped phases 3-4, STOP and return to the /cat:work workflow.\n" +
           "Committing code does NOT complete the task - user review and merge are required.\n" +
           "\n" +
           "If this is a legitimate completion (all phases done), proceed with the edit.";
  }
}

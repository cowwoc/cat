package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:stakeholder-review skill.
 *
 * Provides box outputs for stakeholder selection and review.
 */
public final class GetStakeholderOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetStakeholderOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetStakeholderOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Represents a skipped stakeholder with reason.
   *
   * @param stakeholder the stakeholder name
   * @param reason the reason for skipping
   */
  public record SkippedStakeholder(String stakeholder, String reason)
  {
    /**
     * Creates a skipped stakeholder record.
     *
     * @param stakeholder the stakeholder name
     * @param reason the reason for skipping
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if any parameter is blank
     */
    public SkippedStakeholder
    {
      requireThat(stakeholder, "stakeholder").isNotBlank();
      requireThat(reason, "reason").isNotBlank();
    }
  }

  /**
   * Represents a reviewer's status in the review.
   *
   * @param stakeholder the stakeholder name
   * @param status the review status (e.g., "APPROVED", "CONCERNS", "pending...")
   */
  public record ReviewerStatus(String stakeholder, String status)
  {
    /**
     * Creates a reviewer status record.
     *
     * @param stakeholder the stakeholder name
     * @param status the review status
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if any parameter is blank
     */
    public ReviewerStatus
    {
      requireThat(stakeholder, "stakeholder").isNotBlank();
      requireThat(status, "status").isNotBlank();
    }
  }

  /**
   * Build the stakeholder selection box.
   *
   * @param selectedCount the number of stakeholders selected
   * @param totalCount the total number of stakeholders available
   * @param runningList the list of stakeholder names currently running
   * @param skipped the list of skipped stakeholders with reasons
   * @return the formatted selection box
   * @throws NullPointerException if runningList or skipped is null
   * @throws IllegalArgumentException if counts are negative or selectedCount exceeds totalCount
   */
  public String getSelectionBox(int selectedCount, int totalCount, List<String> runningList,
                                List<SkippedStakeholder> skipped)
  {
    requireThat(selectedCount, "selectedCount").isNotNegative();
    requireThat(totalCount, "totalCount").isNotNegative();
    requireThat(selectedCount, "selectedCount").isLessThanOrEqualTo(totalCount);
    requireThat(runningList, "runningList").isNotNull();
    requireThat(skipped, "skipped").isNotNull();

    List<String> content = new ArrayList<>();
    content.add("");
    content.add("Stakeholder Review: " + selectedCount + " of " + totalCount + " stakeholders selected");
    content.add("");
    content.add("Running: " + String.join(", ", runningList));
    content.add("");
    content.add("Skipped:");
    for (SkippedStakeholder s : skipped)
      content.add("  - " + s.stakeholder() + ": " + s.reason());
    content.add("");

    return scope.getDisplayUtils().buildHeaderBox("STAKEHOLDER SELECTION", content);
  }

  /**
   * Build the stakeholder review box.
   *
   * @param taskName the task being reviewed
   * @param reviewers the list of reviewer statuses
   * @param result the overall result (e.g., "APPROVED", "CONCERNS", "REJECTED")
   * @param summary a brief summary of the review outcome
   * @return the formatted review box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if taskName, result, or summary is blank, or reviewers is empty
   */
  public String getReviewBox(String taskName, List<ReviewerStatus> reviewers, String result, String summary)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(reviewers, "reviewers").isNotNull().isNotEmpty();
    requireThat(result, "result").isNotBlank();
    requireThat(summary, "summary").isNotBlank();

    List<String> content = new ArrayList<>();
    content.add("");
    content.add("Task: " + taskName);
    content.add("");
    int separator1 = content.size();

    content.add("Spawning reviewers...");
    for (int i = 0; i < reviewers.size(); ++i)
    {
      ReviewerStatus r = reviewers.get(i);
      String prefix;
      if (i == reviewers.size() - 1)
        prefix = "└── ";
      else
        prefix = "├── ";
      content.add(prefix + r.stakeholder() + " " + r.status());
    }
    int separator2 = content.size();

    content.add("Result: " + result + " (" + summary + ")");
    content.add("");

    return scope.getDisplayUtils().buildHeaderBox("STAKEHOLDER REVIEW", content, List.of(separator1, separator2));
  }

  /**
   * Build a critical concern box.
   *
   * @param stakeholder the stakeholder raising the concern
   * @param concernDescription the description of the concern
   * @param fileLocation the file location related to the concern
   * @return the formatted critical concern box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getCriticalConcernBox(String stakeholder, String concernDescription, String fileLocation)
  {
    requireThat(stakeholder, "stakeholder").isNotBlank();
    requireThat(concernDescription, "concernDescription").isNotBlank();
    requireThat(fileLocation, "fileLocation").isNotBlank();

    List<String> concerns = List.of(
      "[" + stakeholder + "] " + concernDescription,
      "└─ " + fileLocation,
      "");
    return scope.getDisplayUtils().buildConcernBox("CRITICAL", concerns);
  }

  /**
   * Build a high concern box.
   *
   * @param stakeholder the stakeholder raising the concern
   * @param concernDescription the description of the concern
   * @param fileLocation the file location related to the concern
   * @return the formatted high concern box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getHighConcernBox(String stakeholder, String concernDescription, String fileLocation)
  {
    requireThat(stakeholder, "stakeholder").isNotBlank();
    requireThat(concernDescription, "concernDescription").isNotBlank();
    requireThat(fileLocation, "fileLocation").isNotBlank();

    List<String> concerns = List.of(
      "[" + stakeholder + "] " + concernDescription,
      "└─ " + fileLocation,
      "");
    return scope.getDisplayUtils().buildConcernBox("HIGH", concerns);
  }
}

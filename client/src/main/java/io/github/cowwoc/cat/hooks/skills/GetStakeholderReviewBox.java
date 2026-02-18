/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Generates the stakeholder review box for /cat:stakeholder-review skill.
 * <p>
 * Displays the aggregated review outcome showing per-reviewer statuses, overall result, and summary.
 */
public final class GetStakeholderReviewBox
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Represents a reviewer's status in the review.
   *
   * @param stakeholder the stakeholder name
   * @param status      the review status (e.g., "APPROVED", "CONCERNS", "pending...")
   */
  public record ReviewerStatus(String stakeholder, String status)
  {
    /**
     * Creates a reviewer status record.
     *
     * @param stakeholder the stakeholder name
     * @param status      the review status
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if any parameter is blank
     */
    public ReviewerStatus
    {
      requireThat(stakeholder, "stakeholder").isNotBlank();
      requireThat(status, "status").isNotBlank();
    }
  }

  /**
   * Creates a GetStakeholderReviewBox instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStakeholderReviewBox(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Build the stakeholder review box.
   *
   * @param issueName  the issue being reviewed
   * @param reviewers  the list of reviewer statuses
   * @param result     the overall result (e.g., "APPROVED", "CONCERNS", "REJECTED")
   * @param summary    a brief summary of the review outcome
   * @return the formatted review box
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if {@code issueName}, {@code result}, or {@code summary} is blank,
   *                                  or {@code reviewers} is empty
   */
  public String getReviewBox(String issueName, List<ReviewerStatus> reviewers, String result, String summary)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(reviewers, "reviewers").isNotNull().isNotEmpty();
    requireThat(result, "result").isNotBlank();
    requireThat(summary, "summary").isNotBlank();

    List<String> content = new ArrayList<>();
    content.add("");
    content.add("Issue: " + issueName);
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
   * Main method for command-line execution.
   * <p>
   * Usage:
   * <pre>
   * get-stakeholder-review-box &lt;issue&gt; &lt;reviewers&gt; &lt;result&gt; &lt;summary&gt;
   * </pre>
   * Where:
   * <ul>
   *   <li>{@code issue} - the issue name</li>
   *   <li>{@code reviewers} - comma-separated colon pairs (e.g., "architect:APPROVED,design:CONCERNS")</li>
   *   <li>{@code result} - overall result: "APPROVED", "CONCERNS", or "REJECTED"</li>
   *   <li>{@code summary} - brief summary of the review outcome</li>
   * </ul>
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length != 4)
    {
      System.err.println("Expected 4 arguments but got " + args.length);
      printUsage();
      System.exit(1);
    }

    String issue = args[0];
    String reviewers = args[1];
    String result = args[2];
    String summary = args[3];

    try (JvmScope scope = new MainJvmScope())
    {
      GetStakeholderReviewBox box = new GetStakeholderReviewBox(scope);
      List<ReviewerStatus> reviewerList = parseReviewers(reviewers);
      System.out.print(box.getReviewBox(issue, reviewerList, result, summary));
    }
  }

  /**
   * Parses a comma-separated list of "stakeholder:status" pairs into reviewer statuses.
   *
   * @param value the comma-separated string of "stakeholder:status" pairs
   * @return the list of reviewer statuses
   * @throws NullPointerException if {@code value} is null
   */
  private static List<ReviewerStatus> parseReviewers(String value)
  {
    requireThat(value, "value").isNotNull();
    if (value.isEmpty())
      return List.of();
    List<ReviewerStatus> result = new ArrayList<>();
    for (String pair : value.split(","))
    {
      int colonIndex = pair.indexOf(':');
      if (colonIndex < 0)
        continue;
      String name = pair.substring(0, colonIndex).strip();
      String status = pair.substring(colonIndex + 1).strip();
      if (!name.isEmpty() && !status.isEmpty())
        result.add(new ReviewerStatus(name, status));
    }
    return result;
  }

  /**
   * Prints usage information to stderr.
   */
  private static void printUsage()
  {
    System.err.println("""
      Usage:
        get-stakeholder-review-box <issue> <reviewers> <result> <summary>

      Arguments:
        issue      The issue name
        reviewers  Comma-separated colon pairs (e.g., "architect:APPROVED,design:CONCERNS")
        result     Overall result: "APPROVED", "CONCERNS", or "REJECTED"
        summary    Brief summary of the review outcome
      """);
  }
}

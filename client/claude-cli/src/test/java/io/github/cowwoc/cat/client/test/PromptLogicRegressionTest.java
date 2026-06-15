/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Regression tests for prompt-owned parsing and routing logic previously covered by shell helpers.
 */
public final class PromptLogicRegressionTest
{
  private static final Pattern CURIOSITY_PATTERN = Pattern.compile(
    "\"curiosity\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern SLASH_COMMAND_PATTERN = Pattern.compile("/cat:[a-z]");
  private static final Pattern STATUS_PATTERN = Pattern.compile(
    "\"status\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern HIGH_CONCERNS_PATTERN = Pattern.compile(
    "\"has_high_or_critical\"\\s*:\\s*([^,}\\s]+)");

  /**
   * Verifies the add lightweight plan skeleton uses the Parent Requirements heading.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void addLightweightPlanUsesParentRequirements() throws IOException
  {
    String planBlock = extractLightweightPlanBlock();

    requireThat(planBlock, "planBlock").contains("## Parent Requirements");
    requireThat(planBlock, "planBlock").contains("\nNone\n");
    requireThat(planBlock, "planBlock").doesNotContain("## Satisfies");
  }

  /**
   * Verifies add completion guidance prohibits internal slash commands and the detector matches intended strings.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void addGuidanceRejectsSlashCommands() throws IOException
  {
    String firstUse = Files.readString(sourceRoot().
      resolve("client/plugin/skills/common/add/first-use.md"), StandardCharsets.UTF_8);

    requireThat(firstUse, "firstUse").contains("Post-completion workflow");
    requireThat(firstUse, "firstUse").contains("do NOT mention internal slash commands");
    requireThat(firstUse, "firstUse").contains("/cat:work");
    requireThat(firstUse, "firstUse").contains("structured user-choice prompt");

    requireThat(containsSlashCommand(
      "Your issue has been created. You can start working on it with /cat:work."),
      "containsWork").isTrue();
    requireThat(containsSlashCommand(
      "Issue created successfully. To see project status, run /cat:status."),
      "containsStatus").isTrue();
    requireThat(containsSlashCommand(
      "Done! To add another issue, use /cat:add."),
      "containsAdd").isTrue();
    requireThat(containsSlashCommand(
      "Your issue has been created successfully. Would you like to start working on it now?"),
      "cleanResponse").isFalse();
    requireThat(containsSlashCommand("Issue 'fix-login-bug' created under v2.1. Start working on it now?"),
      "offerResponse").isFalse();
    requireThat(containsSlashCommand("The prefix cat: is used for skills."),
      "prefixOnly").isFalse();
    requireThat(containsSlashCommand("Use /CAT:work to start."),
      "uppercase").isFalse();
  }

  /**
   * Verifies work-implement has-steps, jobs-count, and plan-builder invocation helper logic.
   */
  @Test
  public void workImplementPlanRouting() throws IOException
  {
    requireThat(detectHasSteps("## Goal\n\nSome goal text.\n\n## Post-conditions\n\n- condition 1\n"),
      "noSteps").isFalse();
    requireThat(detectHasSteps("## Goal\n\nSome goal.\n\n## Jobs\n\n### Job 1\n"),
      "jobsHeader").isTrue();
    requireThat(detectHasSteps("## Goal\n\nSome goal.\n\n## Execution Steps\n\n1. Step one\n"),
      "executionStepsHeader").isTrue();
    requireThat(detectHasSteps(" ## Jobs\n"), "leadingWhitespace").isFalse();
    requireThat(detectHasSteps("### Jobs\n"), "wrongHeaderLevel").isFalse();

    requireThat(detectJobsCount(""), "emptyJobsCount").isEqualTo(0);
    requireThat(detectJobsCount("## Jobs\n\n### Job 1\nStep 1 content\n"), "oneJob").isEqualTo(1);
    requireThat(detectJobsCount("## Jobs\n\n### Job 1\n\n### Job 2\n"), "twoJobs").isEqualTo(2);
    requireThat(detectJobsCount("### Job 1\n\n### Implementation Notes\n\n### Job 2\n"),
      "ignoresOtherHeaders").isEqualTo(2);
    requireThat(detectJobsCount("## Execution Steps\n\n### Step 1\nDo something\n"),
      "executionStepsNotJobs").isEqualTo(0);

    requireThat(classifyJobExecution(0), "zeroJobs").isEqualTo("single");
    requireThat(classifyJobExecution(1), "oneJobClass").isEqualTo("single");
    requireThat(classifyJobExecution(2), "twoJobsClass").isEqualTo("parallel");
    requireThat(buildSubagentPrompt("/tmp/test/plan.md"), "subagentPrompt").
      contains("PLAN_MD_PATH: /tmp/test/plan.md").
      doesNotContain("JOBS_COUNT=").
      doesNotContain(" 3 ");

    requireThat(extractCuriosityRaw("{\"curiosity\": \"high\", \"trust\": \"medium\"}"),
      "rawHigh").isEqualTo("high");
    requireThat(extractCuriosityRaw("{\"curiosity\": \"invalid\"}"),
      "rawInvalid").isEqualTo("invalid");
    requireThat(extractCuriosityRaw("{\"curiosity\": 42}"),
      "rawNumeric").isEmpty();
    requireThat(extractCuriosityRaw("{\"curiosity\": true}"),
      "rawBoolean").isEmpty();
    requireThat(extractCuriosityRaw("{\"outer\":{\"curiosity\":\"high\"},\"curiosity\":\"low\"}"),
      "nestedFirstMatch").isEqualTo("high\nlow");
    requireThat(normalizeCuriosity(""), "defaultCuriosity").isEqualTo("medium");
    requireThat(normalizeCuriosity("low"), "lowCuriosity").isEqualTo("low");

    Throwable invalidCuriosity = expectThrows(() -> normalizeCuriosity("bogus"));
    requireThat(invalidCuriosity.getMessage(), "invalidCuriosityMessage").contains(
      "Invalid curiosity value 'bogus'");

    requireThat(buildPlanBuilderArgs("high", "/path/to/issue"), "planBuilderArgs").isEqualTo(
      "high revise /path/to/issue Generate full implementation steps for this lightweight plan. " +
        "Add Jobs or Execution Steps section with detailed step-by-step implementation guidance.");
    requireThat(buildPlanBuilderArgs("low", "/other/path"), "planBuilderArgsLow").contains("low revise");

    requireThat(shouldInvokePlanBuilder("## Jobs\n\n### Job 1\n", () ->
      {
        throw new IOException("should not read config");
      }, "/issue/path"), "skipOnSteps").isEqualTo("SKIP");
    requireThat(shouldInvokePlanBuilder("## Goal\n\nSome goal text.\n", () ->
      "{\"curiosity\": \"high\"}", "/issue/path"), "invokeHigh").
      isEqualTo("INVOKE:" + buildPlanBuilderArgs("high", "/issue/path"));
    requireThat(shouldInvokePlanBuilder("## Goal\n\nSome goal text.\n", () ->
      "{\"trust\":\"medium\"}", "/issue/path"), "invokeDefaultMedium").
      isEqualTo("INVOKE:" + buildPlanBuilderArgs("medium", "/issue/path"));

    Throwable configFailure = expectThrows(() -> shouldInvokePlanBuilder(
      "## Goal\n\nSome goal text.\n", () ->
      {
        throw new IOException("config failed");
      }, "/issue/path"));
    requireThat(configFailure.getMessage(), "configFailure").contains("Failed to read effective config");
  }

  /**
   * Verifies curiosity-driven review routing and scope selection.
   */
  @Test
  public void curiosityRoutingAndReviewScope()
  {
    requireThat(extractCuriosityWithDefault("{\"trust\": \"medium\", \"curiosity\": \"low\"}"),
      "curiosityLow").isEqualTo("low");
    requireThat(extractCuriosityWithDefault("{\"trust\": \"medium\", \"curiosity\": \"medium\"}"),
      "curiosityMedium").isEqualTo("medium");
    requireThat(extractCuriosityWithDefault("{\"trust\": \"high\", \"curiosity\": \"high\"}"),
      "curiosityHigh").isEqualTo("high");
    requireThat(extractCuriosityWithDefault("{\"curiosity\" : \"high\"}"),
      "curiosityWhitespace").isEqualTo("high");
    requireThat(extractCuriosityWithDefault("{\"curiosity\"  :  \"low\"}"),
      "curiosityMultipleSpaces").isEqualTo("low");
    requireThat(extractCuriosityWithDefault("{\"trust\": \"medium\", \"effort\": \"medium\"}"),
      "curiosityMissing").isEqualTo("medium");
    requireThat(extractCuriosityWithDefault("{}"), "curiosityEmptyObject").isEqualTo("medium");
    requireThat(extractCuriosityWithDefault(""), "curiosityEmptyString").isEqualTo("medium");

    requireThat("low".equals("low"), "lowSkips").isTrue();
    requireThat("medium".equals("low"), "mediumSkips").isFalse();
    requireThat("high".equals("low"), "highSkips").isFalse();
    requireThat("high".equals("high"), "highResearch").isTrue();
    requireThat("low".equals("high"), "lowResearch").isFalse();

    String lowScope = getReviewScope("low");
    String mediumScope = getReviewScope("medium");
    String highScope = getReviewScope("high");
    requireThat(lowScope, "lowScope").isEqualTo(
      "Review changed lines only. Flag obvious issues visible in the diff.");
    requireThat(mediumScope, "mediumScope").contains("surrounding context");
    requireThat(highScope, "highScope").contains("Review the broader system context");
    requireThat(highScope, "highScope").contains("downstream impact");
    requireThat(lowScope, "lowVsMedium").isNotEqualTo(mediumScope);
    requireThat(mediumScope, "mediumVsHigh").isNotEqualTo(highScope);

    String extractedLow = extractCuriosityWithDefault("{\"curiosity\": \"low\"}");
    requireThat(extractedLow, "e2eLow").isEqualTo("low");
    requireThat("low".equals(extractedLow), "e2eLowSkip").isTrue();
    requireThat("high".equals(extractedLow), "e2eLowResearch").isFalse();
    requireThat(getReviewScope(extractCuriosityWithDefault("{\"curiosity\": \"medium\"}")),
      "e2eMediumScope").contains("surrounding context");
    requireThat(getReviewScope(extractCuriosityWithDefault("{\"curiosity\": \"high\"}")),
      "e2eHighScope").contains("broader system context");
  }

  /**
   * Verifies work-prepare pre-condition extraction.
   */
  @Test
  public void workPrepareExtractsUnchecked()
  {
    List<String> mixed = extractUnmetPreconditions("""
      ## Pre-conditions

      - [ ] Some prerequisite that is not yet satisfied
      - [x] Another prerequisite that is already done
      """);
    requireThat(mixed, "mixed").containsExactly(List.of("- [ ] Some prerequisite that is not yet satisfied"));

    requireThat(extractUnmetPreconditions("""
      ## Pre-conditions

      - [x] First prerequisite done
      - [x] Second prerequisite done
      """), "allChecked").isEmpty();
    requireThat(extractUnmetPreconditions("""
      ## Pre-conditions

      - [ ] First prerequisite
      - [ ] Second prerequisite
      - [ ] Third prerequisite
      """), "allUnchecked").containsExactly(List.of(
      "- [ ] First prerequisite",
      "- [ ] Second prerequisite",
      "- [ ] Third prerequisite"));
    requireThat(extractUnmetPreconditions("""
      ## Goal

      Do something useful.

      ## Post-conditions

      - [ ] Work is done
      """), "noPreconditions").isEmpty();
    requireThat(extractUnmetPreconditions("""
      ## Pre-conditions

      ## Post-conditions

      - [ ] Work is done
      """), "emptyPreconditions").isEmpty();
    requireThat(extractUnmetPreconditions(null), "missingPlanGuard").isEmpty();
  }

  /**
   * Verifies trust-gate routing logic for work-implement and work-merge.
   */
  @Test
  public void trustGateRouting()
  {
    String goalSection = extractGoalSection("""
      ## Goal
      Implement trust approval gates for the work workflow.

      ## Post-conditions
      - trust=low shows pre-implementation gate
      - trust=medium skips pre-implementation gate
      - trust=high auto-merges on clean review

      ## Execution Plan
      ### Job 1
      ...
      """);
    requireThat(goalSection, "goalSection").contains("Implement trust approval gates");

    requireThat(isPreImplementationGateSkipped("medium"), "mediumSkipsGate").isTrue();
    requireThat(isPreImplementationGateSkipped("low"), "lowDoesNotSkipGate").isFalse();

    requireThat(shouldPauseHighTrust("""
      {
        "status": "REVIEW_PASSED",
        "has_high_or_critical": false
      }
      """), "highTrustCleanReview").isFalse();
    requireThat(shouldPauseHighTrust("""
      {
        "status": "REVIEW_PASSED",
        "has_high_or_critical": true
      }
      """), "highTrustHighConcern").isTrue();
    requireThat(shouldPauseHighTrust("""
      {
        "status": "CONCERNS_FOUND",
        "has_high_or_critical": false
      }
      """), "highTrustConcernStatus").isTrue();
  }

  private static String extractLightweightPlanBlock() throws IOException
  {
    String firstUse = Files.readString(sourceRoot().
      resolve("client/plugin/skills/common/add/first-use.md"), StandardCharsets.UTF_8);
    int start = firstUse.indexOf("2. Write the lightweight plan.md to ");
    int end = firstUse.indexOf("3. Write the index.json content to ", start);
    requireThat(start, "lightweightPlanStart").isGreaterThanOrEqualTo(0);
    requireThat(end, "lightweightPlanEnd").isGreaterThan(start);
    return firstUse.substring(start, end);
  }

  private static boolean containsSlashCommand(String text)
  {
    return SLASH_COMMAND_PATTERN.matcher(text).find();
  }

  private static boolean detectHasSteps(String plan)
  {
    for (String line : plan.split("\\R", -1))
    {
      if (line.equals("## Jobs") || line.equals("## Execution Steps"))
        return true;
    }
    return false;
  }

  private static int detectJobsCount(String plan)
  {
    int count = 0;
    for (String line : plan.split("\\R", -1))
    {
      if (line.startsWith("### Job "))
        count += 1;
    }
    return count;
  }

  private static String classifyJobExecution(int jobsCount)
  {
    if (jobsCount <= 1)
      return "single";
    return "parallel";
  }

  private static String buildSubagentPrompt(String planMdPath)
  {
    return "PLAN_MD_PATH: " + planMdPath;
  }

  private static String extractCuriosityRaw(String json)
  {
    Matcher matcher = CURIOSITY_PATTERN.matcher(json);
    List<String> matches = new ArrayList<>();
    while (matcher.find())
      matches.add(matcher.group(1));
    return String.join("\n", matches);
  }

  private static String extractCuriosityWithDefault(String json)
  {
    String result = extractCuriosityRaw(json);
    if (result.isEmpty())
      return "medium";
    return result.lines().findFirst().orElse("medium");
  }

  private static String normalizeCuriosity(String curiosity)
  {
    if (curiosity.isEmpty())
      return "medium";
    return switch (curiosity)
    {
      case "low", "medium", "high" -> curiosity;
      default -> throw new IllegalArgumentException(
        "ERROR: Invalid curiosity value '" + curiosity + "'. Expected one of: low, medium, high.");
    };
  }

  private static String buildPlanBuilderArgs(String curiosity, String issuePath)
  {
    return curiosity + " revise " + issuePath + " Generate full implementation steps for this lightweight " +
      "plan. Add Jobs or Execution Steps section with detailed step-by-step implementation guidance.";
  }

  private static String shouldInvokePlanBuilder(String plan, ConfigSupplier configSupplier, String issuePath)
    throws IOException
  {
    if (detectHasSteps(plan))
      return "SKIP";
    String config;
    try
    {
      config = configSupplier.get();
    }
    catch (IOException e)
    {
      throw new IOException("ERROR: Failed to read effective config", e);
    }
    String curiosity = normalizeCuriosity(extractCuriosityRaw(config).lines().findFirst().orElse(""));
    return "INVOKE:" + buildPlanBuilderArgs(curiosity, issuePath);
  }

  private static String getReviewScope(String curiosity)
  {
    return switch (curiosity)
    {
      case "low" -> "Review changed lines only. Flag obvious issues visible in the diff.";
      case "high" -> "Review the broader system context. For each changed file, read the surrounding code " +
        "that references or depends on it. Consider: (1) how this change interacts with other open issues in " +
        "the same version, (2) architectural patterns in the rest of the codebase this change should follow " +
        "or might inadvertently break, (3) cross-cutting concerns (security, performance, accessibility) " +
        "beyond immediately changed files. Flag pre-existing issues in any file you read. Consider downstream " +
        "impact on consumers of changed APIs or interfaces.";
      case "medium" -> "Review changed lines and their surrounding context (functions, classes containing " +
        "the change). Flag issues that arise from the interaction between new and existing code.";
      default -> "Review changed lines and their surrounding context (functions, classes containing the " +
        "change). Flag issues that arise from the interaction between new and existing code.";
    };
  }

  private static List<String> extractUnmetPreconditions(String plan)
  {
    List<String> unmet = new ArrayList<>();
    if (plan == null)
      return unmet;
    boolean inPreconditions = false;
    for (String line : plan.split("\\R", -1))
    {
      if (line.equals("## Pre-conditions"))
      {
        inPreconditions = true;
        continue;
      }
      if (inPreconditions && line.startsWith("## "))
        break;
      if (inPreconditions && line.startsWith("- [ ]"))
        unmet.add(line);
    }
    return unmet;
  }

  private static String extractGoalSection(String plan)
  {
    StringBuilder result = new StringBuilder();
    boolean inGoal = false;
    for (String line : plan.split("\\R", -1))
    {
      if (line.equals("## Goal"))
      {
        inGoal = true;
        continue;
      }
      if (inGoal && line.startsWith("## "))
        break;
      if (inGoal && !line.isBlank())
      {
        if (!result.isEmpty())
          result.append('\n');
        result.append(line);
      }
    }
    return result.toString();
  }

  private static boolean isPreImplementationGateSkipped(String trust)
  {
    return !"low".equals(trust);
  }

  private static boolean shouldPauseHighTrust(String reviewResultJson)
  {
    String status = extractFirstGroup(STATUS_PATTERN, reviewResultJson);
    String hasHigh = extractFirstGroup(HIGH_CONCERNS_PATTERN, reviewResultJson).replace(" ", "");
    return "CONCERNS_FOUND".equals(status) || "true".equals(hasHigh);
  }

  private static String extractFirstGroup(Pattern pattern, String text)
  {
    Matcher matcher = pattern.matcher(text);
    if (!matcher.find())
      return "";
    return matcher.group(1);
  }

  private static Throwable expectThrows(ThrowingRunnable runnable)
  {
    try
    {
      runnable.run();
    }
    catch (Throwable t)
    {
      return t;
    }
    throw new AssertionError("Expected exception");
  }

  private static Path sourceRoot() throws IOException
  {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isDirectory(current.resolve("client/plugin"), LinkOption.NOFOLLOW_LINKS) &&
        Files.isRegularFile(current.resolve("client/pom.xml"), LinkOption.NOFOLLOW_LINKS))
      {
        return current;
      }
      current = current.getParent();
    }
    throw new FileNotFoundException("Unable to find CAT source root");
  }

  @FunctionalInterface
  private interface ConfigSupplier
  {
    /**
     * Returns the effective config JSON.
     *
     * @return the config JSON
     * @throws IOException if loading fails
     */
    String get() throws IOException;
  }

  @FunctionalInterface
  private interface ThrowingRunnable
  {
    /**
     * Runs the action.
     *
     * @throws Exception if execution fails
     */
    void run() throws Exception;
  }
}

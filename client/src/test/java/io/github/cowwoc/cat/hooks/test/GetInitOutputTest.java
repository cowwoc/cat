/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetInitOutput;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetInitOutput functionality.
 * <p>
 * Tests verify that init output generation for default gates, research skipped,
 * choose your partner, CAT initialized, first issue, and all-set boxes
 * produces correctly formatted displays with proper structure and content.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetInitOutputTest
{
  /**
   * Verifies that default gates box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getDefaultGatesConfigured(3);

      requireThat(result, "result").contains("Default gates configured for 3 versions");
    }
  }

  /**
   * Verifies that default gates box describes entry gates.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesBoxDescribesEntryGates() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getDefaultGatesConfigured(1);

      requireThat(result, "result").contains("Entry gates");
    }
  }

  /**
   * Verifies that default gates box describes exit gates.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesBoxDescribesExitGates() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getDefaultGatesConfigured(1);

      requireThat(result, "result").contains("Exit gates");
    }
  }

  /**
   * Verifies that default gates box has rounded box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getDefaultGatesConfigured(2);

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that default gates box with zero versions works correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesBoxHandlesZeroVersions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getDefaultGatesConfigured(0);

      requireThat(result, "result").contains("Default gates configured for 0 versions");
    }
  }

  /**
   * Verifies that research skipped box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void researchSkippedBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getResearchSkipped("v2.0");

      requireThat(result, "result").contains("RESEARCH SKIPPED");
    }
  }

  /**
   * Verifies that research skipped box includes the example version.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void researchSkippedBoxIncludesExampleVersion() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getResearchSkipped("v3.1");

      requireThat(result, "result").contains("/cat:research v3.1");
    }
  }

  /**
   * Verifies that choose your partner box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void chooseYourPartnerBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getChooseYourPartner();

      requireThat(result, "result").contains("CHOOSE YOUR PARTNER");
    }
  }

  /**
   * Verifies that choose your partner box has rounded box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void chooseYourPartnerBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getChooseYourPartner();

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that CAT initialized box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void catInitializedBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getCatInitialized("high", "medium", "low");

      requireThat(result, "result").contains("CAT INITIALIZED");
    }
  }

  /**
   * Verifies that CAT initialized box shows preference values.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void catInitializedBoxShowsPreferences() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getCatInitialized("high", "medium", "low");

      requireThat(result, "result").contains("Trust: high").
        contains("Effort: medium").contains("Patience: low");
    }
  }

  /**
   * Verifies that CAT initialized box mentions /cat:config for adjustments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void catInitializedBoxMentionsConfigCommand() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getCatInitialized("medium", "medium", "medium");

      requireThat(result, "result").contains("/cat:config");
    }
  }

  /**
   * Verifies that first issue walkthrough box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueWalkthroughBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getFirstIssueWalkthrough();

      requireThat(result, "result").contains("FIRST ISSUE WALKTHROUGH");
    }
  }

  /**
   * Verifies that first issue created box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueCreatedBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getFirstIssueCreated("fix-login-bug");

      requireThat(result, "result").contains("FIRST ISSUE CREATED");
    }
  }

  /**
   * Verifies that first issue created box shows the issue name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueCreatedBoxShowsIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getFirstIssueCreated("add-search-feature");

      requireThat(result, "result").contains("Issue: add-search-feature");
    }
  }

  /**
   * Verifies that first issue created box shows location path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueCreatedBoxShowsLocation() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getFirstIssueCreated("my-task");

      requireThat(result, "result").contains(".claude/cat/issues/v0/v0.0/my-task/");
    }
  }

  /**
   * Verifies that first issue created box describes created files.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueCreatedBoxDescribesFiles() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getFirstIssueCreated("test-task");

      requireThat(result, "result").contains("PLAN.md").contains("STATE.md");
    }
  }

  /**
   * Verifies that all set box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allSetBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getAllSet();

      requireThat(result, "result").contains("ALL SET");
    }
  }

  /**
   * Verifies that all set box lists available commands.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allSetBoxListsCommands() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getAllSet();

      requireThat(result, "result").contains("/cat:work").contains("/cat:status").
        contains("/cat:add").contains("/cat:help");
    }
  }

  /**
   * Verifies that explore at your own pace box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void exploreBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getExploreAtYourOwnPace();

      requireThat(result, "result").contains("EXPLORE AT YOUR OWN PACE");
    }
  }

  /**
   * Verifies that explore box lists essential commands.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void exploreBoxListsCommands() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getExploreAtYourOwnPace();

      requireThat(result, "result").contains("/cat:status").contains("/cat:add").
        contains("/cat:work").contains("/cat:help");
    }
  }

  /**
   * Verifies that explore box includes /cat:status tip.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void exploreBoxIncludesStatusTip() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getExploreAtYourOwnPace();

      requireThat(result, "result").contains("Tip:").contains("/cat:status");
    }
  }

  /**
   * Verifies that getOutput with empty args returns empty string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputEmptyArgsReturnsEmptyString() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getOutput(new String[]{});

      requireThat(result, "result").isEqualTo("");
    }
  }

  /**
   * Verifies that getOutput with unknown page throws IllegalArgumentException listing valid pages.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputUnknownPageThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      try
      {
        handler.getOutput(new String[]{"invalid-page"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("Unknown page").
          contains("default-gates-configured").
          contains("research-skipped").
          contains("choose-your-partner").
          contains("cat-initialized").
          contains("first-issue-walkthrough").
          contains("first-issue-created").
          contains("all-set").
          contains("explore-at-your-own-pace");
      }
    }
  }

  /**
   * Verifies that default-gates-configured with non-numeric arg throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void defaultGatesConfiguredNonNumericArgThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      try
      {
        handler.getOutput(new String[]{"default-gates-configured", "not-a-number"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("version-count must be a number").
          contains("not-a-number");
      }
    }
  }

  /**
   * Verifies that research-skipped with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void researchSkippedInsufficientArgsThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      try
      {
        handler.getOutput(new String[]{"research-skipped"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("research-skipped requires 1 argument").
          contains("example-version");
      }
    }
  }

  /**
   * Verifies that cat-initialized with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void catInitializedInsufficientArgsThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      try
      {
        handler.getOutput(new String[]{"cat-initialized", "high", "medium"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("cat-initialized requires 3 arguments").
          contains("trust").
          contains("effort").
          contains("patience");
      }
    }
  }

  /**
   * Verifies that first-issue-created with insufficient args throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstIssueCreatedInsufficientArgsThrows() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      try
      {
        handler.getOutput(new String[]{"first-issue-created"});
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").
          contains("first-issue-created requires 1 argument").
          contains("issue-name");
      }
    }
  }

  /**
   * Verifies that getOutput returns correct box for default-gates-configured.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputDefaultGatesConfiguredDispatch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getOutput(new String[]{"default-gates-configured", "5"});

      requireThat(result, "result").contains("Default gates configured for 5 versions");
    }
  }

  /**
   * Verifies that getOutput returns correct box for research-skipped.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputResearchSkippedDispatch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getOutput(new String[]{"research-skipped", "v2.0"});

      requireThat(result, "result").
        contains("RESEARCH SKIPPED").
        contains("/cat:research v2.0");
    }
  }

  /**
   * Verifies that getOutput returns correct box for cat-initialized.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputCatInitializedDispatch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getOutput(new String[]{"cat-initialized", "high", "medium", "low"});

      requireThat(result, "result").
        contains("CAT INITIALIZED").
        contains("high").
        contains("medium").
        contains("low");
    }
  }

  /**
   * Verifies that getOutput returns correct box for first-issue-created.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputFirstIssueCreatedDispatch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetInitOutput handler = new GetInitOutput(scope);
      String result = handler.getOutput(new String[]{"first-issue-created", "my-feature"});

      requireThat(result, "result").
        contains("FIRST ISSUE CREATED").
        contains("my-feature");
    }
  }
}

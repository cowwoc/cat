/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.prompt.RemindSkillUsage;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for RemindSkillUsage prompt handler.
 */
public class RemindSkillUsageTest
{
  /**
   * Verifies that a rebase prompt triggers a skill reminder.
   */
  @Test
  public void checkReturnsReminderForRebasePrompt()
  {
    RemindSkillUsage handler = new RemindSkillUsage();
    String output = handler.check("please rebase this branch", "session-1");
    requireThat(output, "output").contains("/cat:git-rebase");
  }

  /**
   * Verifies that unrelated prompts do not produce reminders.
   */
  @Test
  public void checkReturnsEmptyForUnrelatedPrompt()
  {
    RemindSkillUsage handler = new RemindSkillUsage();
    String output = handler.check("show me status", "session-1");
    requireThat(output, "output").isEmpty();
  }
}

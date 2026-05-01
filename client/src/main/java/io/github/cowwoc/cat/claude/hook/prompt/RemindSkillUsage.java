/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.prompt;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.PromptHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Injects non-blocking reminders to use relevant skills when prompt patterns are detected.
 */
public final class RemindSkillUsage implements PromptHandler
{
  private final Map<Pattern, String> reminders;

  /**
   * Creates a new reminder handler with built-in prompt patterns.
   */
  public RemindSkillUsage()
  {
    this.reminders = new LinkedHashMap<>();
    reminders.put(Pattern.compile("\\b(rebase|rebasing)\\b", Pattern.CASE_INSENSITIVE),
      "Reminder: Use /cat:git-rebase for rebase operations.");
    reminders.put(Pattern.compile("\\b(squash|squashing)\\b", Pattern.CASE_INSENSITIVE),
      "Reminder: Use /cat:git-squash for squash operations.");
    reminders.put(Pattern.compile("\\b(amend|amending)\\b", Pattern.CASE_INSENSITIVE),
      "Reminder: Use /cat:git-amend for amend operations.");
    reminders.put(Pattern.compile("\\b(work on|resume|continue working)\\b", Pattern.CASE_INSENSITIVE),
      "Reminder: Use /cat:work for issue workflow execution.");
  }

  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(prompt, "prompt").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    StringBuilder out = new StringBuilder();
    for (Map.Entry<Pattern, String> entry : reminders.entrySet())
    {
      if (!entry.getKey().matcher(prompt).find())
        continue;
      if (!out.isEmpty())
        out.append('\n');
      out.append(entry.getValue());
    }
    return out.toString();
  }
}

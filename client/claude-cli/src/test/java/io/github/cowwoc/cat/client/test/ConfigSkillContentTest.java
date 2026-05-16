/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for the cat:config skill instruction content.
 */
public class ConfigSkillContentTest
{
  private static final Pattern QUESTIONNAIRE_OPTION = Pattern.compile("(?m)^- \"([^\"]+)\" →");
  private static final Pattern CONFIG_HEADER = Pattern.compile("(?m)^([ \\t]*-?[ \\t]*)header: \"[^\"]+\"\\R");

  /**
   * Verifies that the personality menu description names each setting instead of listing bare values.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void personalitySummaryLabelsEachSetting() throws IOException
  {
    String content = readPluginFile("skills/common/config/first-use.md");

    requireThat(content, "content").contains(
      "Currently: trust={trust} · caution={caution} · curiosity={curiosity} · " +
        "perfection={perfection} · verbosity={verbosity}");
    requireThat(content, "content").doesNotContain(
      "Currently: {trust} · {caution} · {curiosity} · {perfection} · {verbosity}");
  }

  /**
   * Verifies that questionnaire options in the skill instructions and template end with periods.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void questionnaireOptionsEndWithPeriods() throws IOException
  {
    String firstUse = readPluginFile("skills/common/config/first-use.md");
    String firstUseQuestionnaire = firstUse.substring(firstUse.indexOf("**Question 1:**"),
      firstUse.indexOf("**After collecting all 5 answers"));
    requireQuestionnaireOptionsEndWithPeriods(firstUseQuestionnaire);
    requireQuestionnaireOptionsEndWithPeriods(readPluginFile("templates/questionnaire.md"));
  }

  /**
   * Verifies that the final questionnaire wording describes explaining a change.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void finalQuestionUsesChangeExplanationWording() throws IOException
  {
    String firstUse = readPluginFile("skills/common/config/first-use.md");
    String template = readPluginFile("templates/questionnaire.md");
    String expected = "When explaining why a change was made, you'd prefer CAT to:";

    requireThat(firstUse, "firstUse").contains(expected).doesNotContain(
      "You're reviewing a PR with a tricky bug. You'd prefer CAT to:");
    requireThat(template, "template").contains(expected).doesNotContain(
      "You're reviewing a PR with a tricky bug. You'd prefer CAT to:");
  }

  /**
   * Verifies that cat:get-output tells agents to render only the computed output body.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputSkillDoesNotReturnDispatcherWrapper() throws IOException
  {
    String content = readPluginFile("skills/common/get-output/first-use.md");

    requireThat(content, "content").
      contains("Do not return the dispatcher output wholesale.").
      contains("Output only the complete inner content of that last matching tag.");
  }

  /**
   * Verifies that every config menu title requests left alignment.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void configMenuTitlesAreLeftAligned() throws IOException
  {
    String content = readPluginFile("skills/common/config/first-use.md");
    Matcher matcher = CONFIG_HEADER.matcher(content);
    int headers = 0;
    while (matcher.find())
    {
      ++headers;
      int lineEnd = content.indexOf('\n', matcher.end());
      if (lineEnd == -1)
        lineEnd = content.length();
      String nextLine = content.substring(matcher.end(), lineEnd);
      requireThat(nextLine, "nextLine").contains(matcher.group(1) + "title alignment: left");
    }
    requireThat(headers, "headers").isGreaterThan(0);
  }

  /**
   * Verifies that manual personality settings update one selected setting at a time.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void manualPersonalitySettingsSelectSettingThenValue() throws IOException
  {
    String content = readPluginFile("skills/common/config/first-use.md");
    String manualSettings = content.substring(content.indexOf("### Step 5a: Manual settings"),
      content.indexOf("### Step 6: Width settings"));

    requireThat(manualSettings, "manualSettings").
      contains("First ask which personality setting to update.").
      contains("Then ask for the new value for only the selected setting.").
      contains("Do not ask for trust, caution, curiosity, perfection, and verbosity in sequence.").
      contains("question: \"Which personality setting would you like to update?\"").
      contains("question: \"What value should {setting_name} use?\"").
      doesNotContain("questions array").
      doesNotContain("Page 1 of 2").
      doesNotContain("Page 2 of 2");
  }

  private static void requireQuestionnaireOptionsEndWithPeriods(String content)
  {
    Matcher matcher = QUESTIONNAIRE_OPTION.matcher(content);
    int options = 0;
    while (matcher.find())
    {
      ++options;
      String option = matcher.group(1);
      requireThat(option, "option").endsWith(".");
    }
    requireThat(options, "options").isGreaterThan(0);
  }

  private static String readPluginFile(String relativePath) throws IOException
  {
    Path pluginPath = Path.of(System.getProperty("user.dir")).resolve("../plugin").resolve(relativePath).normalize();
    return Files.readString(pluginPath);
  }
}

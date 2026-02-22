/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AskHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ask.WarnApprovalWithoutRenderDiff;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link WarnApprovalWithoutRenderDiff} detection logic.
 * <p>
 * Tests verify the detection of get-diff usage (or lack thereof) when approval questions are presented.
 */
public final class WarnApprovalWithoutRenderDiffTest
{
  /**
   * Verifies that warning IS triggered when approval question is present but "get-diff" is NOT found
   * in the recent session lines and box characters are insufficient.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingGetDiffTriggersWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-no-get-diff";

      // Set up .claude/cat directory so the catDir check passes
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);

      // Set up session file with content that does NOT contain "get-diff"
      Path sessionDir = tempDir.resolve("projects").resolve("-workspace");
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"I will show you the changes."}
        {"role":"assistant","content":"Here are the files modified: Foo.java, Bar.java"}
        """);

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      requireThat(result.additionalContext(), "additionalContext").contains("RENDER-DIFF NOT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is NOT triggered when "get-diff" IS present in the recent session lines
   * with sufficient box characters.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void presentGetDiffWithBoxCharsAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-with-get-diff";

      // Set up .claude/cat directory so the catDir check passes
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);

      // Set up session file with "get-diff" and box characters (more than MIN_BOX_CHARS_WITH_INVOCATION=10)
      Path sessionDir = tempDir.resolve("projects").resolve("-workspace");
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      // Include get-diff invocation and sufficient box chars (╭╮╰╯│ characters)
      String boxLine = "╭──────────────╮ ╭──────────╮\n│ old content  │ │ new file │\n╰──────────────╯ ╰──────────╯\n";
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"Invoking cat:get-diff to show the diff"}
        """ + "{\"role\":\"assistant\",\"content\":\"" + boxLine.replace("\n", "\\n").replace("\"", "\\\"") + "\"}\n");

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the sparse box character warning triggers when "get-diff" is present
   * but box characters are insufficient and manual diff signs are present.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void getDiffPresentButSparseBoxCharsTriggersReformatWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-reformatted-diff";

      // Set up .claude/cat directory so the catDir check passes
      Path catDir = tempDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir);

      // Set up session file with "get-diff" but sparse box chars and many manual diff signs.
      // MIN_BOX_CHARS_WITH_INVOCATION=10 so we need fewer than 10 box chars.
      // MIN_MANUAL_DIFF_SIGNS=5 so we need more than 5 matches of ^+++|^---|^@@.
      // The MANUAL_DIFF_SIGNS pattern uses MULTILINE, so each literal JSONL line can start
      // with a diff marker to trigger the pattern.
      Path sessionDir = tempDir.resolve("projects").resolve("-workspace");
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"Invoking cat:get-diff to show the diff"}
        --- a/Foo.java
        +++ b/Foo.java
        @@ -1,5 +1,5 @@
        --- a/Bar.java
        +++ b/Bar.java
        @@ -2,3 +2,3 @@
        """);

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      requireThat(result.additionalContext(), "additionalContext").contains("RENDER-DIFF OUTPUT MAY BE REFORMATTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is triggered when the question does not contain "approve".
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonApprovalQuestionAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-non-approval";

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Which approach do you prefer?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is triggered when there is no .claude/cat directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noCatDirectoryAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-no-cat-dir";

      // Intentionally do NOT create .claude/cat directory

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

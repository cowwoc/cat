/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.AskHandler;
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
 * <p>
 * Session files are placed at {@code {claudeConfigPath}/projects/{encodedProjectDir}/{sessionId}.jsonl}, resolved via
 * {@link JvmScope#getClaudeSessionsPath()}. The {@code .cat} directory is resolved via
 * {@link JvmScope#getProjectPath()}, matching the production code's path resolution.
 */
public final class WarnApprovalWithoutRenderDiffTest
{
  /**
   * Verifies that blocking IS triggered when approval question is present but "get-diff" is NOT found
   * in the recent session lines and box characters are insufficient.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingGetDiffBlocksApproval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-no-get-diff";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file with content that does NOT contain "get-diff"
      Path sessionDir = scope.getClaudeSessionsPath();
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

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("RENDER-DIFF NOT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that force push sessions are exempt from blocking even when "get-diff" is absent
   * and box characters are insufficient.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void forcePushIsExemptFromBlock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-force-push";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file containing force push signal but no get-diff and no box chars
      Path sessionDir = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"Pushing with git push --force to update the remote branch"}
        """);

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      // AskHandler.Result has no isAllowed() method; blocked() and additionalContext() are the complete specification
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that force push sessions using the short -f flag are exempt from blocking even when
   * "get-diff" is absent and box characters are insufficient.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void forcePushShortFlagIsExemptFromBlock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-force-push-short-flag";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file containing -f short flag signal but no get-diff and no box chars
      Path sessionDir = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"Pushing with git push -f origin main"}
        """);

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      // AskHandler.Result has no isAllowed() method; blocked() and additionalContext() are the complete specification
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that git filter-repo sessions are exempt from blocking even when "get-diff" is absent
   * and box characters are insufficient.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void filterRepoIsExemptFromBlock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-filter-repo";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file containing filter-repo signal but no get-diff and no box chars
      Path sessionDir = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionDir);
      Path sessionFile = sessionDir.resolve(sessionId + ".jsonl");
      Files.writeString(sessionFile, """
        {"role":"assistant","content":"Running git filter-repo --path sensitive.txt to clean history"}
        """);

      WarnApprovalWithoutRenderDiff handler = new WarnApprovalWithoutRenderDiff(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?"}""");

      AskHandler.Result result = handler.check(toolInput, sessionId);

      // AskHandler.Result has no isAllowed() method; blocked() and additionalContext() are the complete specification
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
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
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-with-get-diff";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file with "get-diff" and box characters (more than MIN_BOX_CHARS_WITH_INVOCATION=10)
      Path sessionDir = scope.getClaudeSessionsPath();
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
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-reformatted-diff";

      // Set up .cat directory so the catDir check passes
      Path catDir = scope.getProjectPath().resolve(".cat");
      Files.createDirectories(catDir);

      // Set up session file with "get-diff" but sparse box chars and many manual diff signs.
      // MIN_BOX_CHARS_WITH_INVOCATION=10 so we need fewer than 10 box chars.
      // REFORMAT_MANUAL_DIFF_THRESHOLD=5 so we need more than 5 matches of ^+++|^---|^@@.
      // The MANUAL_DIFF_SIGNS pattern uses MULTILINE, so each literal JSONL line can start
      // with a diff marker to trigger the pattern.
      Path sessionDir = scope.getClaudeSessionsPath();
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
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
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
   * Verifies that no warning is triggered when there is no .cat directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noCatDirectoryAllows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-warn-approval-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-session-no-cat-dir";

      // Intentionally do NOT create .cat directory

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

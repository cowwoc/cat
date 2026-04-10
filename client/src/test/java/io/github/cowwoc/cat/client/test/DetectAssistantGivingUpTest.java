/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.PostToolHandler;
import io.github.cowwoc.cat.claude.tool.post.DetectAssistantGivingUp;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for DetectAssistantGivingUp.
 */
public final class DetectAssistantGivingUpTest
{
  /**
   * Verifies that no warning is returned when no conversation log exists.
   */
  @Test
  public void noConversationLogAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String sessionId = "test-" + UUID.randomUUID();
      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no warning is returned when conversation log contains no giving-up patterns.
   */
  @Test
  public void noGivingUpPatternAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId, """
        {"role":"assistant","content":"I'll complete all the files."}
        {"role":"assistant","content":"Working on the next file now."}
        """);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that warning is returned when giving-up pattern is detected.
   */
  @Test
  public void givingUpPatternDetectedReturnsContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        """
        {"role":"assistant","content":"Given our token usage (139k/200k), let me complete a few more."}
        """);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").contains("TOKEN POLICY VIOLATION");
      requireThat(result.additionalContext(), "additionalContext").contains("PROHIBITED PATTERNS");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple giving-up patterns are detected.
   */
  @Test
  public void multiplePatternsDetected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String baseSessionId = "test-" + UUID.randomUUID();

      String[] patterns = {
        "given our token usage, let me continue",
        "given the token usage, I'll optimize",
        "tokens used so let me finish",
        "tokens remaining, so I'll complete",
        "given our context, let me complete",
        "our token budget suggests a few more",
        "i've optimized some, let me do more then proceed"
      };

      for (int i = 0; i < patterns.length; ++i)
      {
        String pattern = patterns[i];
        String uniqueSessionId = baseSessionId + "-" + i;
        Path conversationLog = createConversationLog(scope, uniqueSessionId,
          "{\"role\":\"assistant\",\"content\":\"" + pattern + "\"}");

        DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

        String hookDataJson = """
          {
            "tool_input": {},
            "tool_result": {},
            "session_id": "%s"
          }""".formatted(uniqueSessionId);
        JsonNode hookData = mapper.readTree(hookDataJson);
        JsonNode toolResult = mapper.readTree("{}");

        PostToolHandler.Result result = handler.check("Bash", toolResult, uniqueSessionId, hookData);

        requireThat(result.additionalContext(), "pattern_" + i).contains("TOKEN POLICY VIOLATION");

        Files.deleteIfExists(conversationLog);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that keywords split across separate messages do not trigger a false positive.
   * <p>
   * When "given" appears in one message, "token usage" in another, and "let me" in a third,
   * the hook must not fire — only a single message containing all keywords should trigger.
   */
  @Test
  public void keywordsSplitAcrossMessagesShouldNotTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      createConversationLog(scope, sessionId, """
          {"role":"assistant","content":"given the retrospective analysis above"}
          {"role":"assistant","content":"our token usage is high today"}
          {"role":"assistant","content":"let me proceed with implementing the fix"}
          """);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
          {"tool_input":{},"tool_result":{},"session_id":"%s"}""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that keywords in a tool_use input block do not trigger a false positive.
   * <p>
   * Even if all keywords appear in a tool_use block's input, the hook must not fire —
   * only text-type content blocks from the assistant should be scanned.
   */
  @Test
  public void keywordsInToolUseInputShouldNotTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String toolUseJson = "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\"," +
        "\"input\":{\"args\":\"given token usage let me fix this false positive\"}}]}";
      createConversationLog(scope, sessionId, toolUseJson);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
          {"tool_input":{},"tool_result":{},"session_id":"%s"}""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "let me remove" narration before a Bash tool call does not trigger CODE_REMOVAL.
   * <p>
   * When the agent writes "Let me remove the stale worktrees" immediately before a Bash tool call,
   * the assistant JSONL entry is a compound message containing both a text block and a tool_use block.
   * ConversationLogUtils.extractTextContent() returns "" for compound messages, preventing the text
   * from reaching GivingUpDetector and producing a false positive.
   */
  @Test
  public void compoundMessageWithLetMeRemoveDoesNotTriggerCodeRemoval() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String compoundMessageJson = """
        {"role":"assistant","content":[\
        {"type":"text","text":"Let me remove the stale worktrees."},\
        {"type":"tool_use","name":"Bash","input":{"command":"rm -rf .cat/work/worktrees/old"}}]}""";
      Path conversationLog = createConversationLog(scope, sessionId, compoundMessageJson);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.warning(), "warning").isEmpty();
      requireThat(result.additionalContext(), "additionalContext").isEmpty();

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a pure-text turn containing a genuine giving-up phrase IS still detected.
   * <p>
   * When "let me remove" appears in a pure-text assistant message (no tool_use blocks), it
   * represents actual agent reasoning and should trigger CODE_REMOVAL detection.
   */
  @Test
  public void pureTextTurnWithGivingUpPhraseIsDetected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      // Pure text turn — no tool_use block — so it IS scanned
      String pureTextJson =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"The test is failing so let me remove the broken assertion.\"}]}";
      Path conversationLog = createConversationLog(scope, sessionId, pureTextJson);

      DetectAssistantGivingUp handler = new DetectAssistantGivingUp(scope);

      String hookDataJson = """
        {
          "tool_input": {},
          "tool_result": {},
          "session_id": "%s"
        }""".formatted(sessionId);
      JsonNode hookData = mapper.readTree(hookDataJson);
      JsonNode toolResult = mapper.readTree("{}");

      PostToolHandler.Result result = handler.check("Bash", toolResult, sessionId, hookData);

      requireThat(result.additionalContext(), "additionalContext").contains("CODE DISABLING ANTI-PATTERN DETECTED");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a conversation log file for testing, using the scope's session base path.
   *
   * @param scope the hook scope providing the session base path
   * @param sessionId the session ID
   * @param content the JSONL content
   * @return the path to the created log file
   * @throws IOException if file creation fails
   */
  private Path createConversationLog(TestClaudeHook scope, String sessionId, String content) throws IOException
  {
    Path sessionBasePath = scope.getClaudeSessionsPath();
    Files.createDirectories(sessionBasePath);
    Path logFile = sessionBasePath.resolve(sessionId + ".jsonl");
    Files.writeString(logFile, content);
    return logFile;
  }
}

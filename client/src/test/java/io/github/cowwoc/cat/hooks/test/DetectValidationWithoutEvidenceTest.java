/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.tool.post.DetectValidationWithoutEvidence;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for DetectValidationWithoutEvidence.
 */
public final class DetectValidationWithoutEvidenceTest
{
  /**
   * Verifies that no warning is returned when no conversation log exists.
   */
  @Test
  public void noConversationLogAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that no warning is returned when the assistant output contains no validation claim keywords.
   */
  @Test
  public void noValidationClaimAllowsQuietly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId, """
        {"role":"assistant","content":"I'll complete all the files now."}
        {"role":"assistant","content":"Working on the next step."}
        """);

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that no warning is returned when a validation claim is present but a cat:compare-docs skill
   * invocation is also present in the transcript.
   */
  @Test
  public void validationClaimWithEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String skillInvocationLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\"," +
          "\"input\":{\"skill\":\"cat:compare-docs\",\"args\":\"doc1.md doc2.md\"}}]}";
      String claimLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"The documents are semantically equivalent.\"}]}";

      Path conversationLog = createConversationLog(scope, sessionId,
        skillInvocationLine + "\n" + claimLine + "\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that a warning is returned when a validation claim is present but no skill invocation is found
   * in the transcript.
   */
  @Test
  public void validationClaimWithoutEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"The documents are semantically equivalent. Validation complete.\"}]}\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
      requireThat(result.additionalContext(), "additionalContext").contains("VALIDATION CLAIM WITHOUT EVIDENCE");
      requireThat(result.additionalContext(), "additionalContext").contains("cat:compare-docs");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a "score: N/10" pattern triggers the warning when no skill invocation evidence is present.
   */
  @Test
  public void scoreClaimWithoutEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"The documents are equivalent. Score: 9/10.\"}]}\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
      requireThat(result.additionalContext(), "additionalContext").contains("VALIDATION CLAIM WITHOUT EVIDENCE");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a cat:verify-implementation skill invocation also counts as evidence.
   */
  @Test
  public void verifyImplementationSkillCountsAsEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String skillInvocationLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\"," +
          "\"input\":{\"skill\":\"cat:verify-implementation\",\"args\":\"\"}}]}";
      String claimLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"All acceptance criteria met.\"}]}";

      Path conversationLog = createConversationLog(scope, sessionId,
        skillInvocationLine + "\n" + claimLine + "\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that "verified:" pattern without evidence triggers a warning.
   */
  @Test
  public void verifiedPatternWithoutEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"Verified: The documents are identical.\"}]}\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
      requireThat(result.additionalContext(), "additionalContext").contains("VALIDATION CLAIM WITHOUT EVIDENCE");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a cat:verify-implementation-agent skill invocation counts as evidence.
   * <p>
   * This is the primary regression test for the false-positive bug: the main agent invokes
   * cat:verify-implementation-agent (with -agent suffix) and then summarizes results. The hook
   * must recognize the -agent suffix variant as valid evidence.
   */
  @Test
  public void verifyImplementationAgentSkillCountsAsEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String skillInvocationLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\"," +
          "\"input\":{\"skill\":\"cat:verify-implementation-agent\",\"args\":\"\"}}]}";
      String claimLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"Verification complete: all acceptance criteria met.\"}]}";

      Path conversationLog = createConversationLog(scope, sessionId,
        skillInvocationLine + "\n" + claimLine + "\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that a cat:compare-docs-agent skill invocation counts as evidence.
   */
  @Test
  public void compareDocsAgentSkillCountsAsEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      String skillInvocationLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\"," +
          "\"input\":{\"skill\":\"cat:compare-docs-agent\",\"args\":\"doc1.md doc2.md\"}}]}";
      String claimLine =
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"The documents are semantically equivalent.\"}]}";

      Path conversationLog = createConversationLog(scope, sessionId,
        skillInvocationLine + "\n" + claimLine + "\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
   * Verifies that "validated:" pattern without evidence triggers a warning.
   */
  @Test
  public void validatedPatternWithoutEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"Validated: All requirements met.\"}]}\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
      requireThat(result.additionalContext(), "additionalContext").contains("VALIDATION CLAIM WITHOUT EVIDENCE");

      Files.deleteIfExists(conversationLog);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that "no semantic loss" pattern without evidence triggers a warning.
   */
  @Test
  public void noSemanticLossClaimWithoutEvidence() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String sessionId = "test-" + UUID.randomUUID();
      Path conversationLog = createConversationLog(scope, sessionId,
        "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":" +
          "\"No semantic loss. Refactoring is semantically equivalent.\"}]}\n");

      DetectValidationWithoutEvidence handler = new DetectValidationWithoutEvidence(scope);

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
      requireThat(result.additionalContext(), "additionalContext").contains("VALIDATION CLAIM WITHOUT EVIDENCE");

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
   * @param scope     the JVM scope providing the session base path
   * @param sessionId the session ID
   * @param content   the JSONL content
   * @return the path to the created log file
   * @throws IOException if file creation fails
   */
  private Path createConversationLog(JvmScope scope, String sessionId, String content) throws IOException
  {
    Path sessionBasePath = scope.getSessionBasePath();
    Files.createDirectories(sessionBasePath);
    Path logFile = sessionBasePath.resolve(sessionId + ".jsonl");
    Files.writeString(logFile, content);
    return logFile;
  }
}

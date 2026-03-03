/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.tool.post;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PostToolHandler;
import io.github.cowwoc.cat.hooks.util.ConversationLogUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Detects validation or verification claims in assistant messages that lack corresponding skill invocation
 * evidence in the conversation transcript.
 * <p>
 * Monitors the most recent assistant text message for validation claim patterns. When a claim is found,
 * checks the last 20 messages for evidence of a {@code cat:compare-docs} or {@code cat:verify-implementation}
 * skill invocation. If no evidence is found, injects a warning into the context.
 */
public final class DetectValidationWithoutEvidence implements PostToolHandler
{
  private static final int CLAIM_CHECK_LIMIT = 5;
  private static final int EVIDENCE_CHECK_LIMIT = 20;

  private final Path sessionBasePath;
  private final JsonMapper mapper;

  /**
   * Creates a new detect-validation-without-evidence handler.
   *
   * @param scope the JVM scope providing configuration paths and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public DetectValidationWithoutEvidence(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.sessionBasePath = scope.getSessionBasePath();
    this.mapper = scope.getJsonMapper();
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    Path conversationLog = getConversationLogPath(sessionId);
    if (!Files.exists(conversationLog))
      return Result.allow();

    List<String> allLines = readAllLines(conversationLog);
    if (allLines.isEmpty())
      return Result.allow();

    List<String> assistantLines = allLines.stream().
      filter(line -> line.contains("\"role\":\"assistant\"")).
      toList();

    if (assistantLines.isEmpty())
      return Result.allow();

    // Check the most recent CLAIM_CHECK_LIMIT assistant messages for validation claims
    int claimTotal = assistantLines.size();
    List<String> recentClaimLines = assistantLines.stream().
      skip(Math.max(0, claimTotal - CLAIM_CHECK_LIMIT)).
      toList();

    boolean claimFound = false;
    for (String line : recentClaimLines)
    {
      String text = ConversationLogUtils.extractTextContent(line, mapper);
      if (containsValidationClaim(text))
      {
        claimFound = true;
        break;
      }
    }

    if (!claimFound)
      return Result.allow();

    // Check the last EVIDENCE_CHECK_LIMIT assistant messages for skill invocation evidence
    List<String> recentEvidenceLines = assistantLines.stream().
      skip(Math.max(0, claimTotal - EVIDENCE_CHECK_LIMIT)).
      toList();

    for (String line : recentEvidenceLines)
    {
      if (containsSkillEvidence(line))
        return Result.allow();
    }

    return Result.context("""
      VALIDATION CLAIM WITHOUT EVIDENCE DETECTED

      An agent claimed verification or validation results without evidence of a corresponding skill invocation.

      **Claim detected in assistant output**: contains validation/verification claim keywords
      **Expected evidence**: cat:compare-docs or cat:verify-implementation skill invocation in recent transcript

      **MANDATORY**: Do NOT fabricate validation results. Only report verification outcomes after invoking \
      the appropriate skill (cat:compare-docs for semantic equivalence, cat:verify-implementation for \
      post-condition checks).

      **If you need to validate**: Invoke the skill first, then report the result.""");
  }

  /**
   * Gets the path to the conversation log file.
   *
   * @param sessionId the session ID
   * @return the conversation log path
   */
  Path getConversationLogPath(String sessionId)
  {
    return sessionBasePath.resolve(sessionId + ".jsonl");
  }

  /**
   * Reads all lines from a conversation log file.
   *
   * @param conversationLog the path to the conversation log
   * @return list of all lines, or empty list on error
   */
  private List<String> readAllLines(Path conversationLog)
  {
    try
    {
      return Files.readAllLines(conversationLog);
    }
    catch (IOException _)
    {
      return List.of();
    }
  }

  /**
   * Checks whether a JSONL line contains a Skill tool_use block invoking cat:compare-docs or
   * cat:verify-implementation.
   *
   * @param jsonlLine the raw JSONL line to parse
   * @return true if a qualifying skill invocation is found in the line
   */
  private boolean containsSkillEvidence(String jsonlLine)
  {
    try
    {
      JsonNode root = mapper.readTree(jsonlLine);
      JsonNode contentNode = root.path("content");
      if (contentNode.isMissingNode())
        contentNode = root.path("message").path("content");
      if (!contentNode.isArray())
        return false;

      for (JsonNode block : contentNode)
      {
        if (!"tool_use".equals(block.path("type").asString()))
          continue;
        if (!"Skill".equals(block.path("name").asString()))
          continue;
        String skillName = block.path("input").path("skill").asString();
        if ("cat:compare-docs".equals(skillName) || "cat:verify-implementation".equals(skillName))
          return true;
      }
      return false;
    }
    catch (JacksonException _)
    {
      return false;
    }
  }

  /**
   * Checks whether the given text contains validation or verification claim patterns.
   *
   * @param text the text content of one assistant message
   * @return true if a validation claim pattern is detected
   */
  private boolean containsValidationClaim(String text)
  {
    if (text.isEmpty())
      return false;
    String lower = text.toLowerCase(Locale.ENGLISH);
    return lower.matches(".*score:\\s*\\d+/\\d+.*") ||
      lower.contains("verified:") || lower.contains("validated:") ||
      lower.contains("semantically equivalent") || lower.contains("semantic equivalence") ||
      lower.contains("validation complete") || lower.contains("verification complete") ||
      lower.contains("no semantic loss") ||
      lower.contains("all criteria met") || lower.contains("all post-conditions") ||
      lower.contains("all acceptance criteria");
  }
}

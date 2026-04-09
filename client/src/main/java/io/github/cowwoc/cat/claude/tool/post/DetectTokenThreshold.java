/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool.post;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.PostToolHandler;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PostToolUse handler that warns when approaching the context token limit.
 * <p>
 * Reads {@code ~/.claude/sessions/{sessionId}.json} for token usage data. Warns at 60k tokens
 * (soft warning) and 80k tokens (strong warning).
 */
public final class DetectTokenThreshold implements PostToolHandler
{
  private static final long SOFT_WARNING_THRESHOLD = 60_000;
  private static final long STRONG_WARNING_THRESHOLD = 80_000;

  private final ClaudeHook scope;

  /**
   * Creates a new detect-token-threshold handler.
   *
   * @param scope the hook scope providing configuration paths and services
   * @throws NullPointerException if {@code scope} is null
   */
  public DetectTokenThreshold(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    Path sessionFile = scope.getClaudeConfigPath().resolve("sessions").resolve(sessionId + ".json");
    if (!Files.exists(sessionFile))
      return Result.allow();

    long totalTokens;
    try
    {
      totalTokens = readTotalTokens(sessionFile);
    }
    catch (IOException _)
    {
      return Result.allow();
    }

    if (totalTokens > STRONG_WARNING_THRESHOLD)
    {
      return Result.context("""
        <system-reminder>
        WARNING: Token usage is very high (%d tokens). You are approaching the context limit.
        Consider wrapping up your current task and committing progress soon.
        If the task is not yet complete, focus on the most critical remaining work.
        </system-reminder>""".formatted(totalTokens));
    }
    if (totalTokens > SOFT_WARNING_THRESHOLD)
    {
      return Result.context("""
        <system-reminder>
        NOTE: Token usage is elevated (%d tokens). Continue working normally, but be aware
        that you are past the halfway point of the typical context window.
        </system-reminder>""".formatted(totalTokens));
    }
    return Result.allow();
  }

  /**
   * Reads total token usage from a session file.
   *
   * @param sessionFile the path to the session JSON file
   * @return the total token count, or 0 if the data is missing or malformed
   * @throws IOException if the file cannot be read
   */
  long readTotalTokens(Path sessionFile) throws IOException
  {
    String content = Files.readString(sessionFile);
    JsonNode root = scope.getJsonMapper().readTree(content);

    JsonNode tokenUsage = root.get("token_usage");
    if (tokenUsage == null)
      tokenUsage = root.get("usage");
    if (tokenUsage == null)
      return 0;

    long total = 0;
    JsonNode inputTokens = tokenUsage.get("input_tokens");
    if (inputTokens != null && inputTokens.isNumber())
      total += inputTokens.asLong();

    JsonNode outputTokens = tokenUsage.get("output_tokens");
    if (outputTokens != null && outputTokens.isNumber())
      total += outputTokens.asLong();

    return total;
  }
}

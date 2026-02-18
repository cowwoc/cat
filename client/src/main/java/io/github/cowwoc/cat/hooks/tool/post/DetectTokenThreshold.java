/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.tool.post;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

  private final Path claudeConfigDir;

  /**
   * Creates a new detect-token-threshold handler.
   *
   * @param claudeConfigDir the Claude config directory path for resolving session files
   * @throws NullPointerException if {@code claudeConfigDir} is null
   */
  public DetectTokenThreshold(Path claudeConfigDir)
  {
    requireThat(claudeConfigDir, "claudeConfigDir").isNotNull();
    this.claudeConfigDir = claudeConfigDir;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    Path sessionFile = claudeConfigDir.resolve("sessions").resolve(sessionId + ".json");
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
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode root = mapper.readTree(content);

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

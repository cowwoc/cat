/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Output generator for /cat:get-subagent-status skill.
 * <p>
 * Collects subagent worktree status, parses git worktree list to find subagent worktrees,
 * reads completion markers and session files to compute token counts, and outputs structured JSON results.
 */
public final class GetSubagentStatusOutput implements SkillOutput
{
  private static final int THRESHOLD_TOKENS = 80_000;
  private static final Pattern SUB_PATTERN = Pattern.compile("sub-([a-f0-9]+)");

  /**
   * Status of a subagent.
   */
  public enum SubagentStatus
  {
    /** Subagent is currently running. */
    RUNNING("running"),
    /** Subagent has completed. */
    COMPLETE("complete"),
    /** Subagent is approaching token threshold. */
    WARNING("warning");

    private final String jsonValue;

    SubagentStatus(String jsonValue)
    {
      this.jsonValue = jsonValue;
    }

    /**
     * Returns the JSON string representation.
     *
     * @return the JSON value
     */
    public String toJson()
    {
      return jsonValue;
    }
  }

  /**
   * Subagent status information.
   *
   * @param id the subagent ID (short hash)
   * @param task the Claude Task tool name (not a CAT issue ID)
   * @param status the status
   * @param tokens the token count
   * @param compactions the compaction event count
   * @param worktree the worktree path
   */
  public record SubagentInfo(
    String id,
    String task,
    SubagentStatus status,
    int tokens,
    int compactions,
    String worktree)
  {
    /**
     * Creates a new subagent info record.
     *
     * @param id the subagent ID (short hash)
     * @param task the Claude Task tool name (not a CAT issue ID)
     * @param status the status
     * @param tokens the token count
     * @param compactions the compaction event count
     * @param worktree the worktree path
     * @throws NullPointerException if {@code id}, {@code task}, {@code status}, or {@code worktree} is null
     * @throws IllegalArgumentException if {@code tokens} or {@code compactions} is negative
     */
    public SubagentInfo
    {
      requireThat(id, "id").isNotNull();
      requireThat(task, "task").isNotNull();
      requireThat(status, "status").isNotNull();
      requireThat(tokens, "tokens").isNotNegative();
      requireThat(compactions, "compactions").isNotNegative();
      requireThat(worktree, "worktree").isNotNull();
    }
  }

  /**
   * Summary of subagent status results.
   *
   * @param total total number of subagents found
   * @param running number of running subagents
   * @param complete number of complete subagents
   * @param warning number of subagents approaching token threshold
   */
  public record Summary(int total, int running, int complete, int warning)
  {
    /**
     * Creates a new summary record.
     *
     * @param total total number of subagents found
     * @param running number of running subagents
     * @param complete number of complete subagents
     * @param warning number of subagents approaching token threshold
     * @throws IllegalArgumentException if any count is negative
     */
    public Summary
    {
      requireThat(total, "total").isNotNegative();
      requireThat(running, "running").isNotNegative();
      requireThat(complete, "complete").isNotNegative();
      requireThat(warning, "warning").isNotNegative();
    }
  }

  /**
   * Result of subagent status report.
   *
   * @param subagents list of subagent information
   * @param summary summary statistics
   */
  public record StatusResult(List<SubagentInfo> subagents, Summary summary)
  {
    /**
     * Creates a new monitor result.
     *
     * @param subagents list of subagent information
     * @param summary summary statistics
     * @throws NullPointerException if {@code subagents} or {@code summary} is null
     */
    public StatusResult
    {
      requireThat(subagents, "subagents").isNotNull();
      requireThat(summary, "summary").isNotNull();
    }

    /**
     * Converts this result to JSON format.
     *
     * @param mapper the JSON mapper to use
     * @return JSON string representation
     * @throws NullPointerException if {@code mapper} is null
     * @throws IOException if JSON conversion fails
     */
    public String toJson(JsonMapper mapper) throws IOException
    {
      requireThat(mapper, "mapper").isNotNull();
      ObjectNode root = mapper.createObjectNode();

      ArrayNode subagentsArray = mapper.createArrayNode();
      for (SubagentInfo info : subagents)
      {
        ObjectNode subagentNode = mapper.createObjectNode();
        subagentNode.put("id", info.id());
        subagentNode.put("task", info.task());
        subagentNode.put("status", info.status().toJson());
        subagentNode.put("tokens", info.tokens());
        subagentNode.put("compactions", info.compactions());
        subagentNode.put("worktree", info.worktree());
        subagentsArray.add(subagentNode);
      }
      root.set("subagents", subagentsArray);

      ObjectNode summaryNode = mapper.createObjectNode();
      summaryNode.put("total", summary.total());
      summaryNode.put("running", summary.running());
      summaryNode.put("complete", summary.complete());
      summaryNode.put("warning", summary.warning());
      root.set("summary", summaryNode);

      return mapper.writeValueAsString(root);
    }
  }

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetSubagentStatusOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetSubagentStatusOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();

    JsonMapper mapper = scope.getJsonMapper();
    String sessionBase = scope.getSessionBasePath().toString();
    StatusResult result = getStatus(sessionBase, mapper);
    return result.toJson(mapper);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: {@code get-subagent-status [--session-base DIR]}
   * <p>
   * Outputs JSON with status of all active subagents to stdout.
   *
   * @param args command-line arguments
   * @throws IOException if git operations or file reading fails
   */
  public static void main(String[] args) throws IOException
  {
    String sessionBaseOverride = "";

    for (int i = 0; i < args.length - 1; ++i)
    {
      if (args[i].equals("--session-base"))
      {
        ++i;
        sessionBaseOverride = args[i];
      }
    }

    try (MainJvmScope scope = new MainJvmScope())
    {
      // Use scope-provided session base path if not overridden via --session-base
      String sessionBase;
      if (!sessionBaseOverride.isEmpty())
        sessionBase = sessionBaseOverride;
      else
        sessionBase = scope.getSessionBasePath().toString();

      try
      {
        JsonMapper mapper = scope.getJsonMapper();
        StatusResult result = getStatus(sessionBase, mapper);
        System.out.println(result.toJson(mapper));
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "ERROR",
            "message": "%s"
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
      }
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Unexpected error: %s"
        }""".formatted(e.getMessage().replace("\"", "\\\"")));
      System.exit(1);
    }
  }

  /**
   * Reports the status of all subagent worktrees.
   *
   * @param sessionBase the session files base directory (e.g., "/home/node/.config/claude/projects/-workspace")
   * @param mapper the JSON mapper to use for parsing completion files
   * @return the monitoring result
   * @throws NullPointerException if {@code sessionBase} or {@code mapper} is null
   * @throws IOException if git operations or file reading fails
   */
  public static StatusResult getStatus(String sessionBase, JsonMapper mapper) throws IOException
  {
    requireThat(sessionBase, "sessionBase").isNotNull();
    requireThat(mapper, "mapper").isNotNull();

    List<SubagentInfo> subagents = new ArrayList<>();
    int total = 0;
    int running = 0;
    int complete = 0;
    int warning = 0;

    List<String> worktrees = getWorktreeList();

    for (String worktreePath : worktrees)
    {
      if (!worktreePath.contains("-sub-"))
        continue;

      ++total;

      String subagentId = extractSubagentId(worktreePath);
      String taskName = extractTaskName(worktreePath);

      Path completionFile = Paths.get(worktreePath, ".completion.json");
      SubagentStatus status;
      int tokens;
      int compactions;

      if (Files.exists(completionFile))
      {
        status = SubagentStatus.COMPLETE;
        ++complete;

        String completionJson = Files.readString(completionFile, StandardCharsets.UTF_8);
        JsonNode completionData = mapper.readTree(completionJson);
        if (completionData.has("tokensUsed"))
          tokens = completionData.get("tokensUsed").asInt(0);
        else
          tokens = 0;
        if (completionData.has("compactionEvents"))
          compactions = completionData.get("compactionEvents").asInt(0);
        else
          compactions = 0;
      }
      else
      {
        status = SubagentStatus.RUNNING;
        ++running;
        tokens = 0;
        compactions = 0;

        Path sessionIdFile = Paths.get(worktreePath, ".session_id");
        if (Files.exists(sessionIdFile))
        {
          String sessionId = Files.readString(sessionIdFile, StandardCharsets.UTF_8).strip();
          Path sessionFile = Paths.get(sessionBase, sessionId + ".jsonl");

          if (Files.exists(sessionFile))
          {
            TokenCounts counts = countTokensAndCompactions(sessionFile, mapper);
            tokens = counts.tokens();
            compactions = counts.compactions();
          }
        }

        if (tokens >= THRESHOLD_TOKENS)
        {
          status = SubagentStatus.WARNING;
          ++warning;
          --running;
        }
      }

      subagents.add(new SubagentInfo(subagentId, taskName, status, tokens, compactions, worktreePath));
    }

    Summary summary = new Summary(total, running, complete, warning);
    return new StatusResult(subagents, summary);
  }

  /**
   * Gets the list of worktree paths from git.
   *
   * @return list of worktree paths
   * @throws IOException if git command fails
   */
  private static List<String> getWorktreeList() throws IOException
  {
    List<String> worktrees = new ArrayList<>();
    ProcessBuilder pb = new ProcessBuilder("git", "worktree", "list", "--porcelain");
    pb.redirectErrorStream(true);
    Process process = pb.start();

    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      String line = reader.readLine();
      while (line != null)
      {
        if (line.startsWith("worktree "))
        {
          String path = line.substring("worktree ".length());
          worktrees.add(path);
        }
        line = reader.readLine();
      }
    }

    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git worktree list", e);
    }

    if (exitCode != 0)
      throw new IOException("git worktree list failed with exit code " + exitCode);

    return worktrees;
  }

  /**
   * Extracts the subagent ID from a worktree path.
   *
   * @param worktreePath the worktree path
   * @return the subagent ID (short hash), or "unknown" if not found
   */
  private static String extractSubagentId(String worktreePath)
  {
    Matcher matcher = SUB_PATTERN.matcher(worktreePath);
    String lastMatch = "unknown";
    while (matcher.find())
    {
      lastMatch = matcher.group(1);
    }
    return lastMatch;
  }

  /**
   * Extracts the task name from a worktree path.
   *
   * @param worktreePath the worktree path
   * @return the task name
   */
  private static String extractTaskName(String worktreePath)
  {
    String basename = Paths.get(worktreePath).getFileName().toString();
    int subIndex = basename.indexOf("-sub-");
    if (subIndex > 0)
      return basename.substring(0, subIndex);
    return basename;
  }

  /**
   * Token and compaction counts.
   *
   * @param tokens total token count
   * @param compactions total compaction events
   */
  private record TokenCounts(int tokens, int compactions)
  {
    /**
     * Creates a new token counts record.
     *
     * @param tokens total token count
     * @param compactions total compaction events
     * @throws AssertionError if {@code tokens} or {@code compactions} are negative
     */
    TokenCounts
    {
      assert that(tokens, "tokens").isNotNegative().elseThrow();
      assert that(compactions, "compactions").isNotNegative().elseThrow();
    }
  }

  /**
   * Counts tokens and compaction events from a session file.
   *
   * @param sessionFile the session JSONL file
   * @param mapper the JSON mapper to use for parsing
   * @return the token and compaction counts
   * @throws IOException if reading or parsing fails
   */
  private static TokenCounts countTokensAndCompactions(Path sessionFile, JsonMapper mapper) throws IOException
  {
    int totalTokens = 0;
    int compactionCount = 0;

    List<String> lines = Files.readAllLines(sessionFile, StandardCharsets.UTF_8);
    for (String line : lines)
    {
      if (line.isBlank())
        continue;

      JsonNode node = mapper.readTree(line);
      String type = "";
      if (node.has("type"))
      {
        String typeValue = node.get("type").asString();
        if (typeValue != null)
          type = typeValue;
      }

      if ("assistant".equals(type) && node.has("message") && node.get("message").has("usage"))
      {
        JsonNode usage = node.get("message").get("usage");
        int inputTokens;
        if (usage.has("input_tokens"))
          inputTokens = usage.get("input_tokens").asInt(0);
        else
          inputTokens = 0;
        int outputTokens;
        if (usage.has("output_tokens"))
          outputTokens = usage.get("output_tokens").asInt(0);
        else
          outputTokens = 0;
        totalTokens += inputTokens + outputTokens;
      }
      else if ("summary".equals(type))
      {
        ++compactionCount;
      }
    }

    return new TokenCounts(totalTokens, compactionCount);
  }
}

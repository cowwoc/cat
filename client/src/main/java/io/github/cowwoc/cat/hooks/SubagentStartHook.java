/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.ClearSkillMarker;
import io.github.cowwoc.cat.hooks.session.InjectCatAgentId;
import io.github.cowwoc.cat.hooks.util.RulesDiscovery;
import io.github.cowwoc.cat.hooks.util.SkillDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SubagentStart hook handler.
 * <p>
 * Injects the full model-invocable skill listing and audience-filtered CAT rules into subagent context
 * when a subagent starts. This ensures subagents know which skills are available and receive the
 * appropriate rules for their type without relying on static frontmatter preloading.
 * <p>
 * Each skill entry uses the format {@code "- name: description"}, matching Claude Code's native skill
 * listing. The header instructs subagents to use the Skill tool for invoking skills and includes
 * behavioral instructions about when to invoke them.
 * <p>
 * CAT rules are filtered using the {@code subAgents} frontmatter property: omitting {@code subAgents}
 * (or not providing frontmatter) reaches all subagents, {@code subAgents: []} excludes all subagents,
 * and specific types like {@code subAgents: ["cat:work-execute"]} target only matching subagents.
 */
public final class SubagentStartHook implements HookHandler
{
  private final JvmScope scope;

  /**
   * Creates a new SubagentStartHook.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SubagentStartHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the SubagentStart hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    HookRunner.execute(SubagentStartHook::new, args);
  }

  /**
   * Processes the SubagentStart hook by injecting the agent ID, skill listing, and CAT rules as
   * additional context.
   *
   * @param input  the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output with the agent ID, skill listing, and rules
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    StringBuilder combinedContext = new StringBuilder();
    List<String> warnings = new ArrayList<>();

    String sessionId = input.getSessionId();
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session_id is blank. SubagentStart hook requires a valid session ID.");
    }
    String agentId = input.getAgentId();
    if (agentId.isBlank())
    {
      throw new IllegalArgumentException(
        "agent_id is blank. SubagentStart hook requires a valid agent ID.");
    }

    String clearWarning = new ClearSkillMarker(scope).clearSubagentMarker(sessionId, agentId);
    if (!clearWarning.isEmpty())
      warnings.add(clearWarning);

    combinedContext.append(InjectCatAgentId.getSubagentContext(sessionId, agentId));

    String skillListing = SkillDiscovery.getSubagentSkillListing(scope);
    if (!skillListing.isEmpty())
    {
      if (!combinedContext.isEmpty())
        combinedContext.append("\n\n");
      combinedContext.append(skillListing);
    }

    String catRules = getCatRules(input);
    if (!catRules.isEmpty())
    {
      if (!combinedContext.isEmpty())
        combinedContext.append("\n\n");
      combinedContext.append(catRules);
    }

    String worktreeRemovalInstructions = getWorktreeRemovalInstructions(input);
    if (!worktreeRemovalInstructions.isEmpty())
    {
      if (!combinedContext.isEmpty())
        combinedContext.append("\n\n");
      combinedContext.append(worktreeRemovalInstructions);
    }

    if (combinedContext.isEmpty())
      return new HookResult(output.empty(), warnings);
    return new HookResult(output.additionalContext("SubagentStart",
      combinedContext.toString()), warnings);
  }

  /**
   * Discovers and returns CAT rules applicable to this subagent.
   *
   * @param input the hook input containing the subagent type
   * @return the filtered rule content, or empty string if no rules apply
   */
  private String getCatRules(HookInput input)
  {
    String subagentType = input.getString("subagent_type", "");
    if (subagentType.isBlank())
    {
      Logger log = LoggerFactory.getLogger(SubagentStartHook.class);
      log.debug("SubagentStart hook received blank subagent_type; rules requiring a specific " +
        "subagent type will not match");
    }

    Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
    // Rules with paths: restrictions are injected dynamically by InjectPathRules (PreToolUse hook)
    // when matching files are accessed. For subagents, only non-paths rules are injected at start.
    return RulesDiscovery.getCatRulesForAudience(rulesDir, scope.getYamlMapper(),
      (rules, activeFiles) -> RulesDiscovery.filterForSubagent(rules, subagentType, activeFiles),
      List.of());
  }

  /**
   * Returns worktree removal safety instructions for this subagent.
   * <p>
   * Subagents need to know to prefix `git worktree remove` and `rm -rf` commands with
   * their CAT agent ID (in the format `{sessionId}/subagents/{id}`).
   * <p>
   * Only injects instructions when the project is a valid CAT project (has `.claude/cat` directory),
   * since the worktree removal safety only applies in CAT project contexts.
   *
   * @param input the hook input containing the agent_id field from Claude
   * @return the worktree removal safety instructions
   */
  private String getWorktreeRemovalInstructions(HookInput input)
  {
    String agentId = input.getString("agent_id", "");
    if (agentId.isBlank())
      return "";

    // Only inject if this is a valid CAT project
    Path catDir = scope.getClaudeProjectDir().resolve(".claude").resolve("cat");
    if (!Files.isDirectory(catDir))
      return "";  // Not a CAT project, skip safety instructions

    return "## Worktree Removal Safety (CAT_AGENT_ID Protocol)\n" +
      "**MANDATORY**: When removing a worktree or its directory, prefix the command with your CAT agent ID.\n" +
      "\n" +
      "**Why**: The hook identifies which agent owns a lock by matching `CAT_AGENT_ID` against the lock \n" +
      "file's `worktrees` map values. Without this prefix, the hook cannot verify ownership and will block the \n" +
      "command as a fail-safe.\n" +
      "\n" +
      "**Format**: `CAT_AGENT_ID=<your-cat-agent-id> git worktree remove <path>`\n" +
      "**Format**: `CAT_AGENT_ID=<your-cat-agent-id> rm -rf <path>`\n" +
      "\n" +
      "**Your CAT agent ID**: " + agentId + "\n" +
      "\n" +
      "**Example**:\n" +
      "```bash\n" +
      "CAT_AGENT_ID=" + agentId + " git worktree remove \\\n" +
      "  /workspace/.claude/cat/worktrees/my-issue\n" +
      "```\n" +
      "\n" +
      "**When NOT required**: Only needed when removing worktrees or directories that may be locked.\n" +
      "Regular file deletions, `git clean`, and other operations do not need this prefix.";
  }
}

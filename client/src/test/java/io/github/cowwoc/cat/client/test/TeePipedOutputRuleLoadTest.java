/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.session.InjectMainAgentRules;
import io.github.cowwoc.cat.claude.hook.session.SessionStartHandler;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Agent-compliance tests for the tee-piped-output rule.
 *
 * <p>Verifies that the rule in plugin/rules/tee-piped-output.md is properly loaded and injected
 * into the main agent context with correct frontmatter and content.
 */
public final class TeePipedOutputRuleLoadTest
{
  /**
   * Verifies that the tee-piped-output rule is loaded and injected into the main agent context.
   *
   * <p>The rule must be present in plugin/rules/tee-piped-output.md with mainAgent: true
   * frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void teePipedOutputRuleLoadedInMainAgent() throws IOException
  {
    Path projectPath = Files.createTempDirectory("tee-rule-main-project-");
    Path pluginRoot = Files.createTempDirectory("tee-rule-main-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
    {
      Path rulesDir = scope.getPluginRoot().resolve("rules");
      Files.createDirectories(rulesDir);

      Files.writeString(rulesDir.resolve("tee-piped-output.md"), """
        ---
        mainAgent: true
        ---
        ## Tee Piped Process Output

        **MANDATORY:** When running a Bash command that contains a pipe (`|`), insert `tee` to capture the full output.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules();
      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").
        contains("Tee Piped Process Output");
      requireThat(result.additionalContext(), "additionalContext").
        contains("insert `tee` to capture the full output");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }


  /**
   * Verifies that the tee-piped-output rule includes the complete mktemp and cleanup pattern.
   *
   * <p>The rule must include: create temp file with mktemp, use tee to capture output, and
   * cleanup with rm -f.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void teePipedOutputRuleIncludesCompletePattern() throws IOException
  {
    Path projectPath = Files.createTempDirectory("tee-pattern-project-");
    Path pluginRoot = Files.createTempDirectory("tee-pattern-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
    {
      Path rulesDir = scope.getPluginRoot().resolve("rules");
      Files.createDirectories(rulesDir);

      Files.writeString(rulesDir.resolve("tee-piped-output.md"), """
        ---
        mainAgent: true
        ---
        ## Tee Piped Process Output

        **Pattern:**

        ```bash
        # 1. Create a temporary log file
        LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)

        # 2. Capture full output before the pipe
        some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"

        # 3. Later, re-filter without re-running the command
        grep -i "error" "$LOG_FILE"
        tail -50 "$LOG_FILE"

        # Cleanup
        rm -f "$LOG_FILE"
        ```
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules();
      SessionStartHandler.Result result = handler.handle(scope);

      String context = result.additionalContext();
      requireThat(context, "context").contains("mktemp /tmp/cmd-output-XXXXXX.log");
      requireThat(context, "context").contains("some-command 2>&1 | tee");
      requireThat(context, "context").contains("$LOG_FILE");
      requireThat(context, "context").contains("rm -f");
      requireThat(context, "context").contains("Cleanup");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}

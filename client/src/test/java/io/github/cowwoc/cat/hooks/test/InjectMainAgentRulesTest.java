/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.InjectMainAgentRules;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for InjectMainAgentRules.handle() behavior.
 */
public final class InjectMainAgentRulesTest
{
  /**
   * Verifies that handle() returns a non-empty context when the rules directory contains a rule
   * with mainAgent:true.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithMainAgentTrueRuleReturnsContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create the rules directory inside the project dir (scope.getClaudeProjectDir())
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-rule.md"), """
        ---
        mainAgent: true
        subAgents: []
        ---
        # Main agent rule content
        Important instruction for the main agent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      String hookJson = "{\"session_id\": \"test-session\"}";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Main agent rule content");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Important instruction for the main agent.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the rules directory does not exist.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithMissingRulesDirReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-empty-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // No rules directory created — getCatRulesForAudience will find no rules
      JsonMapper mapper = scope.getJsonMapper();
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      String hookJson = "{\"session_id\": \"test-session\"}";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the rules directory exists but all rules
   * have mainAgent:false (no rules pass the main-agent filter).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithAllSubagentOnlyRulesReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-subonly-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      // No subAgents frontmatter → null → targets all subagents; mainAgent: false excludes main agent
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        mainAgent: false
        ---
        # Only for subagents
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      String hookJson = "{\"session_id\": \"test-session\"}";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the constructor throws NullPointerException when scope is null.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testConstructorWithNullScopeThrowsNullPointerException()
  {
    new InjectMainAgentRules(null);
  }

  /**
   * Verifies that handle() throws NullPointerException when input is null.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testHandleWithNullInputThrowsNullPointerException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-null-input-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);
      handler.handle(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns only mainAgent:true rules when both mainAgent:true and
   * mainAgent:false rules are present.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testHandleWithMixedMainAgentRulesFiltersCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-mixed-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-only.md"), """
        ---
        mainAgent: true
        ---
        # Main agent exclusive content
        Only the main agent should see this.
        """);
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        mainAgent: false
        ---
        # Subagent exclusive content
        Only subagents should see this.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      String hookJson = "{\"session_id\": \"test-session\"}";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Main agent exclusive content");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Only the main agent should see this.");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Subagent exclusive content");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Only subagents should see this.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

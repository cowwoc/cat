/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.InjectSubAgentRules;
import io.github.cowwoc.cat.hooks.session.SubagentStartHandler;
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
 * Tests for InjectSubAgentRules.handle() behavior.
 */
public final class InjectSubAgentRulesTest
{
  private HookInput createInput(JsonMapper mapper, String json)
  {
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    return HookInput.readFrom(mapper, stream);
  }

  /**
   * Verifies that handle() returns rules with no subAgents restriction when subagent_type is blank.
   * Rules with null subAgents should reach all subagents regardless of type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesBlankSubagentTypeMatchesAllSubagentRule() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-blank-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      // No subAgents frontmatter → null → matches all subagents
      Files.writeString(rulesDir.resolve("universal.md"), """
        ---
        mainAgent: false
        ---
        # Universal subagent content
        Applies to any subagent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      // Blank subagent_type
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Universal subagent content");
      requireThat(result.additionalContext(), "additionalContext").contains("Applies to any subagent.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns rules matching the specific subagent_type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesPopulatedSubagentTypeMatchesSpecificRule() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-specific-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute specific content
        Only for cat:work-execute.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Work execute specific content");
      requireThat(result.additionalContext(), "additionalContext").contains("Only for cat:work-execute.");
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
  public void getRulesEmptyRulesDirReturnsEmptyString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-empty-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // No rules directory created
      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

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
    new InjectSubAgentRules(null);
  }

  /**
   * Verifies that handle() throws NullPointerException when input is null.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testGetRulesWithNullInputThrowsNullPointerException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-null-input-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      handler.handle(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when subAgents is an empty list, regardless
   * of the subagent_type requested.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesEmptySubagentsExcludesAll() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-empty-subagents-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      // subAgents: [] means no subagent type matches — exclude all
      Files.writeString(rulesDir.resolve("excluded-rule.md"), """
        ---
        mainAgent: false
        subAgents: []
        ---
        # Excluded content
        This rule should not reach any subagent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the subagent_type does not match the
   * specific type listed in the rule's subAgents frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesTypeDoesNotMatchDifferentSpecificType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-type-mismatch-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".claude/cat/rules");
      Files.createDirectories(rulesDir);
      // Rule targets cat:work-execute only
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute only content
        Only for cat:work-execute subagents.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      // Request as a different subagent type
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:git-commit\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

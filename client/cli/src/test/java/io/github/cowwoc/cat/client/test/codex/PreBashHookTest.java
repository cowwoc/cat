/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.codex.hook.PreBashHook;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Tests Codex pre-bash hook input parsing.
 */
public final class PreBashHookTest
{
  /**
   * Returns native Codex command payload shapes that the pre-bash hook supports.
   *
   * @return native Codex command payloads
   */
  @DataProvider
  public Object[][] commandPayloadProvider()
  {
    return new Object[][]
      {
        {"""
          {"arguments":{"cmd":"git config user.email alice@example.com"}}
          """},
        {"""
          {"arguments":{"command":"git config user.email alice@example.com"}}
          """},
        {"""
          {"input":{"cmd":"git config user.email alice@example.com"}}
          """},
        {"""
          {"input":{"command":"git config user.email alice@example.com"}}
          """},
        {"""
          {"tool_input":{"cmd":"git config user.email alice@example.com"}}
          """},
        {"""
          {"tool_input":{"command":"git config user.email alice@example.com"}}
          """},
        {"""
          {"cmd":"git config user.email alice@example.com"}
          """},
        {"""
          {"command":"git config user.email alice@example.com"}
          """}
      };
  }

  /**
   * Verifies that Codex pre-bash returns the neutral response for a non-blocked command.
   */
  @Test
  public void preBashAllowsNonBlockedCommand()
  {
    String output = runPreBash("""
      {
        "thread_id": "thread-1",
        "tool_name": "functions.exec_command",
        "arguments": {
          "cmd": "git status --short"
        }
      }
      """);

    requireThat(output, "output").isEqualTo("{}\n");
  }

  /**
   * Verifies that Codex pre-bash blocks mixed-case git identity writes.
   */
  @Test
  public void preBashBlocksMixedCaseIdentityWrite()
  {
    String output = runPreBash("""
      {
        "thread_id": "thread-1",
        "tool_name": "functions.exec_command",
        "arguments": {
          "cmd": "GIT CONFIG User.Email alice@example.com"
        }
      }
      """);

    requireThat(output, "output").contains("\"decision\":\"block\"");
    requireThat(output, "output").contains("user.email");
  }

  /**
   * Verifies that every supported native Codex command payload shape reaches the guard.
   *
   * @param input the native Codex hook input
   */
  @Test(dataProvider = "commandPayloadProvider")
  public void preBashBlocksEverySupportedCommandPayloadShape(String input)
  {
    String output = runPreBash(input);

    requireThat(output, "output").contains("\"decision\":\"block\"");
    requireThat(output, "output").contains("user.email");
  }

  /**
   * Verifies that blank input emits the neutral response.
   */
  @Test
  public void preBashAllowsBlankInput()
  {
    String output = runPreBash("");

    requireThat(output, "output").isEqualTo("{}\n");
  }

  /**
   * Verifies that payloads without a command emit the neutral response.
   */
  @Test
  public void preBashAllowsPayloadWithoutCommand()
  {
    String output = runPreBash("""
      {
        "thread_id": "thread-1",
        "tool_name": "functions.exec_command",
        "arguments": {
          "cwd": "/workspace"
        }
      }
      """);

    requireThat(output, "output").isEqualTo("{}\n");
  }

  /**
   * Verifies that Codex pre-bash rejects malformed JSON hook input.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*malformed JSON.*")
  public void preBashRejectsMalformedJson()
  {
    runPreBash("{");
  }

  /**
   * Verifies that Codex pre-bash rejects over-limit hook input before parsing.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*exceeds maximum.*")
  public void preBashRejectsOverLimitInput()
  {
    runPreBash(" ".repeat(1_048_577));
  }

  /**
   * Verifies that Codex pre-bash accepts a payload at the exact hook input size limit.
   */
  @Test
  public void preBashAcceptsExactLimitInput()
  {
    String output = runPreBash(sizedPayload(1_048_576));

    requireThat(output, "output").isEqualTo("{}\n");
  }

  /**
   * Verifies that Codex pre-bash accepts a payload immediately below the hook input size limit.
   */
  @Test
  public void preBashAcceptsJustUnderLimitInput()
  {
    String output = runPreBash(sizedPayload(1_048_575));

    requireThat(output, "output").isEqualTo("{}\n");
  }

  /**
   * Verifies that Codex pre-bash rejects unexpected launcher arguments.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unexpected arguments.*")
  public void preBashRejectsUnexpectedArgs()
  {
    ByteArrayInputStream in = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8))
    {
      PreBashHook.run(new String[]{"unexpected"}, in, out);
    }
  }

  /**
   * Runs the Codex pre-bash hook with the supplied native input.
   *
   * @param input the native hook input
   * @return the hook standard output
   */
  private static String runPreBash(String input)
  {
    ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8))
    {
      PreBashHook.run(new String[0], in, out);
    }
    return stdout.toString(StandardCharsets.UTF_8);
  }

  /**
   * Creates a valid JSON payload with exactly the requested UTF-8 byte length.
   *
   * @param size the target size in bytes
   * @return a JSON hook payload
   */
  private static String sizedPayload(int size)
  {
    String prefix = "{\"arguments\":{\"cmd\":\"git status --short\"},\"padding\":\"";
    String suffix = "\"}";
    int paddingLength = size - prefix.length() - suffix.length();
    requireThat(paddingLength, "paddingLength").isGreaterThanOrEqualTo(0);
    return prefix + "a".repeat(paddingLength) + suffix;
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.HookRegistrar;
import io.github.cowwoc.cat.hooks.util.HookRegistrar.Config;
import io.github.cowwoc.cat.hooks.util.HookRegistrar.HookTrigger;
import io.github.cowwoc.cat.hooks.util.HookRegistrar.Result;
import io.github.cowwoc.cat.hooks.util.OperationStatus;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for HookRegistrar.
 * <p>
 * Tests verify hook registration functionality, configuration validation, security checks, and JSON output.
 */
public class HookRegistrarTest
{
  /**
   * Verifies that Config validates null name.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*name.*")
  public void configValidatesNullName()
  {
    new Config(null, HookTrigger.PRE_TOOL_USE, "Bash", false, "#!/bin/bash\necho test");
  }

  /**
   * Verifies that Config validates null trigger.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*trigger.*")
  public void configValidatesNullTrigger()
  {
    new Config("test-hook", null, "Bash", false, "#!/bin/bash\necho test");
  }

  /**
   * Verifies that Config validates null matcher.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*matcher.*")
  public void configValidatesNullMatcher()
  {
    new Config("test-hook", HookTrigger.PRE_TOOL_USE, null, false, "#!/bin/bash\necho test");
  }

  /**
   * Verifies that Config validates null scriptContent.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scriptContent.*")
  public void configValidatesNullScriptContent()
  {
    new Config("test-hook", HookTrigger.PRE_TOOL_USE, "Bash", false, null);
  }

  /**
   * Verifies that register rejects script without shebang.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerRejectsMissingShebang() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config("test-hook", HookTrigger.PRE_TOOL_USE, "", false, "echo test");
      Result result = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

      requireThat(result.status(), "status").isEqualTo(OperationStatus.ERROR);
      requireThat(result.message(), "message").contains("shebang");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that register detects curl pipe sh pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerDetectsCurlPipeSh() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config(
        "test-hook",
        HookTrigger.PRE_TOOL_USE,
        "",
        false,
        "#!/bin/bash\ncurl http://example.com/script.sh | sh");
      Result result = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

      requireThat(result.status(), "status").isEqualTo(OperationStatus.ERROR);
      requireThat(result.message(), "message").contains("BLOCKED");
      requireThat(result.message(), "message").contains("curl | sh");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that register detects rm rf root pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerDetectsRmRfRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config(
        "test-hook",
        HookTrigger.PRE_TOOL_USE,
        "",
        false,
        "#!/bin/bash\nrm -rf /var");
      Result result = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

      requireThat(result.status(), "status").isEqualTo(OperationStatus.ERROR);
      requireThat(result.message(), "message").contains("BLOCKED");
      requireThat(result.message(), "message").contains("rm -rf /");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that register detects eval dollar pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerDetectsEvalDollar() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config(
        "test-hook",
        HookTrigger.PRE_TOOL_USE,
        "",
        false,
        "#!/bin/bash\neval $COMMAND");
      Result result = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

      requireThat(result.status(), "status").isEqualTo(OperationStatus.ERROR);
      requireThat(result.message(), "message").contains("BLOCKED");
      requireThat(result.message(), "message").contains("eval $");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that register successfully creates and registers a valid hook.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerCreatesValidHook() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config(
        "test-hook",
        HookTrigger.PRE_TOOL_USE,
        "Bash",
        false,
        "#!/bin/bash\nset -euo pipefail\necho \"Hook executed\"");
      Result result = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

      requireThat(result.status(), "status").isEqualTo(OperationStatus.SUCCESS);
      requireThat(result.hookName(), "hookName").isEqualTo("test-hook");
      requireThat(result.triggerEvent(), "triggerEvent").isEqualTo(HookTrigger.PRE_TOOL_USE);
      requireThat(result.matcher(), "matcher").isEqualTo("Bash");
      requireThat(result.executable(), "executable").isTrue();
      requireThat(result.registered(), "registered").isTrue();
      requireThat(result.restartRequired(), "restartRequired").isTrue();

      Path hookFile = tempDir.resolve("hooks/test-hook.sh");
      requireThat(Files.exists(hookFile), "hookExists").isTrue();
      requireThat(Files.isExecutable(hookFile), "isExecutable").isTrue();

      String content = Files.readString(hookFile, StandardCharsets.UTF_8);
      requireThat(content, "content").contains("#!/bin/bash");
      requireThat(content, "content").contains("Hook executed");

      Path settingsFile = tempDir.resolve("settings.json");
      requireThat(Files.exists(settingsFile), "settingsExists").isTrue();

      JsonNode settings = scope.getJsonMapper().readTree(Files.readString(settingsFile, StandardCharsets.UTF_8));
      requireThat(settings.has("hooks"), "hasHooks").isTrue();
      requireThat(settings.get("hooks").has("PreToolUse"), "hasPreToolUse").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that register rejects duplicate hook.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void registerRejectsDuplicateHook() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Config config = new Config(
        "test-hook",
        HookTrigger.PRE_TOOL_USE,
        "",
        false,
        "#!/bin/bash\necho test");

      Result result1 = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());
      requireThat(result1.status(), "status1").isEqualTo(OperationStatus.SUCCESS);

      Result result2 = HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());
      requireThat(result2.status(), "status2").isEqualTo(OperationStatus.ERROR);
      requireThat(result2.message(), "message").contains("already exists");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Result.toJson produces valid JSON.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void resultToJsonProducesValidJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      Result result = new Result(
        OperationStatus.SUCCESS,
        "Hook registered",
        "test-hook",
        "/path/to/hook.sh",
        HookTrigger.PRE_TOOL_USE,
        "Bash",
        true,
        true,
        true,
        "Use Bash tool",
        "2024-01-01T00:00:00Z");

      String json = result.toJson(scope.getJsonMapper());

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"success\"");
      requireThat(json, "json").contains("\"hook_name\"");
      requireThat(json, "json").contains("\"test-hook\"");
      requireThat(json, "json").contains("\"trigger_event\"");
      requireThat(json, "json").contains("\"PreToolUse\"");
      requireThat(json, "json").contains("\"matcher\"");
      requireThat(json, "json").contains("\"Bash\"");
      requireThat(json, "json").contains("\"executable\"");
      requireThat(json, "json").contains("true");

      JsonNode parsed = scope.getJsonMapper().readTree(json);
      requireThat(parsed.get("status").asString(), "status").isEqualTo("success");
      requireThat(parsed.get("hook_name").asString(), "hookName").isEqualTo("test-hook");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Result validates null status.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*status.*")
  public void resultValidatesNullStatus()
  {
    new Result(
      null, "msg", "hook", "/path", HookTrigger.PRE_TOOL_USE, "", false, false, false, "cmd",
      "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null message.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*message.*")
  public void resultValidatesNullMessage()
  {
    new Result(
      OperationStatus.SUCCESS, null, "hook", "/path", HookTrigger.PRE_TOOL_USE, "", false, false, false, "cmd",
      "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null hookName.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*hookName.*")
  public void resultValidatesNullHookName()
  {
    new Result(
      OperationStatus.SUCCESS, "msg", null, "/path", HookTrigger.PRE_TOOL_USE, "", false, false, false, "cmd",
      "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null matcher.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*matcher.*")
  public void resultValidatesNullMatcher()
  {
    new Result(
      OperationStatus.SUCCESS, "msg", "hook", "/path", HookTrigger.PRE_TOOL_USE, null, false, false, false, "cmd",
      "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result allows empty matcher.
   */
  @Test
  public void resultAllowsEmptyMatcher()
  {
    Result result = new Result(
      OperationStatus.SUCCESS,
      "msg",
      "hook",
      "/path",
      HookTrigger.PRE_TOOL_USE,
      "",
      false,
      false,
      false,
      "cmd",
      "2024-01-01T00:00:00Z");
    requireThat(result.matcher(), "matcher").isEmpty();
  }

  /**
   * Verifies that run() writes a block response to stdout when registration fails (duplicate hook).
   * Non-success results must use HookOutput.block() format, not the custom Result JSON.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runWritesBlockResponseOnNonSuccess() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-registrar-run-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        // Register the hook once successfully first
        Config config = new Config(
          "test-hook",
          HookTrigger.PRE_TOOL_USE,
          "",
          false,
          "#!/bin/bash\necho test");
        HookRegistrar.register(config, tempDir.toString(), scope.getJsonMapper());

        // Now try to register the same hook again — should fail with ERROR status
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String[] args = {
          "--name", "test-hook",
          "--trigger", "PreToolUse",
          "--script-content", "#!/bin/bash\necho test",
          "--claude-dir", tempDir.toString()
        };
        HookRegistrar.run(scope, args, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        // Non-success must produce block response, not custom status JSON
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("\"reason\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that run() writes success JSON to stdout when registration succeeds.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runWritesSuccessJsonOnSuccess() throws IOException
  {
    Path tempDir = Files.createTempDirectory("hook-registrar-run-test-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String[] args = {
          "--name", "test-hook",
          "--trigger", "PreToolUse",
          "--script-content", "#!/bin/bash\necho test",
          "--claude-dir", tempDir.toString()
        };
        HookRegistrar.run(scope, args, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"success\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
}

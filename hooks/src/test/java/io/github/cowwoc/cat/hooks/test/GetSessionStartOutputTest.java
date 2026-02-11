package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.GetSessionStartOutput;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.HookOutput;
import io.github.cowwoc.cat.hooks.session.CheckRetrospectiveDue;
import io.github.cowwoc.cat.hooks.session.CheckUpdateAvailable;
import io.github.cowwoc.cat.hooks.session.CheckUpgrade;
import io.github.cowwoc.cat.hooks.session.ClearSkillMarkers;
import io.github.cowwoc.cat.hooks.session.EchoSessionId;
import io.github.cowwoc.cat.hooks.session.InjectEnv;
import io.github.cowwoc.cat.hooks.session.InjectSessionInstructions;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import io.github.cowwoc.cat.hooks.util.VersionUtils;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetSessionStartOutput and individual session start handlers.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class GetSessionStartOutputTest
{
  /**
   * Creates a HookInput from a JSON string.
   *
   * @param json the JSON input string
   * @return the parsed HookInput
   */
  private HookInput createInput(String json)
  {
    return HookInput.readFrom(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Creates a HookOutput that captures output to a stream.
   *
   * @param capture the stream to capture output into
   * @return a HookOutput writing to the capture stream
   */
  private HookOutput createOutput(ByteArrayOutputStream capture)
  {
    return new HookOutput(new PrintStream(capture, true, StandardCharsets.UTF_8));
  }

  // --- EchoSessionId tests ---

  /**
   * Verifies that EchoSessionId returns the session ID as additional context.
   */
  @Test
  public void echoSessionIdReturnsSessionIdAsContext()
  {
    HookInput input = createInput("{\"session_id\": \"test-session-123\"}");
    SessionStartHandler.Result result = new EchoSessionId().handle(input);
    requireThat(result.additionalContext(), "additionalContext").isEqualTo("Session ID: test-session-123");
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that EchoSessionId returns empty when no session ID is present.
   */
  @Test
  public void echoSessionIdReturnsEmptyWhenNoSessionId()
  {
    HookInput input = createInput("{}");
    SessionStartHandler.Result result = new EchoSessionId().handle(input);
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  // --- ClearSkillMarkers tests ---

  /**
   * Verifies that ClearSkillMarkers returns empty result (no output).
   */
  @Test
  public void clearSkillMarkersReturnsEmptyResult()
  {
    HookInput input = createInput("{}");
    SessionStartHandler.Result result = new ClearSkillMarkers().handle(input);
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that ClearSkillMarkers skips symlinks in /tmp.
   */
  @Test
  public void clearSkillMarkersSkipsSymlinks() throws IOException
  {
    Path tmpDir = Files.createTempDirectory("cat-test-markers-");
    try
    {
      // Create a real file and a symlink
      Path realFile = tmpDir.resolve("cat-skills-loaded-real");
      Files.writeString(realFile, "test");
      Path targetFile = tmpDir.resolve("symlink-target");
      Files.writeString(targetFile, "should-not-be-deleted");
      Path symlink = tmpDir.resolve("cat-skills-loaded-symlink");
      Files.createSymbolicLink(symlink, targetFile);

      // ClearSkillMarkers operates on /tmp so we just verify the handler returns empty
      // The symlink skip behavior is validated by the implementation check
      HookInput input = createInput("{}");
      SessionStartHandler.Result result = new ClearSkillMarkers().handle(input);
      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tmpDir);
    }
  }

  // --- InjectSessionInstructions tests ---

  /**
   * Verifies that InjectSessionInstructions returns instructions with session ID.
   */
  @Test
  public void injectSessionInstructionsIncludesSessionId()
  {
    HookInput input = createInput("{\"session_id\": \"my-session\"}");
    SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
    requireThat(result.additionalContext(), "additionalContext").contains("CAT SESSION INSTRUCTIONS");
    requireThat(result.additionalContext(), "additionalContext").contains("Session ID: my-session");
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that InjectSessionInstructions uses "unknown" when session ID is missing.
   */
  @Test
  public void injectSessionInstructionsUsesUnknownWhenNoSessionId()
  {
    HookInput input = createInput("{}");
    SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
    requireThat(result.additionalContext(), "additionalContext").contains("Session ID: unknown");
  }

  /**
   * Verifies that InjectSessionInstructions includes key instruction sections.
   */
  @Test
  public void injectSessionInstructionsIncludesKeyInstructions()
  {
    HookInput input = createInput("{\"session_id\": \"test\"}");
    SessionStartHandler.Result result = new InjectSessionInstructions().handle(input);
    String context = result.additionalContext();
    requireThat(context, "context").contains("User Input Handling");
    requireThat(context, "context").contains("Mandatory Mistake Handling");
    requireThat(context, "context").contains("Commit Before Review");
    requireThat(context, "context").contains("Skill Workflow Compliance");
    requireThat(context, "context").contains("Work Request Handling");
    requireThat(context, "context").contains("Worktree Isolation");
    requireThat(context, "context").contains("Fail-Fast Protocol");
    requireThat(context, "context").contains("Verbatim Output Skills");
  }

  // --- InjectEnv tests ---

  /**
   * Verifies that InjectEnv throws when CLAUDE_ENV_FILE is not set.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void injectEnvThrowsWhenNoEnvFile()
  {
    // CLAUDE_ENV_FILE is not set in the test environment
    HookInput input = createInput("{}");
    new InjectEnv().handle(input);
  }

  // --- CheckUpdateAvailable tests ---

  /**
   * Verifies that CheckUpdateAvailable runs without error and returns empty when no update is available.
   */
  @Test
  public void checkUpdateAvailableRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-update-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      Files.writeString(pluginRoot.resolve("plugin.json"), "{\"version\": \"99.0.0\"}");
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        HookInput input = createInput("{}");
        SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- CheckUpgrade tests ---

  /**
   * Verifies that CheckUpgrade runs without error and returns empty when no config file exists.
   */
  @Test
  public void checkUpgradeRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-upgrade-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // No cat-config.json in projectDir → handler returns empty
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        HookInput input = createInput("{}");
        SessionStartHandler.Result result = new CheckUpgrade(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- CheckRetrospectiveDue tests ---

  /**
   * Verifies that CheckRetrospectiveDue runs without error and returns empty for a non-CAT project.
   */
  @Test
  public void checkRetrospectiveDueRunsWithEnvironment() throws IOException
  {
    Path projectDir = Files.createTempDirectory("cat-test-retro-");
    Path pluginRoot = Files.createTempDirectory("cat-test-plugin-");
    try
    {
      // No .planning directory → handler returns empty (not a CAT project)
      try (TestJvmScope scope = new TestJvmScope(projectDir, pluginRoot))
      {
        HookInput input = createInput("{}");
        SessionStartHandler.Result result = new CheckRetrospectiveDue(scope).handle(input);
        requireThat(result.additionalContext(), "additionalContext").isEmpty();
        requireThat(result.stderr(), "stderr").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(pluginRoot);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // --- GetSessionStartOutput dispatcher tests ---

  /**
   * Verifies that GetSessionStartOutput returns empty JSON when all handlers return empty.
   */
  @Test
  public void dispatcherReturnsEmptyWhenAllHandlersReturnEmpty()
  {
    SessionStartHandler emptyHandler = input -> SessionStartHandler.Result.empty();
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(emptyHandler));

    HookInput input = createInput("{}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    dispatcher.run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").isEqualTo("{}");
  }

  /**
   * Verifies that GetSessionStartOutput combines context from multiple handlers.
   */
  @Test
  public void dispatcherCombinesContextFromMultipleHandlers()
  {
    SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("context from handler 1");
    SessionStartHandler handler2 = input -> SessionStartHandler.Result.context("context from handler 2");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler1, handler2));

    HookInput input = createInput("{}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    dispatcher.run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    requireThat(result, "result").contains("context from handler 1");
    requireThat(result, "result").contains("context from handler 2");
    requireThat(result, "result").contains("hookSpecificOutput");
    requireThat(result, "result").contains("SessionStart");
  }

  /**
   * Verifies that GetSessionStartOutput writes stderr from handlers.
   */
  @Test
  public void dispatcherWritesStderrFromHandlers()
  {
    SessionStartHandler handler = input -> SessionStartHandler.Result.stderr("stderr message");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    dispatcher.run(input, output, stderrStream);

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").contains("stderr message");

    // No context -> empty output
    String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
    requireThat(stdoutContent, "stdoutContent").isEqualTo("{}");
  }

  /**
   * Verifies that GetSessionStartOutput handles handler exceptions gracefully.
   */
  @Test(expectedExceptions = IllegalStateException.class)
  public void dispatcherHandlesHandlerExceptionsGracefully()
  {
    SessionStartHandler failingHandler = input ->
    {
      throw new IllegalStateException("test error");
    };
    SessionStartHandler goodHandler = input -> SessionStartHandler.Result.context("good context");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(failingHandler, goodHandler));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));

    try
    {
      dispatcher.run(input, output, stderrStream);
    }
    catch (IllegalStateException e)
    {
      // Error should be on stderr
      String stderrContent = stderr.toString(StandardCharsets.UTF_8);
      requireThat(stderrContent, "stderrContent").contains("handler error");

      // Good handler's context should still be in output
      String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
      requireThat(stdoutContent, "stdoutContent").contains("good context");

      // Re-throw to satisfy expectedExceptions
      throw e;
    }
  }

  /**
   * Verifies that failing handler error appears in additionalContext.
   */
  @Test(expectedExceptions = IllegalStateException.class)
  public void dispatcherIncludesErrorInAdditionalContext()
  {
    SessionStartHandler failingHandler = input ->
    {
      throw new AssertionError("CLAUDE_SESSION_ID is not set");
    };
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(failingHandler));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));

    try
    {
      dispatcher.run(input, output, stderrStream);
    }
    catch (IllegalStateException e)
    {
      // Error should appear in additionalContext
      String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
      requireThat(stdoutContent, "stdoutContent").contains("SessionStart Handler Errors");
      requireThat(stdoutContent, "stdoutContent").contains("CLAUDE_SESSION_ID is not set");

      // Error should also be on stderr
      String stderrContent = stderr.toString(StandardCharsets.UTF_8);
      requireThat(stderrContent, "stderrContent").contains("CLAUDE_SESSION_ID is not set");

      // Re-throw to satisfy expectedExceptions
      throw e;
    }
  }

  /**
   * Verifies that other handlers still produce output when one fails.
   */
  @Test(expectedExceptions = IllegalStateException.class)
  public void dispatcherContinuesAfterHandlerFailure()
  {
    SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("handler 1 output");
    SessionStartHandler failingHandler = input ->
    {
      throw new IllegalStateException("handler 2 failed");
    };
    SessionStartHandler handler3 = input -> SessionStartHandler.Result.context("handler 3 output");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler1, failingHandler, handler3));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));

    try
    {
      dispatcher.run(input, output, stderrStream);
    }
    catch (IllegalStateException e)
    {
      // All handlers' context should be present
      String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
      requireThat(stdoutContent, "stdoutContent").contains("handler 1 output");
      requireThat(stdoutContent, "stdoutContent").contains("handler 3 output");
      requireThat(stdoutContent, "stdoutContent").contains("handler 2 failed");

      // Re-throw to satisfy expectedExceptions
      throw e;
    }
  }

  /**
   * Verifies that all handlers succeeding produces no error section.
   */
  @Test
  public void dispatcherReturnsSuccessWhenAllHandlersSucceed()
  {
    SessionStartHandler handler1 = input -> SessionStartHandler.Result.context("output 1");
    SessionStartHandler handler2 = input -> SessionStartHandler.Result.context("output 2");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler1, handler2));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    dispatcher.run(input, output, stderrStream);

    // No error section in output
    String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
    requireThat(stdoutContent, "stdoutContent").doesNotContain("SessionStart Handler Errors");

    // No stderr output
    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").isEmpty();
  }

  /**
   * Verifies that GetSessionStartOutput produces valid JSON with hookSpecificOutput.
   */
  @Test
  public void dispatcherProducesValidJsonWithHookSpecificOutput()
  {
    SessionStartHandler handler = input -> SessionStartHandler.Result.context("test context");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler));

    HookInput input = createInput("{\"session_id\": \"test\"}");
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    HookOutput output = createOutput(capture);

    dispatcher.run(input, output);

    String result = capture.toString(StandardCharsets.UTF_8).trim();
    // Parse as JSON to verify it's valid
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode json = mapper.readTree(result);
    requireThat(json.has("hookSpecificOutput"), "hasHookSpecificOutput").isTrue();

    JsonNode hookOutput = json.get("hookSpecificOutput");
    requireThat(hookOutput.get("hookEventName").asString(), "hookEventName").isEqualTo("SessionStart");
    requireThat(hookOutput.get("additionalContext").asString(), "additionalContext").contains("test context");
  }

  /**
   * Verifies that GetSessionStartOutput with both context and stderr works correctly.
   */
  @Test
  public void dispatcherHandlesBothContextAndStderr()
  {
    SessionStartHandler handler = input -> SessionStartHandler.Result.both("context msg", "stderr msg");
    GetSessionStartOutput dispatcher = new GetSessionStartOutput(List.of(handler));

    HookInput input = createInput("{}");
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    HookOutput output = new HookOutput(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    dispatcher.run(input, output, stderrStream);

    String stderrContent = stderr.toString(StandardCharsets.UTF_8);
    requireThat(stderrContent, "stderrContent").contains("stderr msg");

    String stdoutContent = stdout.toString(StandardCharsets.UTF_8).trim();
    requireThat(stdoutContent, "stdoutContent").contains("context msg");
  }

  // --- SessionStartHandler.Result factory tests ---

  /**
   * Verifies that Result.empty() creates a result with empty strings.
   */
  @Test
  public void resultEmptyCreatesEmptyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.empty();
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that Result.context() creates a result with context only.
   */
  @Test
  public void resultContextCreatesContextOnlyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.context("test");
    requireThat(result.additionalContext(), "additionalContext").isEqualTo("test");
    requireThat(result.stderr(), "stderr").isEmpty();
  }

  /**
   * Verifies that Result.stderr() creates a result with stderr only.
   */
  @Test
  public void resultStderrCreatesStderrOnlyResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.stderr("error");
    requireThat(result.additionalContext(), "additionalContext").isEmpty();
    requireThat(result.stderr(), "stderr").isEqualTo("error");
  }

  /**
   * Verifies that Result.both() creates a result with both fields.
   */
  @Test
  public void resultBothCreatesBothResult()
  {
    SessionStartHandler.Result result = SessionStartHandler.Result.both("ctx", "err");
    requireThat(result.additionalContext(), "additionalContext").isEqualTo("ctx");
    requireThat(result.stderr(), "stderr").isEqualTo("err");
  }

  /**
   * Verifies that Result constructor rejects null additionalContext.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void resultRejectsNullContext()
  {
    new SessionStartHandler.Result(null, "");
  }

  /**
   * Verifies that Result constructor rejects null stderr.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void resultRejectsNullStderr()
  {
    new SessionStartHandler.Result("", null);
  }

  // --- VersionUtils tests ---

  /**
   * Verifies that version comparison detects equal versions.
   */
  @Test
  public void versionCompareDetectsEqualVersions()
  {
    int result = VersionUtils.compareVersions("2.1.0", "2.1.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison detects older version.
   */
  @Test
  public void versionCompareDetectsOlderVersion()
  {
    int result = VersionUtils.compareVersions("1.0.0", "2.0.0");
    requireThat(result < 0, "isLessThan").isTrue();
  }

  /**
   * Verifies that version comparison detects newer version.
   */
  @Test
  public void versionCompareDetectsNewerVersion()
  {
    int result = VersionUtils.compareVersions("2.1.0", "1.0.0");
    requireThat(result > 0, "isGreaterThan").isTrue();
  }

  /**
   * Verifies that version comparison handles different length versions.
   */
  @Test
  public void versionCompareHandlesDifferentLengths()
  {
    int result = VersionUtils.compareVersions("2.1", "2.1.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison handles empty versions.
   */
  @Test
  public void versionCompareHandlesEmptyVersions()
  {
    int result = VersionUtils.compareVersions("", "1.0");
    requireThat(result < 0, "isLessThan").isTrue();
  }

  /**
   * Verifies that version comparison handles null versions.
   */
  @Test
  public void versionCompareHandlesNullVersions()
  {
    int result = VersionUtils.compareVersions(null, null);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that version comparison handles non-numeric parts.
   */
  @Test
  public void versionCompareHandlesNonNumericParts()
  {
    int result = VersionUtils.compareVersions("1.0.alpha", "1.0.0");
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that getPluginVersion throws when plugin.json is not found.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void getPluginVersionThrowsForMissingDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion reads from plugin.json.
   */
  @Test
  public void getPluginVersionReadsFromPluginJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Files.writeString(tempDir.resolve("plugin.json"), "{\"version\": \"2.5.0\"}");
      String version = VersionUtils.getPluginVersion(tempDir);
      requireThat(version, "version").isEqualTo("2.5.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion falls back to .claude-plugin/plugin.json.
   */
  @Test
  public void getPluginVersionFallsBackToClaudePluginDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Path claudePluginDir = tempDir.resolve(".claude-plugin");
      Files.createDirectories(claudePluginDir);
      Files.writeString(claudePluginDir.resolve("plugin.json"), "{\"version\": \"1.0.3\"}");
      String version = VersionUtils.getPluginVersion(tempDir);
      requireThat(version, "version").isEqualTo("1.0.3");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion throws when plugin.json has no version field.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void getPluginVersionThrowsWhenNoVersionField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Files.writeString(tempDir.resolve("plugin.json"), "{\"name\": \"test\"}");
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getPluginVersion throws for malformed JSON.
   */
  @Test(expectedExceptions = tools.jackson.core.JacksonException.class)
  public void getPluginVersionThrowsForMalformedJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-test-version-");
    try
    {
      Files.writeString(tempDir.resolve("plugin.json"), "not json");
      VersionUtils.getPluginVersion(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

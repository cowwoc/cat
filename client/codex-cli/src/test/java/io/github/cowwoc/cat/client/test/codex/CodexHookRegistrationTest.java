/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Tests Codex hook registration behavior.
 */
public final class CodexHookRegistrationTest
{
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  /**
   * Verifies that Codex pre-bash registration matches native Codex command tool names.
   *
   * @throws IOException if the hooks file cannot be read
   */
  @Test
  public void preBashMatcherAcceptsNativeCodexCommand() throws IOException
  {
    JsonNode hooks = readHooksJson().get("hooks");
    JsonNode registrations = hooks.get("PreToolUse");

    requireThat(registrations.size(), "preToolUseRegistrationCount").isEqualTo(1);
    JsonNode registration = registrations.get(0);
    Pattern matcher = Pattern.compile(registration.get("matcher").asString());

    requireThat(matcher.matcher("Bash").matches(), "bashMatches").isTrue();
    requireThat(matcher.matcher("functions.exec_command").matches(), "execCommandMatches").isTrue();
    requireThat(matcher.matcher("Read").matches(), "readDoesNotMatch").isFalse();
    requireThat(matcher.matcher("Glob").matches(), "globDoesNotMatch").isFalse();
    requireThat(matcher.matcher("grep").matches(), "grepDoesNotMatch").isFalse();
    requireThat(matcher.matcher("custom.tool").matches(), "customToolDoesNotMatch").isFalse();
    requireThat(registration.get("hooks").get(0).get("command").asString(), "preBashCommand").
      isEqualTo("${CAT_PLUGIN_ROOT}/client/bin/pre-bash");
  }

  /**
   * Verifies that Codex only registers hook events it currently supports.
   *
   * @throws IOException if the hooks file cannot be read
   */
  @Test
  public void codexRegistersOnlySupportedHookEvents() throws IOException
  {
    JsonNode hooks = readHooksJson().get("hooks");

    requireThat(hooks.has("SessionStart"), "hasSessionStart").isTrue();
    requireThat(hooks.has("SubagentStart"), "hasSubagentStart").isTrue();
    requireThat(hooks.has("PreToolUse"), "hasPreToolUse").isTrue();
    requireThat(hooks.has("UserPromptSubmit"), "hasUserPromptSubmit").isFalse();
    requireThat(hooks.has("PostToolUse"), "hasPostToolUse").isFalse();
    requireThat(hooks.has("Stop"), "hasStop").isFalse();
    requireThat(hooks.size(), "hookEventCount").isEqualTo(3);
  }

  /**
   * Verifies that Codex subagent-start registration uses the native launcher.
   *
   * @throws IOException if the hooks file cannot be read
   */
  @Test
  public void subagentStartRegistrationUsesNative() throws IOException
  {
    JsonNode registration = readHooksJson().get("hooks").get("SubagentStart").get(0);

    requireThat(registration.has("matcher"), "subagentStartMatcher").isFalse();
    requireThat(registration.get("hooks").get(0).get("command").asString(),
      "subagentStartCommand").isEqualTo("${CAT_PLUGIN_ROOT}/client/bin/subagent-start");
  }

  /**
   * Verifies that Codex session-start registration uses the native launcher.
   *
   * @throws IOException if the hooks file cannot be read
   */
  @Test
  public void sessionStartRegistrationUsesNative() throws IOException
  {
    JsonNode registration = readHooksJson().get("hooks").get("SessionStart").get(0);
    Pattern matcher = Pattern.compile(registration.get("matcher").asString());

    requireThat(matcher.matcher("startup").matches(), "startupMatches").isTrue();
    requireThat(matcher.matcher("resume").matches(), "resumeMatches").isTrue();
    requireThat(matcher.matcher("clear").matches(), "clearMatches").isTrue();
    requireThat(matcher.matcher("other").matches(), "otherDoesNotMatch").isFalse();
    requireThat(registration.get("hooks").get(0).get("command").asString(), "sessionStartCommand").
      isEqualTo("${CAT_PLUGIN_ROOT}/client/bin/session-start");
  }

  /**
   * Reads the Codex hook registration file from the test checkout.
   *
   * @return the parsed hooks.json file
   * @throws IOException if the hooks file cannot be read
   */
  private static JsonNode readHooksJson() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path hooksJson = repoRoot.resolve("plugin/hooks/codex/hooks.json");
    return JSON_MAPPER.readTree(Files.readString(hooksJson, StandardCharsets.UTF_8));
  }
}

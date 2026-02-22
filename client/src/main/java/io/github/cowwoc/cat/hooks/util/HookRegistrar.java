/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.MainJvmScope;
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
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hook registration utility for Claude Code.
 * <p>
 * Creates hook scripts with proper error handling and registers them in settings.json.
 * Validates script content for security issues before registration.
 */
public final class HookRegistrar
{
  /**
   * Hook trigger events.
   */
  public enum HookTrigger
  {
    /** Triggered when a session starts. */
    SESSION_START("SessionStart"),
    /** Triggered when user submits a prompt. */
    USER_PROMPT_SUBMIT("UserPromptSubmit"),
    /** Triggered before a tool is used. */
    PRE_TOOL_USE("PreToolUse"),
    /** Triggered after a tool is used. */
    POST_TOOL_USE("PostToolUse"),
    /** Triggered before context compaction. */
    PRE_COMPACT("PreCompact");

    private final String jsonValue;

    HookTrigger(String jsonValue)
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

  private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
    Pattern.compile("curl[^|]*\\|[^|]*(ba)?sh"),
    Pattern.compile("wget[^|]*\\|[^|]*(ba)?sh"),
    Pattern.compile("rm\\s+-rf\\s+/[^.]"),
    Pattern.compile("eval\\s+\\$"),
    Pattern.compile("exec\\s+<"));

  /**
   * Hook registration configuration.
   *
   * @param name hook name (will be used as filename: name.sh)
   * @param trigger trigger event
   * @param matcher tool pattern to match (empty string for no matcher)
   * @param canBlock if true, hook can block tool execution
   * @param scriptContent the bash script content for the hook
   */
  public record Config(
    String name,
    HookTrigger trigger,
    String matcher,
    boolean canBlock,
    String scriptContent)
  {
    /**
     * Creates a new hook registration configuration.
     *
     * @param name hook name (will be used as filename: name.sh)
     * @param trigger trigger event
     * @param matcher tool pattern to match (empty string for no matcher)
     * @param canBlock if true, hook can block tool execution
     * @param scriptContent the bash script content for the hook
     * @throws NullPointerException if {@code name}, {@code trigger}, {@code matcher}, or {@code scriptContent}
     *   is null
     */
    public Config
    {
      requireThat(name, "name").isNotNull();
      requireThat(trigger, "trigger").isNotNull();
      requireThat(matcher, "matcher").isNotNull();
      requireThat(scriptContent, "scriptContent").isNotNull();
    }
  }

  /**
   * Result of hook registration.
   *
   * @param status the operation status
   * @param message result message
   * @param hookName the hook name
   * @param hookPath the hook file path
   * @param triggerEvent the trigger event
   * @param matcher the tool matcher (empty string if none)
   * @param executable whether the hook is executable
   * @param registered whether the hook was registered
   * @param restartRequired whether Claude Code restart is required
   * @param testCommand suggested test command
   * @param timestamp ISO-8601 timestamp
   */
  public record Result(
    OperationStatus status,
    String message,
    String hookName,
    String hookPath,
    HookTrigger triggerEvent,
    String matcher,
    boolean executable,
    boolean registered,
    boolean restartRequired,
    String testCommand,
    String timestamp)
  {
    /**
     * Creates a new hook registration result.
     *
     * @param status the operation status
     * @param message result message
     * @param hookName the hook name
     * @param hookPath the hook file path
     * @param triggerEvent the trigger event
     * @param matcher the tool matcher (empty string if none)
     * @param executable whether the hook is executable
     * @param registered whether the hook was registered
     * @param restartRequired whether Claude Code restart is required
     * @param testCommand suggested test command
     * @param timestamp ISO-8601 timestamp
     * @throws NullPointerException if {@code status}, {@code triggerEvent}, or any string parameter is null
     */
    public Result
    {
      requireThat(status, "status").isNotNull();
      requireThat(message, "message").isNotNull();
      requireThat(hookName, "hookName").isNotNull();
      requireThat(hookPath, "hookPath").isNotNull();
      requireThat(triggerEvent, "triggerEvent").isNotNull();
      requireThat(matcher, "matcher").isNotNull();
      requireThat(testCommand, "testCommand").isNotNull();
      requireThat(timestamp, "timestamp").isNotNull();
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
      root.put("status", status.toJson());
      root.put("message", message);
      root.put("hook_name", hookName);
      root.put("hook_path", hookPath);
      root.put("trigger_event", triggerEvent.toJson());
      root.put("matcher", matcher);
      root.put("executable", executable);
      root.put("registered", registered);
      root.put("restart_required", restartRequired);
      root.put("test_command", testCommand);
      root.put("timestamp", timestamp);
      return mapper.writeValueAsString(root);
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private HookRegistrar()
  {
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: {@code register-hook --name NAME --trigger EVENT [--matcher PATTERN] [--can-block]
   * --script-content CONTENT [--claude-dir DIR]}
   * <p>
   * Registers a hook script and outputs JSON result to stdout.
   *
   * @param args command-line arguments
   * @throws IOException if file operations fail
   */
  public static void main(String[] args) throws IOException
  {
    String name = "";
    String trigger = "";
    String matcher = "";
    boolean canBlock = false;
    String scriptContent = "";
    String claudeDir = System.getProperty("user.home") + "/.claude";

    for (int i = 0; i < args.length; ++i)
    {
      if (i + 1 < args.length)
      {
        switch (args[i])
        {
          case "--name" ->
          {
            ++i;
            name = args[i];
          }
          case "--trigger" ->
          {
            ++i;
            trigger = args[i];
          }
          case "--matcher" ->
          {
            ++i;
            matcher = args[i];
          }
          case "--script-content" ->
          {
            ++i;
            scriptContent = args[i];
          }
          case "--claude-dir" ->
          {
            ++i;
            claudeDir = args[i];
          }
          default ->
          {
            if (args[i].equals("--can-block"))
              canBlock = true;
          }
        }
      }
      else if (args[i].equals("--can-block"))
      {
        canBlock = true;
      }
    }

    if (name.isEmpty() || trigger.isEmpty() || scriptContent.isEmpty())
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Usage: register-hook --name NAME --trigger EVENT --script-content CONTENT \
[--matcher PATTERN] [--can-block] [--claude-dir DIR]"
        }""");
      System.exit(1);
      return;
    }

    HookTrigger hookTrigger;
    try
    {
      hookTrigger = switch (trigger)
      {
        case "SessionStart" -> HookTrigger.SESSION_START;
        case "UserPromptSubmit" -> HookTrigger.USER_PROMPT_SUBMIT;
        case "PreToolUse" -> HookTrigger.PRE_TOOL_USE;
        case "PostToolUse" -> HookTrigger.POST_TOOL_USE;
        case "PreCompact" -> HookTrigger.PRE_COMPACT;
        default -> throw new IllegalArgumentException(
          "Invalid trigger: '" + trigger + "'. Expected: SessionStart, UserPromptSubmit, " +
            "PreToolUse, PostToolUse, or PreCompact");
      };
    }
    catch (IllegalArgumentException e)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "%s"
        }""".formatted(e.getMessage().replace("\"", "\\\"")));
      System.exit(1);
      return;
    }

    Config config = new Config(name, hookTrigger, matcher, canBlock, scriptContent);
    try (MainJvmScope scope = new MainJvmScope())
    {
      Result result = register(config, claudeDir, scope.getJsonMapper());
      System.out.println(result.toJson(scope.getJsonMapper()));
      if (result.status() != OperationStatus.SUCCESS)
        System.exit(1);
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

  /**
   * Registers a hook script.
   *
   * @param config the hook registration configuration
   * @param claudeDir the Claude configuration directory (typically ~/.claude)
   * @param mapper the JSON mapper to use for reading and writing settings.json
   * @return the registration result
   * @throws NullPointerException if {@code config}, {@code claudeDir}, or {@code mapper} is null
   * @throws IOException if file operations fail
   */
  public static Result register(Config config, String claudeDir, JsonMapper mapper) throws IOException
  {
    requireThat(config, "config").isNotNull();
    requireThat(claudeDir, "claudeDir").isNotNull();
    requireThat(mapper, "mapper").isNotNull();

    String timestamp = Instant.now().toString();

    if (!config.scriptContent().startsWith("#!"))
    {
      return new Result(
        OperationStatus.ERROR,
        "Script must start with shebang (e.g., #!/bin/bash)",
        config.name(),
        "",
        config.trigger(),
        config.matcher(),
        false,
        false,
        false,
        "",
        timestamp);
    }

    String securityError = checkDangerousPatterns(config.scriptContent());
    if (!securityError.isEmpty())
    {
      return new Result(
        OperationStatus.ERROR,
        "BLOCKED: " + securityError,
        config.name(),
        "",
        config.trigger(),
        config.matcher(),
        false,
        false,
        false,
        "",
        timestamp);
    }

    String syntaxError = checkSyntax(config.scriptContent());
    if (!syntaxError.isEmpty())
    {
      return new Result(
        OperationStatus.ERROR,
        "Script has syntax errors: " + syntaxError,
        config.name(),
        "",
        config.trigger(),
        config.matcher(),
        false,
        false,
        false,
        "",
        timestamp);
    }

    Path hooksDir = Paths.get(claudeDir, "hooks");
    Files.createDirectories(hooksDir);

    Path hookPath = hooksDir.resolve(config.name() + ".sh");
    if (Files.exists(hookPath))
    {
      return new Result(
        OperationStatus.ERROR,
        "Hook already exists: " + hookPath,
        config.name(),
        hookPath.toString(),
        config.trigger(),
        config.matcher(),
        false,
        false,
        false,
        "",
        timestamp);
    }

    Files.writeString(hookPath, config.scriptContent(), StandardCharsets.UTF_8);

    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(hookPath);
    perms.add(PosixFilePermission.OWNER_EXECUTE);
    Files.setPosixFilePermissions(hookPath, perms);

    if (!Files.isExecutable(hookPath))
    {
      return new Result(
        OperationStatus.ERROR,
        "Failed to make hook executable: " + hookPath,
        config.name(),
        hookPath.toString(),
        config.trigger(),
        config.matcher(),
        false,
        false,
        false,
        "",
        timestamp);
    }

    Path settingsFile = Paths.get(claudeDir, "settings.json");
    if (!Files.exists(settingsFile))
    {
      Files.writeString(settingsFile, "{}", StandardCharsets.UTF_8);
    }

    String settingsJson = Files.readString(settingsFile, StandardCharsets.UTF_8);
    JsonNode settings = mapper.readTree(settingsJson);
    ObjectNode settingsObj;
    if (settings.isObject())
    {
      settingsObj = (ObjectNode) settings;
    }
    else
    {
      settingsObj = mapper.createObjectNode();
    }

    if (!settingsObj.has("hooks"))
    {
      settingsObj.set("hooks", mapper.createObjectNode());
    }

    ObjectNode hooks = (ObjectNode) settingsObj.get("hooks");
    if (!hooks.has(config.trigger().toJson()))
    {
      hooks.set(config.trigger().toJson(), mapper.createArrayNode());
    }

    ArrayNode triggerArray = (ArrayNode) hooks.get(config.trigger().toJson());

    String hookCommand = "~/.claude/hooks/" + config.name() + ".sh";
    ObjectNode hookEntry = mapper.createObjectNode();

    if (!config.matcher().isEmpty())
    {
      hookEntry.put("matcher", config.matcher());
    }

    ArrayNode hooksArray = mapper.createArrayNode();
    ObjectNode hookDef = mapper.createObjectNode();
    hookDef.put("type", "command");
    hookDef.put("command", hookCommand);
    hooksArray.add(hookDef);

    hookEntry.set("hooks", hooksArray);
    triggerArray.add(hookEntry);

    String updatedSettings = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(settingsObj);
    Files.writeString(settingsFile, updatedSettings, StandardCharsets.UTF_8);

    try
    {
      mapper.readTree(Files.readString(settingsFile, StandardCharsets.UTF_8));
    }
    catch (IOException e)
    {
      return new Result(
        OperationStatus.ERROR,
        "Settings.json is invalid after update: " + e.getMessage(),
        config.name(),
        hookPath.toString(),
        config.trigger(),
        config.matcher(),
        true,
        false,
        false,
        "",
        timestamp);
    }

    String testCommand = getTestCommand(config.trigger(), config.matcher());

    return new Result(
      OperationStatus.SUCCESS,
      "Hook registered successfully",
      config.name(),
      hookPath.toString(),
      config.trigger(),
      config.matcher(),
      true,
      true,
      true,
      testCommand,
      timestamp);
  }

  /**
   * Checks for dangerous patterns in script content.
   *
   * @param scriptContent the script content to check
   * @return error message if dangerous pattern found, empty string otherwise
   */
  private static String checkDangerousPatterns(String scriptContent)
  {
    if (DANGEROUS_PATTERNS.get(0).matcher(scriptContent).find())
    {
      return "Script contains 'curl | sh' pattern (remote code execution risk)";
    }
    if (DANGEROUS_PATTERNS.get(1).matcher(scriptContent).find())
    {
      return "Script contains 'wget | sh' pattern (remote code execution risk)";
    }
    if (DANGEROUS_PATTERNS.get(2).matcher(scriptContent).find())
    {
      return "Script contains 'rm -rf /' pattern (destructive operation)";
    }
    if (DANGEROUS_PATTERNS.get(3).matcher(scriptContent).find())
    {
      return "Script contains 'eval $' pattern (arbitrary code execution)";
    }
    if (DANGEROUS_PATTERNS.get(4).matcher(scriptContent).find())
    {
      return "Script contains 'exec <' pattern (input redirection risk)";
    }
    return "";
  }

  /**
   * Checks script syntax using bash -n.
   *
   * @param scriptContent the script content to check
   * @return error message if syntax errors found, empty string otherwise
   */
  private static String checkSyntax(String scriptContent)
  {
    try
    {
      Path tempScript = Files.createTempFile("hook-check-", ".sh");
      try
      {
        Files.writeString(tempScript, scriptContent, StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder("bash", "-n", tempScript.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
          String line = reader.readLine();
          while (line != null)
          {
            if (output.length() > 0)
              output.append('\n');
            output.append(line);
            line = reader.readLine();
          }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0)
        {
          return output.toString();
        }

        return "";
      }
      finally
      {
        Files.deleteIfExists(tempScript);
      }
    }
    catch (IOException | InterruptedException e)
    {
      return "Failed to check syntax: " + e.getMessage();
    }
  }

  /**
   * Gets the test command for a trigger event.
   *
   * @param trigger the trigger event
   * @param matcher the tool matcher
   * @return the test command description
   */
  private static String getTestCommand(HookTrigger trigger, String matcher)
  {
    return switch (trigger)
    {
      case SESSION_START -> "Restart Claude Code";
      case USER_PROMPT_SUBMIT -> "Submit any prompt";
      case PRE_TOOL_USE, POST_TOOL_USE ->
      {
        if (matcher.isEmpty())
          yield "Use any tool";
        else
          yield "Use " + matcher + " tool";
      }
      case PRE_COMPACT -> "Wait for context compaction";
    };
  }
}

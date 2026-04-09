/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.util.GetSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Blocks guarded Bash commands unless the required skill has already been loaded in the current agent's
 * session.
 * <p>
 * A JSON registry file at {@code CLAUDE_PLUGIN_ROOT/config/skill-triggers.json} maps regex patterns
 * to required skill names. When a command matches a pattern, this handler checks whether the corresponding
 * skill is present in the agent's {@code skills-loaded} marker file. If the skill has not been loaded, the
 * command is blocked with an actionable error message.
 * <p>
 * This handler fails open: if the registry file or marker file cannot be read due to an I/O error, the
 * command is allowed to proceed. This prevents blocking legitimate work during setup errors.
 */
public final class RequireSkillForCommand implements BashHandler
{
  private final Logger log = LoggerFactory.getLogger(RequireSkillForCommand.class);
  private final ClaudeHook scope;
  private final List<GuardEntry> guards;

  /**
   * A mapping from a compiled regex pattern to the required fully qualified skill name.
   *
   * @param pattern the compiled regex pattern to match against bash commands
   * @param skill the fully qualified skill name (with plugin prefix) required when the pattern matches
   *   (e.g. {@code marketplaces:git-rebase-agent})
   */
  private record GuardEntry(Pattern pattern, String skill)
  {
  }

  /**
   * Creates a new handler that reads the registry from the plugin root at construction time.
   *
   * @param scope the JVM scope providing access to shared resources including the plugin root and JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public RequireSkillForCommand(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.guards = loadGuards();
  }

  /**
   * Loads guard entries from the skill-triggers.json file.
   * <p>
   * If the file cannot be read or parsed, logs the error and returns an empty list (fail-open).
   *
   * @return the list of guard entries, never null
   */
  private List<GuardEntry> loadGuards()
  {
    Path registryFile = scope.getPluginRoot().resolve("config").resolve("skill-triggers.json");
    List<GuardEntry> result = new ArrayList<>();
    try
    {
      if (!Files.exists(registryFile))
      {
        log.warn("RequireSkillForCommand: registry file not found: {}", registryFile);
        return result;
      }
      String content = Files.readString(registryFile, UTF_8);
      JsonNode root = scope.getJsonMapper().readTree(content);
      JsonNode guardsNode = root.get("guards");
      if (guardsNode == null || !guardsNode.isArray())
      {
        log.warn("RequireSkillForCommand: registry file missing 'guards' array: {}", registryFile);
        return result;
      }
      for (JsonNode entry : guardsNode)
      {
        JsonNode patternNode = entry.get("pattern");
        JsonNode skillNode = entry.get("skill");
        if (patternNode == null || !patternNode.isString() || skillNode == null || !skillNode.isString())
        {
          log.warn("RequireSkillForCommand: skipping invalid guard entry: {}", entry);
          continue;
        }
        String patternString = patternNode.asString();
        String bareName = skillNode.asString();
        assert that(bareName, "bareName").doesNotContain(":").elseThrow();
        String qualifiedName = scope.getPluginPrefix() + ":" + bareName;
        Pattern compiled = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        result.add(new GuardEntry(compiled, qualifiedName));
      }
    }
    catch (IOException e)
    {
      log.error("RequireSkillForCommand: failed to load registry from {}: {}", registryFile,
        e.getMessage(), e);
    }
    return result;
  }

  /**
   * Checks whether the bash command requires a skill that has not yet been loaded.
   * <p>
   * The agent's {@code skills-loaded} marker file is read once before the guard loop. Each guard whose
   * pattern matches the command is then checked against the in-memory skill set. If any required skill is
   * absent, the command is blocked. If the command matches no pattern, it is allowed.
   * <p>
   * This handler fails open: I/O errors reading the marker file or unexpected agent ID formats cause the
   * command to be allowed rather than blocked.
   *
   * @param scope the hook scope providing the bash command, session ID, and agent context
   * @return a block result if a required skill is not loaded, or an allow result otherwise
   */
  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();
    if (command.isBlank())
      return Result.allow();

    String sessionId = scope.getSessionId();
    String catAgentId = scope.getCatAgentId(sessionId);
    Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
    Set<String> loadedSkills;
    try
    {
      Path agentDir = GetSkill.resolveAndValidateContainment(baseDir, catAgentId, "catAgentId");
      Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
      try (java.util.stream.Stream<Path> stream = Files.list(loadedDir))
      {
        loadedSkills = stream.
          map(p -> p.getFileName().toString()).
          map(name -> URLDecoder.decode(name, UTF_8)).
          collect(Collectors.toSet());
      }
    }
    catch (NoSuchFileException _)
    {
      loadedSkills = Set.of();
    }
    catch (IllegalArgumentException e)
    {
      log.error("RequireSkillForCommand: unexpected catAgentId format '{}': {}", catAgentId,
        e.getMessage(), e);
      return Result.allow();
    }
    catch (IOException e)
    {
      log.error("RequireSkillForCommand: failed to read loaded marker directory for agent '{}': {}",
        catAgentId, e.getMessage(), e);
      return Result.allow();
    }

    for (GuardEntry guard : guards)
    {
      if (!guard.pattern().matcher(command).find())
        continue;
      if (!loadedSkills.contains(guard.skill()))
        return buildBlockResult(guard.skill());
    }

    return Result.allow();
  }

  /**
   * Builds a block result with an actionable message naming the required skill.
   *
   * @param qualifiedName the fully qualified skill name (e.g. {@code marketplaces:git-rebase-agent})
   * @return a block result with the formatted message
   */
  private Result buildBlockResult(String qualifiedName)
  {
    String skillBaseName = GetSkill.stripPrefix(qualifiedName);
    String message = """
      BLOCKED: This command requires the cat:%s skill.

      Load the skill first:
        /cat:%s

      Then retry the command.""".formatted(skillBaseName, skillBaseName);
    return Result.block(message);
  }
}

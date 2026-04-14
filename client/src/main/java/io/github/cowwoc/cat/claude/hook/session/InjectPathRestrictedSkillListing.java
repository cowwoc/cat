/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AbstractClaudeHook;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import io.github.cowwoc.cat.claude.hook.ReadHandler;
import io.github.cowwoc.cat.claude.hook.util.GlobMatcher;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery;
import io.github.cowwoc.cat.claude.hook.util.SkillDiscovery.SkillEntry;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * Lazily injects skill name and description as additional context when a file matching a skill's
 * {@code paths:} frontmatter glob is accessed.
 * <p>
 * Skills that declare {@code paths:} in their SKILL.md frontmatter are path-restricted: they apply
 * to specific file types and are not listed at session start. Instead, when the agent reads or writes
 * a file matching the glob, this handler injects the skill name and description once per session so
 * the agent can invoke the skill if relevant.
 */
public final class InjectPathRestrictedSkillListing implements ReadHandler, FileWriteHandler
{
  private final Logger log = LoggerFactory.getLogger(InjectPathRestrictedSkillListing.class);
  private final ClaudeHook scope;

  /**
   * Creates a new InjectPathRestrictedSkillListing handler.
   *
   * @param scope the hook scope providing project path and session information
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectPathRestrictedSkillListing(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public ReadHandler.Result check(String toolName, JsonNode toolInput, JsonNode toolResult,
    String catAgentId)
  {
    // Only fire for Read; Glob uses a pattern rather than a specific file path
    if (!toolName.equals("Read"))
      return ReadHandler.Result.allow();
    JsonNode filePathNode = toolInput.get("file_path");
    if (filePathNode == null || !filePathNode.isString())
      return ReadHandler.Result.allow();
    String context = getRelatedSkills(filePathNode.asString(), catAgentId);
    if (context.isEmpty())
      return ReadHandler.Result.allow();
    return ReadHandler.Result.context(context);
  }

  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String catAgentId)
  {
    JsonNode filePathNode = toolInput.get("file_path");
    if (filePathNode == null || !filePathNode.isString())
      return FileWriteHandler.Result.allow();
    String context = getRelatedSkills(filePathNode.asString(), catAgentId);
    if (context.isEmpty())
      return FileWriteHandler.Result.allow();
    return FileWriteHandler.Result.context(context);
  }

  /**
   * Computes the additional context to inject for a given file path and agent ID.
   * <p>
   * Discovers all skills with non-empty {@code paths:} lists, matches the file path against each
   * skill's globs, and injects the skill name and description for any skill whose hint has not yet
   * been injected this session. Writes a per-session marker file to prevent repeated injection.
   * <p>
   * Marker files are named {@code skill-listed-<sanitized-name>} where the sanitized name replaces
   * any character outside {@code [a-zA-Z0-9-]} with a hyphen.
   *
   * @param filePath the file path being accessed (absolute paths are skipped)
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the additional context string, or an empty string if nothing should be injected
   */
  private String getRelatedSkills(String filePath, String catAgentId)
  {
    // Absolute paths are not matched against skill globs. They cannot be relative to any project
    // root, so matching would always be wrong. Rejecting them also prevents path traversal.
    if (filePath.startsWith("/"))
    {
      log.debug("Skipping absolute path for skill hint injection: {}", filePath);
      return "";
    }
    String sessionId = AbstractClaudeHook.extractSessionId(catAgentId);
    List<SkillEntry> entries = SkillDiscovery.discoverAllEntries(scope);
    StringJoiner joiner = new StringJoiner("\n");
    boolean hasMatches = false;
    for (SkillEntry entry : entries)
    {
      if (entry.paths().isEmpty())
        continue;
      boolean matched = false;
      for (String glob : entry.paths())
      {
        if (GlobMatcher.matches(glob, filePath))
        {
          matched = true;
          break;
        }
      }
      if (!matched)
        continue;
      // Marker file names use only [a-zA-Z0-9-] characters to avoid filesystem special chars
      String sanitizedName = entry.name().replaceAll("[^a-zA-Z0-9-]", "-");
      Path markerFile = scope.getCatSessionPath(sessionId).resolve("skill-listed-" + sanitizedName);
      if (Files.exists(markerFile))
        continue;
      try
      {
        Files.createDirectories(markerFile.getParent());
        Files.createFile(markerFile);
      }
      catch (FileAlreadyExistsException _)
      {
        // Another concurrent invocation already created the marker — this skill was already listed
        continue;
      }
      catch (IOException e)
      {
        log.warn("Failed to write skill listing marker for {}: {}", entry.name(), e.getMessage());
        // Best-effort: marker write failed, still inject the hint this invocation
      }
      if (!hasMatches)
      {
        joiner.add("The following skills might be related to your work on this file:");
        hasMatches = true;
      }
      joiner.add("- " + entry.name() + ": " + entry.description());
    }
    String context = joiner.toString();
    if (log.isDebugEnabled() && !context.isEmpty())
      log.debug("Injecting {} bytes of path-restricted skill context for file: {}", context.length(), filePath);
    return context;
  }
}

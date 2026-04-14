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
import io.github.cowwoc.cat.claude.hook.util.RulesDiscovery;
import io.github.cowwoc.cat.claude.hook.util.RulesDiscovery.RuleFile;
import tools.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lazily injects path-restricted rule content as additional context when a file matching the rule's
 * {@code paths:} frontmatter glob is accessed.
 * <p>
 * Rules that declare {@code paths:} in their frontmatter are path-restricted: they apply to specific
 * file types and are not injected at session start. Instead, when the agent reads or writes a file
 * matching the glob, this handler injects the rule content once per session so the agent receives
 * the relevant guidance.
 */
public final class InjectPathRestrictedRuleListing implements ReadHandler, FileWriteHandler
{
  private final Logger log = LoggerFactory.getLogger(InjectPathRestrictedRuleListing.class);
  private final ClaudeHook scope;

  /**
   * Creates a new InjectPathRestrictedRuleListing handler.
   *
   * @param scope the hook scope providing project path and session information
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectPathRestrictedRuleListing(ClaudeHook scope)
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
    String context = getRelatedRules(filePathNode.asString(), catAgentId);
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
    String context = getRelatedRules(filePathNode.asString(), catAgentId);
    if (context.isEmpty())
      return FileWriteHandler.Result.allow();
    return FileWriteHandler.Result.context(context);
  }

  /**
   * Computes the additional context to inject for a given file path and agent ID.
   * <p>
   * Discovers all rules with non-empty {@code paths:} lists from both the plugin rules directory
   * and the project rules directory, matches the file path against each rule's globs, and injects
   * the rule content for any rule that has not yet been injected this session. Writes a per-session
   * marker file to prevent repeated injection.
   * <p>
   * Marker files are named {@code rule-listed-<sanitized-filename>} where the sanitized name
   * replaces any character outside {@code [a-zA-Z0-9-]} with a hyphen.
   *
   * @param filePath the file path being accessed (absolute paths are skipped)
   * @param catAgentId the CAT agent ID (sessionId for main agent, sessionId/subagents/agentXxx for subagents)
   * @return the additional context string, or an empty string if nothing should be injected
   */
  private String getRelatedRules(String filePath, String catAgentId)
  {
    // Absolute paths are not matched against rule globs. They cannot be relative to any project
    // root, so matching would always be wrong. Rejecting them also prevents path traversal.
    if (filePath.startsWith("/"))
    {
      log.debug("Skipping absolute path for rule injection: {}", filePath);
      return "";
    }
    String sessionId = AbstractClaudeHook.extractSessionId(catAgentId);
    Path pluginRulesDir = scope.getPluginRoot().resolve("rules");
    Path projectRulesDir = scope.getCatDir().resolve("rules");
    List<RuleFile> allRules = new ArrayList<>(new RulesDiscovery(pluginRulesDir, scope.getYamlMapper()).discoverAll());
    allRules.addAll(new RulesDiscovery(projectRulesDir, scope.getYamlMapper()).discoverAll());

    StringBuilder sb = new StringBuilder();
    for (RuleFile rule : allRules)
    {
      if (rule.paths().isEmpty())
        continue;
      boolean matched = false;
      for (String glob : rule.paths())
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
      String sanitizedName = rule.path().getFileName().toString().replaceAll("[^a-zA-Z0-9-]", "-");
      Path markerFile = scope.getCatSessionPath(sessionId).resolve("rule-listed-" + sanitizedName);
      if (Files.exists(markerFile))
        continue;
      try
      {
        Files.createDirectories(markerFile.getParent());
        Files.createFile(markerFile);
      }
      catch (FileAlreadyExistsException _)
      {
        // Another concurrent invocation already created the marker — this rule was already injected
        continue;
      }
      catch (IOException e)
      {
        log.warn("Failed to write rule listing marker for {}: {}", rule.path().getFileName(),
          e.getMessage());
        // Best-effort: marker write failed, still inject the rule content this invocation
      }
      if (!sb.isEmpty())
        sb.append("\n\n");
      sb.append(rule.content());
    }
    String context = sb.toString();
    if (log.isDebugEnabled() && !context.isEmpty())
      log.debug("Injecting {} bytes of path-restricted rule context for file: {}", context.length(),
        filePath);
    return context;
  }
}

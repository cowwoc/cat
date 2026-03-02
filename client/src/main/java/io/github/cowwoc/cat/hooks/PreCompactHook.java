/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.session.RestoreCwdAfterCompaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves the agent's working directory before context compaction.
 * <p>
 * TRIGGER: PreCompact
 * <p>
 * Reads the {@code cwd} field from the hook input and writes it to
 * {@code .claude/cat/sessions/{session_id}.cwd}, but only when the path is non-blank and differs
 * from the project root. This file is consumed by {@link RestoreCwdAfterCompaction} to restore
 * the working directory after context compaction.
 */
public final class PreCompactHook implements HookHandler
{
  private final Logger log = LoggerFactory.getLogger(PreCompactHook.class);
  private final JvmScope scope;

  /**
   * Creates a new PreCompactHook instance.
   *
   * @param scope the JVM scope providing environment paths
   * @throws NullPointerException if {@code scope} is null
   */
  public PreCompactHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the PreCompact hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookRunner.execute(PreCompactHook::new, args);
  }

  /**
   * Processes hook input and saves the current working directory before compaction.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String sessionId = input.getSessionId();
    if (sessionId.isBlank())
      return HookResult.withoutWarnings(output.empty());

    String cwd = input.getString("cwd");
    if (cwd.isBlank())
      return HookResult.withoutWarnings(output.empty());

    Path projectRoot = scope.getClaudeProjectDir();
    Path normalizedCwd = Path.of(cwd).normalize();
    Path normalizedProjectRoot = projectRoot.normalize();

    if (normalizedCwd.equals(normalizedProjectRoot))
      return HookResult.withoutWarnings(output.empty());

    Path sessionsDir = projectRoot.resolve(".claude/cat/sessions");
    Path cwdFile = sessionsDir.resolve(sessionId + ".cwd");

    try
    {
      Files.createDirectories(sessionsDir);
      Files.writeString(cwdFile, cwd);
    }
    catch (IOException e)
    {
      log.debug("Failed to write working directory to {}: {}", cwdFile, e.getMessage());
    }

    return HookResult.withoutWarnings(output.empty());
  }
}

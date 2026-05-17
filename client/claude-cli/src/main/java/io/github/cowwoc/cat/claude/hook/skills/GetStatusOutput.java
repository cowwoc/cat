/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import static io.github.cowwoc.cat.tool.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import io.github.cowwoc.cat.agent.AgentScope;
import io.github.cowwoc.cat.tool.skills.SkillOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Claude adapter for the runtime-neutral /cat:status output generator.
 */
public final class GetStatusOutput implements SkillOutput
{
  public static final String NO_CAT_PROJECT_MESSAGE =
    io.github.cowwoc.cat.tool.skills.GetStatusOutput.NO_CAT_PROJECT_MESSAGE;
  public static final String NO_PLANNING_STRUCTURE_MESSAGE =
    io.github.cowwoc.cat.tool.skills.GetStatusOutput.NO_PLANNING_STRUCTURE_MESSAGE;

  private final io.github.cowwoc.cat.tool.skills.GetStatusOutput delegate;

  /**
   * Creates a GetStatusOutput adapter.
   *
   * @param scope the Claude tool scope
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStatusOutput(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.delegate = new io.github.cowwoc.cat.tool.skills.GetStatusOutput(scope);
  }

  @Override
  public String getOutput(String[] args) throws IOException
  {
    return delegate.getOutput(args);
  }

  /**
   * Parses issue status from markdown content.
   *
   * @param content the markdown content
   * @param sourcePath the source path for diagnostics
   * @return the parsed status
   * @throws IOException if parsing fails
   */
  public String parseStatusFromContent(String content, String sourcePath) throws IOException
  {
    return delegate.parseStatusFromContent(content, sourcePath);
  }

  /**
   * Returns whether a Git branch name is safe to inspect.
   *
   * @param branch the branch name
   * @return true if the branch name is safe
   */
  public boolean isValidBranchName(String branch)
  {
    return delegate.isValidBranchName(branch);
  }

  /**
   * Returns whether a state file path is safe to inspect.
   *
   * @param filePath the state file path
   * @return true if the path is safe
   */
  public boolean isValidStateFilePath(String filePath)
  {
    return delegate.isValidStateFilePath(filePath);
  }

  /**
   * Entry point for /cat:status output.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetStatusOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the status output logic with a caller-provided output stream.
   *
   * @param scope the JVM scope (must implement {@link ClaudeTool})
   * @param args command-line arguments
   * @param out the output stream to write to
   * @throws IOException if an I/O error occurs
   */
  public static void run(AgentScope scope, String[] args, PrintStream out) throws IOException
  {
    io.github.cowwoc.cat.tool.skills.GetStatusOutput.run(scope, args, out);
  }

  /**
   * Returns whether text exactly matches a plain status setup message.
   *
   * @param text the text to check
   * @return true if the text matches a plain setup message
   */
  public static boolean isPlainSetupStatusOutput(String text)
  {
    return io.github.cowwoc.cat.tool.skills.GetStatusOutput.isPlainSetupStatusOutput(text);
  }
}

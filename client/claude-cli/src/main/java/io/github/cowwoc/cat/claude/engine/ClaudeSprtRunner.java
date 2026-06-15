/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Claude-specific CLI entrypoint for the SPRT runner.
 */
public final class ClaudeSprtRunner
{
  private ClaudeSprtRunner()
  {
  }

  /**
   * CLI entrypoint.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (CliTool scope = new MainClaudeTool())
    {
      try
      {
        String claudeCodeVersion = ModelIdResolver.detectClaudeCodeVersionOrLatestMapping();
        SprtRunner.run(scope, args, System.out,
          new ClaudeSprtMetadataResolver(claudeCodeVersion));
      }
      catch (IllegalArgumentException | IOException | InterruptedException e)
      {
        try
        {
          String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
          System.out.println(SprtRunner.toErrorJson(scope, message));
        }
        catch (IOException jsonException)
        {
          Logger log = LoggerFactory.getLogger(ClaudeSprtRunner.class);
          log.error("Failed to serialize error message", jsonException);
          System.out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
        }
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(ClaudeSprtRunner.class);
        log.error("Unexpected error", e);
        try
        {
          String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
          System.out.println(SprtRunner.toErrorJson(scope, message));
        }
        catch (IOException jsonException)
        {
          log.error("Failed to serialize error message", jsonException);
          System.out.println("{\"status\":\"ERROR\",\"message\":\"serialization failed\"}");
        }
      }
    }
  }
}

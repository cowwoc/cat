/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manual E2E verification tool for the defer-plan-generation-to-work-phase feature.
 * <p>
 * Asserts that:
 * <ol>
 *   <li>{@code plugin/skills/add-agent/first-use.md} does NOT contain a {@code cat:plan-builder-agent} invocation</li>
 *   <li>{@code plugin/skills/add-agent/first-use.md} contains the lightweight plan generation block
 *       ({@code planTempFile=$(mktemp})</li>
 *   <li>{@code plugin/skills/work-implement-agent/first-use.md} contains the {@code hasSteps} check</li>
 *   <li>{@code plugin/skills/work-implement-agent/first-use.md} invokes {@code cat:plan-builder-agent}</li>
 * </ol>
 * <p>
 * Usage: {@code verify-defer-plan-generation [PROJECT_ROOT]}
 * <p>
 * {@code PROJECT_ROOT} defaults to the {@code CLAUDE_PROJECT_DIR} environment variable.
 */
public final class VerifyDeferPlanGeneration
{
  /**
   * Instances are not supported.
   */
  private VerifyDeferPlanGeneration()
  {
  }

  /**
   * Entry point for the verify-defer-plan-generation CLI tool.
   *
   * @param args optional first argument is the project root path; defaults to {@code CLAUDE_PROJECT_DIR}
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        int exitCode = run(scope, args, System.out);
        if (exitCode != 0)
          System.exit(exitCode);
      }
      catch (IllegalArgumentException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(VerifyDeferPlanGeneration.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Runs the verification checks, writing results to {@code out}.
   *
   * @param scope the JVM scope providing the project path when no argument is supplied
   * @param args  command-line arguments; optional first arg is PROJECT_ROOT
   * @param out   the output stream for results
   * @return 0 if all checks passed, 1 if any check failed
   * @throws NullPointerException if {@code scope}, {@code args}, or {@code out} are null
   */
  public static int run(JvmScope scope, String[] args, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    try
    {
      if (args.length > 1)
      {
        throw new IllegalArgumentException(
          "Expected at most 1 argument (project-root), got " + args.length + ". " +
            "Usage: verify-defer-plan-generation [<project-root>]");
      }
      Path projectRoot;
      if (args.length >= 1)
        projectRoot = Path.of(args[0]);
      else
        projectRoot = scope.getProjectPath();

      Path addSkill = projectRoot.resolve("plugin/skills/add-agent/first-use.md");
      Path workImplementSkill = projectRoot.resolve("plugin/skills/work-implement-agent/first-use.md");

      out.println("=== E2E Verification: defer-plan-generation-to-work-phase ===");

      int passed = 0;
      int failed = 0;

      // Check 1: add/first-use.md must NOT contain cat:plan-builder-agent invocation
      if (Files.notExists(addSkill))
      {
        out.println("FAIL: add/first-use.md has no cat:plan-builder-agent invocation");
        out.println("      File not found: " + addSkill);
        ++failed;
      }
      else
      {
        String addContent = Files.readString(addSkill);
        if (addContent.contains("cat:plan-builder-agent"))
        {
          out.println("FAIL: add/first-use.md has no cat:plan-builder-agent invocation");
          out.println("      Found 'cat:plan-builder-agent' in " + addSkill);
          ++failed;
        }
        else
        {
          out.println("PASS: add/first-use.md has no cat:plan-builder-agent invocation");
          ++passed;
        }
      }

      // Check 2: add/first-use.md must contain the lightweight plan generation block
      if (Files.notExists(addSkill))
      {
        out.println("FAIL: add/first-use.md contains lightweight plan block (planTempFile mktemp)");
        out.println("      File not found: " + addSkill);
        ++failed;
      }
      else
      {
        String addContent = Files.readString(addSkill);
        if (addContent.contains("planTempFile=$(mktemp"))
        {
          out.println("PASS: add/first-use.md contains planTempFile=$(mktemp ...)");
          ++passed;
        }
        else
        {
          out.println("FAIL: add/first-use.md contains lightweight plan block (planTempFile mktemp)");
          out.println("      Pattern 'planTempFile=$(mktemp' not found in " + addSkill);
          ++failed;
        }
      }

      // Check 3: work-implement-agent/first-use.md must contain hasSteps check
      if (Files.notExists(workImplementSkill))
      {
        out.println("FAIL: work-implement-agent/first-use.md contains hasSteps check");
        out.println("      File not found: " + workImplementSkill);
        ++failed;
      }
      else
      {
        String workContent = Files.readString(workImplementSkill);
        if (workContent.contains("hasSteps"))
        {
          out.println("PASS: work-implement-agent/first-use.md contains hasSteps check");
          ++passed;
        }
        else
        {
          out.println("FAIL: work-implement-agent/first-use.md contains hasSteps check");
          out.println("      Pattern 'hasSteps' not found in " + workImplementSkill);
          ++failed;
        }
      }

      // Check 4: work-implement-agent/first-use.md must invoke cat:plan-builder-agent
      if (Files.notExists(workImplementSkill))
      {
        out.println("FAIL: work-implement-agent/first-use.md invokes cat:plan-builder-agent");
        out.println("      File not found: " + workImplementSkill);
        ++failed;
      }
      else
      {
        String workContent = Files.readString(workImplementSkill);
        if (workContent.contains("cat:plan-builder-agent"))
        {
          out.println("PASS: work-implement-agent/first-use.md invokes cat:plan-builder-agent");
          ++passed;
        }
        else
        {
          out.println("FAIL: work-implement-agent/first-use.md invokes cat:plan-builder-agent");
          out.println("      Pattern 'cat:plan-builder-agent' not found in " + workImplementSkill);
          ++failed;
        }
      }

      out.println("=== Results: " + passed + " passed, " + failed + " failed ===");

      if (failed > 0)
        return 1;
      return 0;
    }
    catch (IOException e)
    {
      out.println("ERROR: " + e.getMessage());
      return 1;
    }
  }
}

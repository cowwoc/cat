/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.MainCliTool;

/**
 * Runtime-neutral entrypoints for shared utility launchers.
 */
@SuppressWarnings({"PMD.CommentRequired", "PMD.MissingStaticMethodInNonInstantiatableClass"})
public final class SharedUtilityMain
{
  private SharedUtilityMain()
  {
  }

  /**
   * Entry point for the token-counter launcher.
   */
  public static final class TokenCounter
  {
    private TokenCounter()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.TokenCounter.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the create-issue launcher.
   */
  public static final class IssueCreator
  {
    private IssueCreator()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.IssueCreator.run(scope, args, System.in, System.out);
      }
    }
  }

  /**
   * Entry point for the git-squash launcher.
   */
  public static final class GitSquash
  {
    private GitSquash()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.GitSquash.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the git-merge-linear launcher.
   */
  public static final class GitMergeLinear
  {
    private GitMergeLinear()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.GitMergeLinear.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the git-amend launcher.
   */
  public static final class GitAmend
  {
    private GitAmend()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.GitAmend.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the git-rebase launcher.
   */
  public static final class GitRebase
  {
    private GitRebase()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.GitRebase.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the issue-lock launcher.
   */
  public static final class IssueLock
  {
    private IssueLock()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.IssueLock.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the check-existing-work launcher.
   */
  public static final class ExistingWorkChecker
  {
    private ExistingWorkChecker()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        boolean success =
          io.github.cowwoc.cat.claude.hook.util.ExistingWorkChecker.run(scope, args, System.out, System.err);
        if (!success)
          System.exit(1);
      }
    }
  }

  /**
   * Entry point for the wrap-markdown launcher.
   */
  public static final class MarkdownWrapper
  {
    private MarkdownWrapper()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.MarkdownWrapper.run(scope, args, System.in, System.out);
      }
    }
  }

  /**
   * Entry point for the batch-read launcher.
   */
  public static final class BatchReader
  {
    private BatchReader()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.BatchReader.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the validate-status-alignment launcher.
   */
  public static final class StatusAlignmentValidator
  {
    private StatusAlignmentValidator()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.StatusAlignmentValidator.run(scope, args, System.in, System.out);
      }
    }
  }

  /**
   * Entry point for the feedback launcher.
   */
  public static final class Feedback
  {
    private Feedback()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.Feedback.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the auto-close-index launcher.
   */
  public static final class AutoCloseIndexJson
  {
    private AutoCloseIndexJson()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.AutoCloseIndexJson.run(scope, args, System.out);
      }
    }
  }

  /**
   * Entry point for the verify-defer-plan-generation launcher.
   */
  public static final class VerifyDeferPlanGeneration
  {
    private VerifyDeferPlanGeneration()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        int exitCode =
          io.github.cowwoc.cat.claude.hook.util.VerifyDeferPlanGeneration.run(scope, args, System.out);
        if (exitCode != 0)
          System.exit(exitCode);
      }
    }
  }

  /**
   * Entry point for the write-session-marker launcher.
   */
  public static final class WriteSessionMarker
  {
    private WriteSessionMarker()
    {
    }

    public static void main(String[] args) throws Exception
    {
      io.github.cowwoc.cat.claude.hook.util.WriteSessionMarker.run(args, System.out);
    }
  }

  /**
   * Entry point for the read-session-marker launcher.
   */
  public static final class ReadSessionMarker
  {
    private ReadSessionMarker()
    {
    }

    public static void main(String[] args) throws Exception
    {
      try
      {
        io.github.cowwoc.cat.claude.hook.util.ReadSessionMarker.run(args, System.out);
      }
      catch (java.nio.file.NoSuchFileException e)
      {
        System.err.println("Marker file not found: " + e.getFile());
        System.exit(1);
      }
    }
  }

  /**
   * Entry point for the write-and-commit launcher.
   */
  public static final class WriteAndCommit
  {
    private WriteAndCommit()
    {
    }

    public static void main(String[] args)
    {
      try (CliTool scope = new MainCliTool())
      {
        io.github.cowwoc.cat.claude.hook.util.WriteAndCommit.run(scope, args, System.out);
      }
    }
  }
}

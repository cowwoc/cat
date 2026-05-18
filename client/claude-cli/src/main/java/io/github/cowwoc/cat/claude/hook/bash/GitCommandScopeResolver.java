/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import io.github.cowwoc.cat.claude.hook.ShellParser;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves effective git command scope after applying global git flags.
 */
final class GitCommandScopeResolver
{
  private GitCommandScopeResolver()
  {
  }

  /**
   * Resolves effective scope details for a raw command segment containing git.
   *
   * @param rawSegment the raw command segment
   * @param workingDirectory hook working directory
   * @return resolved scope details
   */
  static GitScopeTarget resolve(String rawSegment, Path workingDirectory)
  {
    List<String> tokens = ShellParser.tokenize(rawSegment);
    int gitIndex = GitCommandNormalizer.findGitTokenIndex(tokens);
    if (gitIndex == -1)
      return new GitScopeTarget(workingDirectory, null, false);

    Path effectiveWorkingTree = workingDirectory;
    Path effectiveGitDirectory = null;
    boolean overrides = false;
    int i = gitIndex + 1;
    boolean done = false;
    while (i < tokens.size() && !done)
    {
      String token = tokens.get(i);
      if (token.equals("-C"))
      {
        if (i + 1 >= tokens.size())
          done = true;
        else
        {
          ++i;
          effectiveWorkingTree = ShellParser.resolvePath(tokens.get(i), workingDirectory).toAbsolutePath();
          overrides = true;
          ++i;
        }
      }
      else if (token.startsWith("-C") && token.length() > 2)
      {
        effectiveWorkingTree = ShellParser.resolvePath(token.substring(2), workingDirectory).toAbsolutePath();
        overrides = true;
        ++i;
      }
      else if (token.equals("--work-tree"))
      {
        if (i + 1 >= tokens.size())
          done = true;
        else
        {
          ++i;
          effectiveWorkingTree = ShellParser.resolvePath(tokens.get(i), workingDirectory).toAbsolutePath();
          overrides = true;
          ++i;
        }
      }
      else if (token.startsWith("--work-tree="))
      {
        effectiveWorkingTree = ShellParser.resolvePath(token.substring("--work-tree=".length()),
          workingDirectory).toAbsolutePath();
        overrides = true;
        ++i;
      }
      else if (token.equals("--git-dir"))
      {
        if (i + 1 >= tokens.size())
          done = true;
        else
        {
          ++i;
          Path gitDir = ShellParser.resolvePath(tokens.get(i), workingDirectory).toAbsolutePath();
          overrides = true;
          effectiveGitDirectory = gitDir;
          Path gitParent = gitDir.getParent();
          if (gitParent == null)
            effectiveWorkingTree = gitDir;
          else
            effectiveWorkingTree = gitParent;
          ++i;
        }
      }
      else if (token.startsWith("--git-dir="))
      {
        Path gitDir = ShellParser.resolvePath(token.substring("--git-dir=".length()),
          workingDirectory).toAbsolutePath();
        overrides = true;
        effectiveGitDirectory = gitDir;
        Path gitParent = gitDir.getParent();
        if (gitParent == null)
          effectiveWorkingTree = gitDir;
        else
          effectiveWorkingTree = gitParent;
        ++i;
      }
      else if (token.equals("-c"))
      {
        if (i + 1 < tokens.size())
          ++i;
        ++i;
      }
      else if (token.startsWith("-c") && token.length() > 2)
        ++i;
      else
        done = true;
    }
    return new GitScopeTarget(effectiveWorkingTree, effectiveGitDirectory, overrides);
  }

  /**
   * Resolved git scope.
   *
   * @param workingTree resolved working tree
   * @param gitDirectory resolved git directory
   * @param overridesScope true if global git flags changed effective scope
   */
  record GitScopeTarget(Path workingTree, Path gitDirectory, boolean overridesScope)
  {
  }
}

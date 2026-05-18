/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.ShellParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and normalizes git commands from raw bash text.
 * <p>
 * The normalizer removes git global flags that can precede the subcommand and returns command
 * strings that are safe to match against in hook handlers.
 */
public final class GitCommandNormalizer
{
  /**
   * Prevent instantiation.
   */
  private GitCommandNormalizer()
  {
  }

  /**
   * Extracts git command segments from raw bash input and strips supported global flags.
   * <p>
   * Segments are split on {@code &&}, {@code ||}, {@code ;}, and {@code |} when outside quotes.
   * For each git segment, this method removes:
   * <ul>
   *   <li>{@code -C <path>}</li>
   *   <li>{@code --git-dir=<path>} and {@code --git-dir <path>}</li>
   *   <li>{@code --work-tree=<path>} and {@code --work-tree <path>}</li>
   *   <li>{@code -c <key>=<value>} and compact {@code -c<key>=<value>}</li>
   * </ul>
   *
   * @param rawCommand raw bash command text
   * @return normalized git command strings
   * @throws NullPointerException if {@code rawCommand} is null
   */
  public static List<String> extractNormalizedGitCommands(String rawCommand)
  {
    List<NormalizedGitCommand> commands = extractGitCommands(rawCommand);
    List<String> result = new ArrayList<>(commands.size());
    for (NormalizedGitCommand command : commands)
      result.add(command.normalizedCommand());
    return result;
  }

  /**
   * Extracts git command segments from raw bash input, preserving each raw segment.
   *
   * @param rawCommand raw bash command text
   * @return raw and normalized git command pairs
   * @throws NullPointerException if {@code rawCommand} is null
   */
  public static List<NormalizedGitCommand> extractGitCommands(String rawCommand)
  {
    requireThat(rawCommand, "rawCommand").isNotNull();
    List<NormalizedGitCommand> result = new ArrayList<>();
    List<String> segments = splitSegments(rawCommand);
    for (String segment : segments)
    {
      List<String> tokens = ShellParser.tokenize(segment);
      if (tokens.isEmpty())
        continue;
      int gitIndex = findGitTokenIndex(tokens);
      if (gitIndex == -1)
        continue;
      String normalized = normalizeGitTokens(tokens, gitIndex);
      if (!normalized.isEmpty())
        result.add(new NormalizedGitCommand(segment, normalized));
    }
    return result;
  }

  /**
   * A raw git command segment and its normalized form.
   *
   * @param rawSegment the original shell segment containing the git invocation
   * @param normalizedCommand the normalized git command, starting with {@code git}
   */
  public record NormalizedGitCommand(String rawSegment, String normalizedCommand)
  {
  }

  /**
   * Returns true if a segment contains an unquoted {@code # ACKNOWLEDGED} shell comment.
   *
   * @param rawSegment the raw command segment
   * @return {@code true} if acknowledged
   */
  public static boolean containsAcknowledgedComment(String rawSegment)
  {
    requireThat(rawSegment, "rawSegment").isNotNull();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < rawSegment.length(); ++i)
    {
      char ch = rawSegment.charAt(i);
      if (ch == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (ch == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (ch == '\\' && !inSingleQuote && i + 1 < rawSegment.length())
      {
        ++i;
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote && ch == '#')
      {
        String comment = rawSegment.substring(i + 1).stripLeading();
        return comment.startsWith("ACKNOWLEDGED");
      }
    }
    return false;
  }

  /**
   * Splits a shell command string into command segments across logical operators.
   *
   * @param command the command to split
   * @return split segments (trimmed, non-empty)
   */
  private static List<String> splitSegments(String command)
  {
    List<String> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;

    for (int i = 0; i < command.length(); ++i)
    {
      char ch = command.charAt(i);
      if (ch == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        current.append(ch);
        continue;
      }
      if (ch == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        current.append(ch);
        continue;
      }
      if (ch == '\\' && !inSingleQuote && i + 1 < command.length())
      {
        current.append(ch);
        ++i;
        current.append(command.charAt(i));
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote)
      {
        if (ch == ';')
        {
          addSegment(segments, current);
          continue;
        }
        if (ch == '\n')
        {
          addSegment(segments, current);
          continue;
        }
        if (ch == '\r')
        {
          addSegment(segments, current);
          if (i + 1 < command.length() && command.charAt(i + 1) == '\n')
            ++i;
          continue;
        }
        if (ch == '&')
        {
          addSegment(segments, current);
          if (i + 1 < command.length() && command.charAt(i + 1) == '&')
            ++i;
          continue;
        }
        if (ch == '|')
        {
          addSegment(segments, current);
          if (i + 1 < command.length() && command.charAt(i + 1) == '|')
            ++i;
          continue;
        }
      }
      current.append(ch);
    }
    addSegment(segments, current);
    return segments;
  }

  /**
   * Adds the current segment if non-empty, then clears the builder.
   *
   * @param segments destination segment list
   * @param current current segment builder
   */
  private static void addSegment(List<String> segments, StringBuilder current)
  {
    String segment = current.toString().strip();
    if (!segment.isEmpty())
      segments.add(segment);
    current.setLength(0);
  }

  /**
   * Finds the git executable token after safe shell prefixes.
   *
   * @param tokens command segment tokens
   * @return the git token index, or {@code -1} if this is not a git invocation
   */
  public static int findGitTokenIndex(List<String> tokens)
  {
    requireThat(tokens, "tokens").isNotNull();
    int index = 0;
    while (index < tokens.size())
    {
      String token = tokens.get(index);
      if (isShellAssignment(token))
      {
        ++index;
        continue;
      }
      if (token.equals("env"))
      {
        index = skipEnvPrefix(tokens, index + 1);
        continue;
      }
      if (token.equals("command") || token.equals("builtin"))
      {
        index = skipSimpleCommandOptions(tokens, index + 1);
        continue;
      }
      if (token.equals("sudo"))
      {
        index = skipSudoPrefix(tokens, index + 1);
        continue;
      }
      if (token.equals("git"))
        return index;
      return -1;
    }
    return -1;
  }

  /**
   * Returns true if a token is a shell assignment prefix.
   *
   * @param token the token to inspect
   * @return true if the token is an assignment
   */
  private static boolean isShellAssignment(String token)
  {
    int equals = token.indexOf('=');
    if (equals <= 0)
      return false;
    char first = token.charAt(0);
    if (!Character.isLetter(first) && first != '_')
      return false;
    for (int i = 1; i < equals; ++i)
    {
      char ch = token.charAt(i);
      if (!Character.isLetterOrDigit(ch) && ch != '_')
        return false;
    }
    return true;
  }

  /**
   * Skips {@code env} options and assignment prefixes.
   *
   * @param tokens command tokens
   * @param index index after {@code env}
   * @return the next likely executable index
   */
  private static int skipEnvPrefix(List<String> tokens, int index)
  {
    while (index < tokens.size())
    {
      String token = tokens.get(index);
      if (token.equals("-u") || token.equals("--unset") || token.equals("-S"))
      {
        index += 2;
        continue;
      }
      if (token.startsWith("--unset="))
      {
        ++index;
        continue;
      }
      if (token.startsWith("-") || isShellAssignment(token))
      {
        ++index;
        continue;
      }
      return index;
    }
    return index;
  }

  /**
   * Skips options for simple shell wrappers such as {@code command}.
   *
   * @param tokens command tokens
   * @param index index after the wrapper command
   * @return the next likely executable index
   */
  private static int skipSimpleCommandOptions(List<String> tokens, int index)
  {
    while (index < tokens.size() && tokens.get(index).startsWith("-"))
      ++index;
    return index;
  }

  /**
   * Skips common {@code sudo} options before the wrapped command.
   *
   * @param tokens command tokens
   * @param index index after {@code sudo}
   * @return the next likely executable index
   */
  private static int skipSudoPrefix(List<String> tokens, int index)
  {
    while (index < tokens.size())
    {
      String token = tokens.get(index);
      if (token.equals("-u") || token.equals("-g") || token.equals("-h"))
      {
        index += 2;
        continue;
      }
      if (token.startsWith("-"))
      {
        ++index;
        continue;
      }
      return index;
    }
    return index;
  }

  /**
   * Removes supported global flags from a git command token list.
   *
   * @param tokens command tokens whose first token is {@code git}
   * @param gitIndex the index of the {@code git} token
   * @return normalized command string
   */
  private static String normalizeGitTokens(List<String> tokens, int gitIndex)
  {
    int index = gitIndex + 1;
    boolean done = false;
    while (index < tokens.size() && !done)
    {
      String token = tokens.get(index);
      if (token.equals("-C"))
      {
        index += 2;
        continue;
      }
      if (token.startsWith("-C") && token.length() > 2)
      {
        ++index;
        continue;
      }
      if (token.equals("--git-dir") || token.equals("--work-tree") || token.equals("-c"))
      {
        index += 2;
        continue;
      }
      if (token.startsWith("--git-dir=") || token.startsWith("--work-tree=") ||
        (token.startsWith("-c") && token.length() > 2))
      {
        ++index;
        continue;
      }
      done = true;
    }
    if (index >= tokens.size())
      return "git";
    StringBuilder normalized = new StringBuilder("git");
    for (int i = index; i < tokens.size(); ++i)
      normalized.append(' ').append(tokens.get(i));
    return normalized.toString();
  }
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.ClaudeHook;

import java.util.regex.Pattern;

/**
 * Block modifications to git commit identity (user.name / user.email) without explicit user request.
 * <p>
 * Agents must never silently overwrite the git commit identity. Reads (--get, bare read) are allowed;
 * writes, unsets, section removal, and inline {@code -c} overrides are blocked unless the user has
 * explicitly requested the change.
 */
public final class BlockGitUserConfigChange implements BashHandler
{
  /**
   * Matches {@code git config} commands that modify user.name or user.email.
   * <p>
   * Read-only flags (--get, --get-all, --list, --get-regexp) are excluded.
   * --unset and --unset-all are included because they modify the identity configuration.
   * A write is detected when the key (user.name or user.email) is followed by a value argument,
   * or when --unset / --unset-all precedes the key.
   */
  private static final Pattern WRITE_USER_IDENTITY_PATTERN = Pattern.compile(
    // Matches: git config [flags] user.name <value>
    // or:      git config [flags] user.email <value>
    // or:      git config [flags] --unset user.name
    // or:      git config [flags] --unset user.email
    // or:      git config [flags] --unset-all user.name
    // or:      git config [flags] --unset-all user.email
    "git\\s+config\\b" +
      "(?:(?!--get(?:-all|-regexp)?\\b|--list\\b)[^\\n])*?" +
      "(?:" +
      // unset / unset-all: flag followed by user.name or user.email
      "--unset(?:-all)?\\s+user\\.(?:name|email)\\b" +
      "|" +
      // write: user.name or user.email followed by a value (anything that's not a flag start)
      "user\\.(?:name|email)\\s+(?!-)[^\\n]+" +
      ")",
    Pattern.CASE_INSENSITIVE);

  /**
   * Matches {@code git config --remove-section user}, which deletes the entire {@code [user]} section.
   */
  private static final Pattern REMOVE_USER_SECTION_PATTERN = Pattern.compile(
    "git\\s+config\\b.*?--remove-section\\s+user\\b",
    Pattern.CASE_INSENSITIVE);

  /**
   * Matches {@code git -c user.name=<value>} or {@code git -c user.email=<value>} inline overrides
   * that set identity for a single command without touching the config file.
   */
  private static final Pattern INLINE_USER_IDENTITY_PATTERN = Pattern.compile(
    "git\\b(?:\\s+\\S+)*?\\s+-c\\s+user\\.(?:name|email)\\s*=",
    Pattern.CASE_INSENSITIVE);

  /**
   * Matches commands that set git commit identity via environment variable overrides.
   * These variables override identity for a single invocation without touching git config:
   * <ul>
   *   <li>{@code GIT_AUTHOR_NAME=value git commit}</li>
   *   <li>{@code GIT_AUTHOR_EMAIL=value git commit}</li>
   *   <li>{@code GIT_COMMITTER_NAME=value git commit}</li>
   *   <li>{@code GIT_COMMITTER_EMAIL=value git commit}</li>
   * </ul>
   */
  private static final Pattern GIT_IDENTITY_ENV_VAR_PATTERN = Pattern.compile(
    "GIT_(?:AUTHOR|COMMITTER)_(?:NAME|EMAIL)\\s*=",
    Pattern.CASE_INSENSITIVE);

  /**
   * Matches {@code git commit --author=...} or {@code git commit --author '...'} which sets
   * commit author for a single commit without touching git config.
   */
  private static final Pattern GIT_COMMIT_AUTHOR_PATTERN = Pattern.compile(
    "git\\s+commit\\b.*?--author[=\\s]",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /**
   * Matches read-only git config invocations for user.name or user.email.
   * <p>
   * These commands query the value and must be allowed:
   * <ul>
   *   <li>{@code git config user.name}</li>
   *   <li>{@code git config user.email}</li>
   *   <li>{@code git config --get user.name}</li>
   *   <li>{@code git config --get user.email}</li>
   * </ul>
   * Commands containing {@code --unset} or {@code --unset-all} are excluded even if the key appears
   * at end of line.
   */
  private static final Pattern READ_USER_IDENTITY_PATTERN = Pattern.compile(
    "git\\s+config\\b(?!.*--unset).*?(?:--get(?:-all|-regexp)?\\s+)?user\\.(?:name|email)\\s*$",
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  /**
   * The canonical gitconfig paths that must be protected from direct shell writes.
   * <p>
   * These paths cover:
   * <ul>
   *   <li>{@code ~/.gitconfig} (tilde or {@code $HOME} form)</li>
   *   <li>{@code ~/.config/git/config} (tilde or {@code $HOME} form)</li>
   *   <li>{@code /etc/gitconfig} (system-wide)</li>
   * </ul>
   */
  private static final String GITCONFIG_PATH_PATTERN =
    "(?:~|\\$(?:HOME|\\{HOME\\}))/\\.gitconfig" +
      "|(?:~|\\$(?:HOME|\\{HOME\\}))/\\.config/git/config" +
      "|/etc/gitconfig";

  /**
   * Matches shell write redirections targeting canonical gitconfig file paths.
   * <p>
   * Detects commands such as:
   * <ul>
   *   <li>{@code echo '[user]' >> ~/.gitconfig}</li>
   *   <li>{@code printf '[user]\nname=X\n' >> ~/.gitconfig}</li>
   *   <li>{@code tee ~/.gitconfig}</li>
   *   <li>{@code tee -a ~/.gitconfig}</li>
   *   <li>{@code tee --append ~/.gitconfig}</li>
   *   <li>{@code cp /tmp/evil.gitconfig ~/.gitconfig}</li>
   *   <li>{@code mv /tmp/evil.gitconfig ~/.gitconfig}</li>
   *   <li>{@code install -m 600 /tmp/evil.gitconfig ~/.gitconfig}</li>
   *   <li>{@code sed -i 's/name/X/' ~/.gitconfig}</li>
   *   <li>{@code awk '{...}' > ~/.gitconfig}</li>
   *   <li>{@code cat > ~/.gitconfig}</li>
   * </ul>
   * Read-only invocations (e.g., {@code cat ~/.gitconfig} without redirection) are not matched.
   */
  private static final Pattern SHELL_WRITE_GITCONFIG_PATTERN = Pattern.compile(
    // echo/printf piped or redirected to a gitconfig path
    "(?:echo|printf)\\b[^\\n]*?>>?\\s*(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // tee targeting a gitconfig path (tee writes by definition)
      // Matches: tee, tee -a, tee --append, tee -ai, etc.
      "tee\\b(?:\\s+(?:--?[a-zA-Z-]+(?:=\\S+)?)*)*\\s+(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // cp (copy) targeting a gitconfig path
      "cp\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // mv (move) targeting a gitconfig path
      "mv\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // install command targeting a gitconfig path
      "install\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // sed -i editing a gitconfig path in place
      "sed\\s+(?:-[a-z]*i[a-z]*|--in-place)\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // awk writing output to a gitconfig path
      "awk\\b[^\\n]*?>\\s*(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|" +
      // cat with output redirection to a gitconfig path
      "cat\\b[^\\n]*?>\\s*(?:" + GITCONFIG_PATH_PATTERN + ")",
    Pattern.CASE_INSENSITIVE);

  /**
   * Creates a new handler for blocking git user identity changes.
   */
  public BlockGitUserConfigChange()
  {
    // Handler class
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();

    // Check for direct shell writes to canonical gitconfig paths (does not require "git" keyword)
    boolean mentionsGitconfigPath = command.contains(".gitconfig") ||
      command.contains("git/config") || command.contains("/etc/gitconfig");
    if (mentionsGitconfigPath && SHELL_WRITE_GITCONFIG_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: direct write to gitconfig file without explicit user request**

        Writing directly to `~/.gitconfig`, `~/.config/git/config`, or `/etc/gitconfig` via shell \
        tools (echo, printf, tee, sed, awk, cat) bypasses git's identity protection and silently \
        overwrites the author information on every future commit.

        Only change git identity when the user explicitly asks you to (e.g., "set my git username \
        to Alice").

        To read or modify git identity safely:
        ```bash
        git config user.name        # read current name
        git config user.email       # read current email
        ```
        """);
    }

    // Quick exit: must contain "git" plus any relevant marker
    if (!command.contains("git"))
      return Result.allow();
    boolean mentionsUserIdentity = command.contains("user.name") || command.contains("user.email") ||
      command.contains("--remove-section");
    boolean mentionsIdentityOverride =
      command.contains("GIT_AUTHOR_NAME") || command.contains("GIT_AUTHOR_EMAIL") ||
        command.contains("GIT_COMMITTER_NAME") || command.contains("GIT_COMMITTER_EMAIL") ||
        command.contains("--author");
    if (!mentionsUserIdentity && !mentionsIdentityOverride)
      return Result.allow();

    // Allow read-only access (bare key at end of command, or --get flag)
    if (READ_USER_IDENTITY_PATTERN.matcher(command).find())
      return Result.allow();

    // Block write/unset operations on user identity
    if (WRITE_USER_IDENTITY_PATTERN.matcher(command).find())
    {
      String key;
      if (command.contains("user.email"))
        key = "user.email";
      else
        key = "user.name";
      return Result.block("""
        **BLOCKED: git config %s cannot be changed without explicit user request**

        Modifying git commit identity (user.name / user.email) silently overwrites the author \
        information on every future commit.

        Only change git identity when the user explicitly asks you to (e.g., "set my git username \
        to Alice").

        To read the current value:
        ```bash
        git config user.name
        git config user.email
        ```
        """.formatted(key));
    }

    // Block removal of the entire [user] section
    if (REMOVE_USER_SECTION_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: git config --remove-section user cannot be run without explicit user request**

        Removing the [user] section deletes both user.name and user.email, silently clearing the \
        git commit identity.

        Only change git identity when the user explicitly asks you to (e.g., "remove my git \
        identity configuration").

        To read the current value:
        ```bash
        git config user.name
        git config user.email
        ```
        """);
    }

    // Block inline identity override via git -c user.name=... or git -c user.email=...
    if (INLINE_USER_IDENTITY_PATTERN.matcher(command).find())
    {
      String key;
      if (command.contains("user.email"))
        key = "user.email";
      else
        key = "user.name";
      return Result.block("""
        **BLOCKED: git -c %s=... cannot be used without explicit user request**

        The -c flag sets git commit identity inline for the entire command, silently overriding \
        the author information for every commit produced by that invocation.

        Only change git identity when the user explicitly asks you to (e.g., "set my git username \
        to Alice").

        To read the current value:
        ```bash
        git config user.name
        git config user.email
        ```
        """.formatted(key));
    }

    // Block git identity override via environment variables
    if (GIT_IDENTITY_ENV_VAR_PATTERN.matcher(command).find())
    {
      String key;
      if (command.contains("GIT_AUTHOR_EMAIL") || command.contains("GIT_COMMITTER_EMAIL"))
        key = "GIT_AUTHOR_EMAIL / GIT_COMMITTER_EMAIL";
      else
        key = "GIT_AUTHOR_NAME / GIT_COMMITTER_NAME";
      return Result.block("""
        **BLOCKED: %s cannot be set without explicit user request**

        Setting these environment variables overrides the git commit identity for the \
        entire invocation, silently changing the author information on produced commits.

        Only change git identity when the user explicitly asks you to (e.g., "set my git \
        username to Alice").

        To read the current value:
        ```bash
        git config user.name
        git config user.email
        ```
        """.formatted(key));
    }

    // Block git commit --author flag
    if (GIT_COMMIT_AUTHOR_PATTERN.matcher(command).find())
    {
      return Result.block("""
        **BLOCKED: git commit --author cannot be used without explicit user request**

        The --author flag overrides the commit author identity for a single commit, silently \
        changing the author information without modifying git config.

        Only change git identity when the user explicitly asks you to (e.g., "commit as Alice").

        To read the current value:
        ```bash
        git config user.name
        git config user.email
        ```
        """);
    }

    return Result.allow();
  }
}

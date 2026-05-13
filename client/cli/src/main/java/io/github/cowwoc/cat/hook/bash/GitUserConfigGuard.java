/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hook.bash;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blocks modifications to git commit identity without explicit user request.
 */
public final class GitUserConfigGuard
{
  private static final Pattern WRITE_USER_IDENTITY_PATTERN = Pattern.compile(
    "git\\s+config\\b" +
      "(?:(?!--get(?:-all|-regexp)?\\b|--list\\b)[^\\n])*?" +
      "(?:--unset(?:-all)?\\s+user\\.(?:name|email)\\b" +
      "|user\\.(?:name|email)\\s+(?!-)[^\\n]+)",
    Pattern.CASE_INSENSITIVE);
  private static final Pattern REMOVE_USER_SECTION_PATTERN = Pattern.compile(
    "git\\s+config\\b.*?--remove-section\\s+user\\b",
    Pattern.CASE_INSENSITIVE);
  private static final Pattern INLINE_USER_IDENTITY_PATTERN = Pattern.compile(
    "git\\b(?:\\s+\\S+)*?\\s+-c\\s+user\\.(?:name|email)\\s*=",
    Pattern.CASE_INSENSITIVE);
  private static final Pattern GIT_IDENTITY_ENV_VAR_PATTERN = Pattern.compile(
    "GIT_(?:AUTHOR|COMMITTER)_(?:NAME|EMAIL)\\s*=",
    Pattern.CASE_INSENSITIVE);
  private static final Pattern GIT_COMMIT_AUTHOR_PATTERN = Pattern.compile(
    "git\\s+commit\\b.*?--author[=\\s]",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern READ_USER_IDENTITY_PATTERN = Pattern.compile(
    "git\\s+config\\b(?!.*--unset).*?(?:--get(?:-all|-regexp)?\\s+)?user\\.(?:name|email)\\s*$",
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  private static final String GITCONFIG_PATH_PATTERN =
    "(?:~|\\$(?:HOME|\\{HOME\\}))/\\.gitconfig" +
      "|(?:~|\\$(?:HOME|\\{HOME\\}))/\\.config/git/config" +
      "|/etc/gitconfig";
  private static final Pattern GITCONFIG_PATH = Pattern.compile(GITCONFIG_PATH_PATTERN,
    Pattern.CASE_INSENSITIVE);
  private static final Pattern READ_ONLY_GITCONFIG_PATTERN = Pattern.compile(
    "^\\s*(?:cat|less|more|head|tail|grep|rg|wc|ls|stat|test)\\b[^\\n]*(?:" +
      GITCONFIG_PATH_PATTERN + ")\\s*$" +
      "|git\\s+config\\b(?:(?!--unset(?:-all)?\\b|--remove-section\\b)[^\\n])*?" +
      "(?:--get(?:-all|-regexp)?\\b|--list\\b)[^\\n]*(?:" + GITCONFIG_PATH_PATTERN + ")?\\s*$",
    Pattern.CASE_INSENSITIVE);
  private static final Pattern SHELL_WRITE_GITCONFIG_PATTERN = Pattern.compile(
    "(?:echo|printf)\\b[^\\n]*?>>?\\s*(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|tee\\b(?:\\s+(?:--?[a-zA-Z-]+(?:=\\S+)?)*)*\\s+(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|cp\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|mv\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|install\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|sed\\s+(?:-[a-z]*i[a-z]*|--in-place)\\b[^\\n]*?(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|awk\\b[^\\n]*?>\\s*(?:" + GITCONFIG_PATH_PATTERN + ")" +
      "|cat\\b[^\\n]*?>\\s*(?:" + GITCONFIG_PATH_PATTERN + ")",
    Pattern.CASE_INSENSITIVE);

  /**
   * Prevents construction.
   */
  private GitUserConfigGuard()
  {
  }

  /**
   * Checks the command.
   *
   * @param command the shell command
   * @return the reason for blocking, or an empty string if allowed
   */
  public static String getBlockReason(String command)
  {
    String lowercaseCommand = command.toLowerCase(Locale.ROOT);
    if (mentionsCanonicalGitconfigPath(command) && !isReadOnlyGitconfigCommand(command))
      return "BLOCKED: direct write to gitconfig file without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";

    if (!lowercaseCommand.contains("git"))
      return "";
    boolean mentionsUserIdentity = lowercaseCommand.contains("user.name") ||
      lowercaseCommand.contains("user.email") || lowercaseCommand.contains("--remove-section");
    boolean mentionsIdentityOverride =
      lowercaseCommand.contains("git_author_name") || lowercaseCommand.contains("git_author_email") ||
        lowercaseCommand.contains("git_committer_name") || lowercaseCommand.contains("git_committer_email") ||
        lowercaseCommand.contains("--author");
    if (!mentionsUserIdentity && !mentionsIdentityOverride)
      return "";
    if (READ_USER_IDENTITY_PATTERN.matcher(command).find())
      return "";
    if (WRITE_USER_IDENTITY_PATTERN.matcher(command).find())
      return "BLOCKED: git config " + getGitIdentityKey(command) +
        " cannot be changed without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";
    if (REMOVE_USER_SECTION_PATTERN.matcher(command).find())
      return "BLOCKED: git config --remove-section user cannot be run without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";
    if (INLINE_USER_IDENTITY_PATTERN.matcher(command).find())
      return "BLOCKED: git -c " + getGitIdentityKey(command) +
        "=... cannot be used without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";
    if (GIT_IDENTITY_ENV_VAR_PATTERN.matcher(command).find())
      return "BLOCKED: git identity environment variables cannot be set without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";
    if (GIT_COMMIT_AUTHOR_PATTERN.matcher(command).find())
      return "BLOCKED: git commit --author cannot be used without explicit user request. " +
        "Only change git identity when the user explicitly asks you to.";
    return "";
  }

  /**
   * Returns true if the command names a canonical git configuration file.
   *
   * @param command the shell command
   * @return true if a canonical git configuration path is present
   */
  private static boolean mentionsCanonicalGitconfigPath(String command)
  {
    return GITCONFIG_PATH.matcher(command).find();
  }

  /**
   * Returns true if the command is a recognized read-only git configuration inspection.
   *
   * @param command the shell command
   * @return true if the command only reads git configuration
   */
  private static boolean isReadOnlyGitconfigCommand(String command)
  {
    return !SHELL_WRITE_GITCONFIG_PATTERN.matcher(command).find() &&
      READ_ONLY_GITCONFIG_PATTERN.matcher(command).find();
  }

  /**
   * Identifies which git identity key is present in the command.
   *
   * @param command the shell command
   * @return {@code user.email} when mentioned; otherwise {@code user.name}
   */
  private static String getGitIdentityKey(String command)
  {
    if (command.toLowerCase(Locale.ROOT).contains("user.email"))
      return "user.email";
    return "user.name";
  }
}

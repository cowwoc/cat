/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;


import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.ShellParser;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * Loads skill content from a plugin's skill directory structure.
 * <p>
 * On first invocation for a given skill, returns the full {@code first-use.md} content with
 * preprocessor directives expanded. On subsequent invocations within the same session, returns a
 * short "already loaded" reference message and re-executes the single {@code !} preprocessor
 * directive from {@code first-use.md} (if present) to produce fresh output.
 * <p>
 * <b>Marker files and hash tracking:</b> Each loaded skill is tracked by a marker file in
 * {@code loadedDir}, named after the URL-encoded qualified skill name. Marker files contain the
 * SHA-256 hex digest of the skill's {@code first-use.md} at the time the skill was first loaded.
 * On each {@link #load(String)} call, the stored digest is compared to the current digest. A
 * mismatch invalidates the marker and triggers a full first-use reload — ensuring Claude always
 * receives updated skill instructions after a plugin upgrade.
 * <p>
 * <b>Skill directory structure:</b>
 * <pre>
 * plugin-root/
 *   skills/
 *     {skill-name}/
 *       first-use.md            — Skill content with at most one {@code !} preprocessor directive
 * </pre>
 * <p>
 * <b>Single-directive constraint:</b> Each {@code first-use.md} may contain at most one
 * {@code !} preprocessor directive. If more than one directive is found, {@code GetSkill} fails
 * with a validation error. Skills without a directive return only the "already loaded" reference
 * message on subsequent loads.
 * <p>
 * <b>Variable substitution:</b> Variable substitution applies only inside {@code !} directive strings.
 * Content body passes through untouched to Claude Code, which handles {@code ${VAR}} expansion natively.
 * <p>
 * Inside {@code !} directive strings, the following variable forms are expanded:
 * <ul>
 *   <li>{@code ${name}} — resolved via {@link System#getenv(String)}</li>
 *   <li>{@code ${CLAUDE_SKILL_DIR}} — resolved by GetSkill to the skill's directory
 *       ({@code pluginRoot/skills/{skill-name}/})</li>
 *   <li>{@code $0}, {@code $1}, ..., {@code $N} — resolved to skill positional arguments</li>
 *   <li>{@code $ARGUMENTS} — all skill arguments joined with a space</li>
 *   <li>{@code $ARGUMENTS[N]} — the Nth skill argument (0-based)</li>
 * </ul>
 * <p>
 * Undefined variables are passed through unchanged.
 * <p>
 * <b>Positional arguments:</b> The first positional argument ({@code $0}) is the agent ID, which
 * identifies the agent instance and is used to determine the per-agent marker file path. Arguments with
 * spaces are preserved in the substitution. Use the {@code argument-hint} frontmatter field to document
 * expected arguments. {@code $0}.{@code $N}, {@code $ARGUMENTS}, and {@code $ARGUMENTS[N]} are expanded
 * inside {@code !} directive strings only — not in the content body.
 * <p>
 * <b>Preprocessor directives:</b> Lines containing {@code !`"path" [args]`} patterns are processed
 * by expanding variables in the directive string, then when the path's filename matches a launcher in
 * the {@code client/bin/} lookup directory, instantiating the class as a {@link SkillOutput} and calling
 * {@link SkillOutput#getOutput(String[])} to replace the directive with the output.
 * <p>
 * <b>Usage:</b> {@code get-skill <skill-name> <catAgentId> [skill-args...]}
 * <br>
 * The {@code catAgentId} argument is mandatory. Main agents pass {@code ${CLAUDE_SESSION_ID}};
 * subagents pass the value injected by SubagentStartHook (e.g., {@code {sessionId}/subagents/{agent_id}}).
 * The {@code pluginRoot} is read from the JVM environment via {@link JvmScope}.
 *
 * @see io.github.cowwoc.cat.hooks.session.ClearAgentMarkers
 */
public final class GetSkill
{
  /**
   * Standard subdirectory within a session directory for storing per-subagent marker files.
   */
  public static final String SUBAGENTS_DIR = "subagents";
  /**
   * Name of the directory that tracks which skills and files have been loaded by an agent.
   * <p>
   * Marker files for loaded skills use the URL-encoded skill name as the filename
   * (e.g., {@code cat%3Aadd} for {@code cat:add}). Marker files for loaded files use the
   * URL-encoded absolute file path as the filename.
   */
  public static final String LOADED_DIR = "loaded";

  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern PREPROCESSOR_DIRECTIVE_PATTERN = Pattern.compile(
    "!`\"([^\"\n]+)\"([ \t]+[^`\n]+)?`");
  private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
    "\\A---\\n.*?\\n---\\n?", Pattern.DOTALL);
  private static final Pattern POSITIONAL_INDEXED_ARG_PATTERN = Pattern.compile("\\$(\\d+)");
  private static final Pattern ARGUMENTS_INDEXED_PATTERN = Pattern.compile("\\$ARGUMENTS\\[(\\d+)]");
  private static final String LAUNCHER_DIRECTORY = "client/bin";
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private final ClaudeTool scope;
  private final Path pluginRoot;
  private final List<String> skillArgs;
  private final Path loadedDir;
  private final Map<String, String> skillHashes;
  private final String pluginPrefix;

  /**
   * Creates a new GetSkill instance.
   * <p>
   * The CAT agent ID is derived from the first positional skill argument ({@code skillArgs.get(0)}). If
   * the first element contains a space, it is split on the first space: the prefix becomes the agent ID
   * ({@code $0}) and the remainder is inserted as {@code $1}, shifting any existing {@code $1}..$N
   * arguments to {@code $2}..$N+1. This handles callers that pass {@code "catAgentId description text"}
   * as a single quoted argument. If the first element is blank, it is rejected with an
   * {@link IllegalArgumentException} because a blank agent ID is a skill misconfiguration. If
   * {@code skillArgs} is empty, the SKILL.md is misconfigured (missing {@code "$0"}) and the constructor
   * fails fast.
   * <p>
   * The plugin root and project directory are read from {@code scope} via
   * {@link ClaudeTool#getPluginRoot()} and {@link JvmScope#getProjectPath()}.
   * When invoked from {@link #main(String[])}, the scope is a {@link MainClaudeTool}.
   *
   * @param scope the scope providing access to session paths, shared services, and environment paths
   * @param skillArgs pre-tokenized positional arguments; the first element ({@code $0}) is the CAT agent ID,
   *   optionally followed by a space and description text to insert as {@code $1}
   * @throws NullPointerException if {@code scope} or {@code skillArgs} are null
   * @throws IllegalArgumentException if {@code skillArgs} is empty, if the first element (catAgentId)
   *   is blank, or if the catAgentId does not match a valid UUID or subagent ID format
   * @throws IOException if the plugin root directory does not exist, or if the agent marker file cannot be read
   */
  public GetSkill(ClaudeTool scope, List<String> skillArgs) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(skillArgs, "skillArgs").isNotNull();

    if (skillArgs.isEmpty())
    {
      throw new IllegalArgumentException(
        "catAgentId is required as the first skill argument ($0) but was not provided. " +
          "Main agents must pass their session ID (${CLAUDE_SESSION_ID}); " +
          "subagents must pass the value injected by SubagentStartHook " +
          "(e.g., {sessionId}/subagents/{agent_id}).");
    }

    // When $0 contains a space, split into catAgentId (before space) and remainder (after space).
    // This handles callers that pass "catAgentId description text" as a single quoted argument:
    // the catAgentId is path-safe (no spaces), while the description is inserted as $1,
    // shifting any existing $1..$N arguments to $2..$N+1.
    String catAgentId;
    List<String> tokens = new ArrayList<>(skillArgs);
    String firstArg = skillArgs.getFirst();
    int spaceIndex = firstArg.indexOf(' ');
    if (spaceIndex > 0)
    {
      catAgentId = firstArg.substring(0, spaceIndex);
      String remainder = firstArg.substring(spaceIndex + 1);
      tokens.set(0, catAgentId);
      tokens.add(1, remainder);
    }
    else
    {
      catAgentId = firstArg;
      tokens.set(0, catAgentId);
    }

    // Blank agentId is a skill misconfiguration - the SKILL.md must provide $0.
    // Fail fast instead of falling back to an env var.
    if (catAgentId.isBlank())
    {
      throw new IllegalArgumentException(
        "catAgentId is blank. The SKILL.md must provide a non-blank agent ID as the first argument ($0). " +
          "Main agents pass ${CLAUDE_SESSION_ID}; subagents pass the value injected by SubagentStartHook.");
    }

    // Validate catAgentId format. A valid catAgentId is either a UUID (main agent) or
    // "{uuid}/subagents/{agentId}" (subagent). If the value doesn't match either pattern,
    // the model passed the wrong argument (e.g., a branch name or path). Fail fast to
    // prevent marker files from being created in wrong directories.
    if (!AgentIdPatterns.SESSION_ID_PATTERN.matcher(catAgentId).matches() &&
      !AgentIdPatterns.SUBAGENT_ID_PATTERN.matcher(catAgentId).matches())
    {
      throw new IllegalArgumentException(
        "catAgentId '" + catAgentId + "' does not match a valid format. " +
          "Expected: UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx) for main agents, or " +
          "UUID/subagents/{agentId} for subagents. " +
          "The model must pass the value injected by SubagentStartHook ($0), not a branch name or path.");
    }

    this.scope = scope;
    this.pluginRoot = scope.getPluginRoot();
    if (!Files.isDirectory(pluginRoot))
    {
      throw new IOException(
        "Plugin root directory does not exist or is not a directory: " + pluginRoot + ". " +
          "Ensure CLAUDE_PLUGIN_ROOT points to a valid plugin installation directory.");
    }
    this.skillArgs = List.copyOf(tokens);
    this.pluginPrefix = scope.getPluginPrefix();

    Path baseDir = scope.getClaudeSessionsPath().toAbsolutePath().normalize();
    Path agentDir = resolveAndValidateContainment(baseDir, catAgentId,
      "catAgentId");
    this.loadedDir = agentDir.resolve(LOADED_DIR);

    Files.createDirectories(loadedDir);
    this.skillHashes = new HashMap<>();

    try (Stream<Path> stream = Files.list(loadedDir))
    {
      List<Path> markerFiles = stream.toList();
      for (Path markerFile : markerFiles)
      {
        String qualifiedName = URLDecoder.decode(markerFile.getFileName().toString(), UTF_8);
        String storedHash = Files.readString(markerFile, UTF_8);
        if (storedHash.isEmpty())
        {
          Files.delete(markerFile);
          continue;
        }
        skillHashes.put(qualifiedName, storedHash);
      }
    }
  }

  /**
   * Loads a skill, returning full content on first load or a dynamically generated reference on
   * subsequent loads.
   * <p>
   * On first load: returns full {@code first-use.md} content with preprocessor directives expanded.
   * On subsequent loads: returns a short "already loaded" reference message. If the {@code first-use.md}
   * contains a single {@code !} preprocessor directive, that directive is re-executed and its output is
   * appended to the reference message.
   *
   * @param skillName the skill name
   * @return the skill content with preprocessor directives expanded
   * @throws NullPointerException if {@code skillName} is null
   * @throws IllegalArgumentException if {@code skillName} is blank, or if {@code first-use.md} contains
   *   more than one {@code !} preprocessor directive
   * @throws IOException if skill files cannot be read
   */
  public String load(String skillName) throws IOException
  {
    requireThat(skillName, "skillName").isNotBlank();

    String rawContent = loadRawContent(skillName);
    String content = stripFrontmatter(rawContent);

    String qualifiedName = qualifySkillName(skillName);
    String storedHash = skillHashes.get(qualifiedName);
    if (storedHash != null)
    {
      String currentHash = computeFirstUseHash(skillName);
      if (storedHash.equals(currentHash))
      {
        // Hash matches: skill content unchanged, use cached marker
        return buildSubsequentLoadResponse(skillName, content);
      }
      // Hash mismatch: first-use.md was updated (e.g., plugin upgrade) — invalidate marker
      String encodedName = URLEncoder.encode(qualifiedName, UTF_8);
      Files.deleteIfExists(loadedDir.resolve(encodedName));
      skillHashes.remove(qualifiedName);
    }
    // First load or invalidated marker: return full expanded content
    markSkillLoaded(qualifiedName, skillName);
    return processPreprocessorDirectives(content, skillName);
  }

  /**
   * Builds the response for a subsequent (non-first) skill load.
   * <p>
   * Returns a short "already loaded" reference message. If the {@code first-use.md} content contains
   * exactly one {@code !} preprocessor directive, that directive is re-executed and its output is
   * appended. If more than one directive is found, fails with a validation error.
   *
   * @param skillName the skill name
   * @param content the {@code first-use.md} content with frontmatter already stripped
   * @return the subsequent-load response
   * @throws IllegalArgumentException if the content contains more than one {@code !} preprocessor directive
   * @throws IOException if directive execution fails
   */
  private String buildSubsequentLoadResponse(String skillName, String content) throws IOException
  {
    List<String> directives = findPreprocessorDirectives(content);
    if (directives.size() > 1)
    {
      throw new IllegalArgumentException(
        "first-use.md for skill '" + skillName + "' contains " + directives.size() +
          " preprocessor directives but at most one is allowed. " +
          "GetSkill re-executes the single directive on subsequent loads. " +
          "Split the skill or reduce to a single directive.");
    }

    StringBuilder output = new StringBuilder(512);
    output.append("""
      The skill instructions were already loaded earlier in this conversation.
      Use the Skill tool to invoke this skill again with the same arguments.
      The skill script generates fresh, accurate output on every invocation.
      Execute the skill instructions in FULL - do NOT summarize, paraphrase, or abbreviate any output.""");

    if (!directives.isEmpty())
    {
      String directive = directives.getFirst();
      String directiveOutput = executeSingleDirective(directive, skillName);
      output.append("\n\n").append(directiveOutput);
    }

    return output.toString();
  }

  /**
   * Finds all {@code !} preprocessor directive strings in the content.
   *
   * @param content the content to scan
   * @return a list of complete directive strings (e.g., {@code !`"path" args`})
   */
  private static List<String> findPreprocessorDirectives(String content)
  {
    List<String> directives = new ArrayList<>();
    Matcher matcher = PREPROCESSOR_DIRECTIVE_PATTERN.matcher(content);
    while (matcher.find())
      directives.add(matcher.group(0));
    return directives;
  }

  /**
   * Extracts and expands the launcher path and arguments from a matched preprocessor directive.
   * <p>
   * Given a {@link Matcher} that has already matched a preprocessor directive, expands variables in
   * the launcher path (group 1) and optional arguments token (group 2), then tokenizes the expanded
   * arguments using the shell parser.
   *
   * @param matcher   a {@link Matcher} positioned on a preprocessor directive match
   * @param skillName the skill name, used to resolve {@code ${CLAUDE_SKILL_DIR}}
   * @return a two-element array: {@code [0]} is the expanded launcher path string, {@code [1..N]}
   *   are the tokenized arguments (may be empty if no arguments are present)
   */
  private String[] expandDirectiveToArguments(Matcher matcher, String skillName)
  {
    String launcherPath = expandDirectiveString(matcher.group(1), skillName);
    String argumentsToken = matcher.group(2);
    String expandedArgs;
    if (argumentsToken == null)
      expandedArgs = null;
    else
      expandedArgs = expandDirectiveString(argumentsToken.strip(), skillName);
    String[] arguments;
    if (expandedArgs == null)
      arguments = new String[0];
    else
    {
      List<String> tokenList = ShellParser.tokenize(expandedArgs);
      arguments = tokenList.toArray(new String[0]);
    }
    // Prepend launcher path as element [0] in a combined array for the caller.
    // Caller unpacks: [0] = launcher path, [1..] = arguments to pass to the directive.
    String[] result = new String[1 + arguments.length];
    result[0] = launcherPath;
    System.arraycopy(arguments, 0, result, 1, arguments.length);
    return result;
  }

  /**
   * Executes a single preprocessor directive string and returns the output.
   *
   * @param directive the complete directive string (e.g., {@code !`"path" args`})
   * @param skillName the skill name, used to resolve {@code ${CLAUDE_SKILL_DIR}}
   * @return the output from executing the directive
   * @throws IOException if execution fails
   */
  private String executeSingleDirective(String directive, String skillName) throws IOException
  {
    Matcher matcher = PREPROCESSOR_DIRECTIVE_PATTERN.matcher(directive);
    if (!matcher.find())
      return directive;

    String[] expanded = expandDirectiveToArguments(matcher, skillName);
    String launcherPath = expanded[0];
    String[] arguments = new String[expanded.length - 1];
    System.arraycopy(expanded, 1, arguments, 0, arguments.length);

    return executeDirective(launcherPath, arguments, directive);
  }

  /**
   * Loads raw content (including frontmatter) from the skill's {@code first-use.md} file.
   * <p>
   * For {@code *-agent} skills that have no {@code first-use.md} of their own, falls back to the
   * parent skill's {@code first-use.md} (the same name without the {@code -agent} suffix), if it
   * exists. This covers the case where an agent variant delegates its full workflow to its parent.
   * Only one level of {@code -agent} suffix is stripped.
   *
   * @param skillName the skill name
   * @return the raw content including YAML frontmatter
   * @throws IOException if {@code first-use.md} is not found or cannot be read
   */
  private String loadRawContent(String skillName) throws IOException
  {
    Path skillsDir = pluginRoot.resolve("skills").toAbsolutePath().normalize();
    String dirName = stripPrefix(skillName);
    Path contentPath = pluginRoot.resolve("skills/" + dirName + "/first-use.md").toAbsolutePath().normalize();
    if (!contentPath.startsWith(skillsDir))
    {
      throw new IllegalArgumentException("skillName contains path traversal: '" + skillName +
        "'. Expected path under: " + skillsDir);
    }

    // Fall back to parent skill's first-use.md for *-agent variants that have none of their own.
    // Only one level of -agent suffix is stripped: "add-agent" → "add", not "add-agent-agent" → "add".
    if (Files.notExists(contentPath) && dirName.endsWith("-agent"))
    {
      String parentDirName = dirName.substring(0, dirName.length() - "-agent".length());
      contentPath = pluginRoot.resolve("skills/" + parentDirName + "/first-use.md").toAbsolutePath().normalize();
      if (!contentPath.startsWith(skillsDir))
      {
        throw new IllegalArgumentException("skillName (parent) contains path traversal: '" + skillName +
          "'. Expected path under: " + skillsDir);
      }
    }

    return Files.readString(contentPath, UTF_8);
  }

  /**
   * Strips the plugin prefix from a qualified skill name.
   * <p>
   * For example, {@code "cat:git-commit"} becomes {@code "git-commit"}.
   * Names without a prefix are returned unchanged.
   *
   * @param qualifiedName the qualified skill name
   * @return the bare skill name without the prefix
   */
  public static String stripPrefix(String qualifiedName)
  {
    int colonIndex = qualifiedName.indexOf(':');
    if (colonIndex >= 0)
      return qualifiedName.substring(colonIndex + 1);
    return qualifiedName;
  }

  /**
   * Strips YAML frontmatter from the beginning of content.
   * <p>
   * Removes the leading {@code ---\n...\n---\n} block if present. This is used when loading
   * {@code first-use.md} files which carry frontmatter for Claude Code's own use.
   *
   * @param content the content to process
   * @return the content with YAML frontmatter removed, or unchanged if none found
   */
  private static String stripFrontmatter(String content)
  {
    Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
    if (matcher.find())
      return content.substring(matcher.end());
    return content;
  }

  /**
   * Processes preprocessor directives in content.
   * <p>
   * Scans for patterns like {@code !`"path/to/launcher" [args]`}, expands variables in the launcher
   * path and arguments token using {@link #expandDirectiveString(String, String)}, then when the launcher's
   * filename matches a file in the {@code client/bin/} launcher lookup directory, instantiates the
   * corresponding Java class as a {@link SkillOutput} and calls {@link SkillOutput#getOutput(String[])}
   * to replace the directive with the output.
   *
   * @param content the content to process
   * @param skillName the skill name, used to resolve {@code ${CLAUDE_SKILL_DIR}}
   * @return the content with preprocessor directives replaced by their output
   * @throws IOException if directive processing fails
   */
  private String processPreprocessorDirectives(String content, String skillName) throws IOException
  {
    Matcher matcher = PREPROCESSOR_DIRECTIVE_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      result.append(content, lastEnd, matcher.start());
      String originalDirective = matcher.group(0);
      String[] expanded = expandDirectiveToArguments(matcher, skillName);
      String launcherPath = expanded[0];
      String[] arguments = new String[expanded.length - 1];
      System.arraycopy(expanded, 1, arguments, 0, arguments.length);
      String output = executeDirective(launcherPath, arguments, originalDirective);
      result.append(output);
      lastEnd = matcher.end();
    }
    result.append(content.substring(lastEnd));

    return result.toString();
  }

  /**
   * Expands variable references within a directive string.
   * <p>
   * Applied to both the launcher path and arguments token of {@code !} directives. The following
   * substitutions are performed in order:
   * <ol>
   *   <li>{@code $ARGUMENTS[N]} — replaced with the Nth skill argument (0-based), or literal
   *       {@code $ARGUMENTS[N]} if out of range</li>
   *   <li>{@code $ARGUMENTS} — replaced with all skill arguments joined with a space</li>
   *   <li>{@code $N} — replaced with the Nth skill positional argument, or literal {@code $N} if
   *       out of range</li>
   *   <li>{@code ${name}} — resolved via {@link #resolveVariable(String, String)}</li>
   * </ol>
   *
   * @param text the directive string to expand
   * @param skillName the skill name, used to resolve {@code ${CLAUDE_SKILL_DIR}}
   * @return the text with all recognized variable forms expanded
   */
  private String expandDirectiveString(String text, String skillName)
  {
    // Step 1: $ARGUMENTS[N] — indexed access (must come before $ARGUMENTS to avoid partial match)
    Matcher indexedMatcher = ARGUMENTS_INDEXED_PATTERN.matcher(text);
    StringBuilder step1 = new StringBuilder();
    int lastEnd = 0;
    while (indexedMatcher.find())
    {
      step1.append(text, lastEnd, indexedMatcher.start());
      int index = Integer.parseInt(indexedMatcher.group(1));
      if (index >= 0 && index < skillArgs.size())
        step1.append(skillArgs.get(index));
      else
        step1.append(indexedMatcher.group(0));
      lastEnd = indexedMatcher.end();
    }
    step1.append(text.substring(lastEnd));

    // Step 2: $ARGUMENTS — all skill args joined with space
    String step2 = step1.toString().replace("$ARGUMENTS", String.join(" ", skillArgs));

    // Step 3: $N — positional args
    Matcher positionalMatcher = POSITIONAL_INDEXED_ARG_PATTERN.matcher(step2);
    StringBuilder step3 = new StringBuilder();
    lastEnd = 0;
    while (positionalMatcher.find())
    {
      step3.append(step2, lastEnd, positionalMatcher.start());
      int index = Integer.parseInt(positionalMatcher.group(1));
      if (index >= 0 && index < skillArgs.size())
        step3.append(skillArgs.get(index));
      else
        step3.append(positionalMatcher.group(0));
      lastEnd = positionalMatcher.end();
    }
    step3.append(step2.substring(lastEnd));

    // Step 4: ${name} — environment variable lookup via resolveVariable()
    Matcher varMatcher = VAR_PATTERN.matcher(step3);
    StringBuilder step4 = new StringBuilder();
    lastEnd = 0;
    while (varMatcher.find())
    {
      step4.append(step3, lastEnd, varMatcher.start());
      String varName = varMatcher.group(1);
      step4.append(resolveVariable(varName, skillName));
      lastEnd = varMatcher.end();
    }
    step4.append(step3.substring(lastEnd));

    return step4.toString();
  }

  /**
   * Executes a preprocessor directive by invoking the corresponding Java class.
   *
   * @param launcherPath the path to the launcher script
   * @param arguments the arguments to pass to getOutput()
   * @param originalDirective the original directive text for error messages
   * @return the output from the directive execution
   * @throws IOException if execution fails
   */
  private String executeDirective(String launcherPath, String[] arguments, String originalDirective)
    throws IOException
  {
    Path launcherFile = Paths.get(launcherPath);
    String launcherName = launcherFile.getFileName().toString();
    Path expectedLauncher = pluginRoot.resolve(LAUNCHER_DIRECTORY + "/" + launcherName);

    if (!Files.exists(expectedLauncher))
      return originalDirective;

    String launcherContent = Files.readString(expectedLauncher, UTF_8);
    String className = extractClassName(launcherContent);
    if (className.isEmpty())
    {
      throw new IOException("Failed to extract class name from launcher: " + expectedLauncher +
        ". Expected '-m module/class' pattern in launcher content.");
    }
    return invokeSkillOutput(className, arguments, originalDirective);
  }

  /**
   * Extracts the fully-qualified class name from a launcher script.
   * <p>
   * Looks for patterns like {@code java -m module/class}, including multi-line launcher scripts
   * that use shell line continuations ({@code \} + newline) between {@code java} and {@code -m}.
   *
   * @param launcherContent the launcher script content
   * @return the fully-qualified class name, or empty string if not found
   * @throws NullPointerException if {@code launcherContent} is null
   */
  public static String extractClassName(String launcherContent)
  {
    requireThat(launcherContent, "launcherContent").isNotNull();
    Pattern pattern = Pattern.compile("java.*?-m\\s+(\\S+)/(\\S+)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(launcherContent);
    if (matcher.find())
      return matcher.group(2);
    return "";
  }

  /**
   * Instantiates a SkillOutput class and invokes getOutput().
   *
   * @param className the fully-qualified class name
   * @param arguments the arguments to pass to getOutput()
   * @param originalDirective the original directive text for error messages
   * @return the output from getOutput()
   * @throws IOException if invocation fails
   */
  private String invokeSkillOutput(String className, String[] arguments, String originalDirective)
    throws IOException
  {
    try
    {
      Class<?> targetClass = Class.forName(className);
      Object instance;
      try
      {
        instance = targetClass.getConstructor(ClaudeTool.class).newInstance(scope);
      }
      catch (NoSuchMethodException e)
      {
        throw new IllegalStateException("SkillOutput class " + targetClass.getName() +
          " must have a constructor that takes ClaudeTool", e);
      }
      if (!(instance instanceof SkillOutput skillOutput))
      {
        throw new IOException("Class " + className + " does not implement SkillOutput");
      }
      return skillOutput.getOutput(arguments);
    }
    catch (IOException e)
    {
      throw e;
    }
    catch (InvocationTargetException e)
    {
      Throwable cause = e.getCause();
      if (cause == null)
        cause = e;
      StringWriter stringWriter = new StringWriter();
      cause.printStackTrace(new PrintWriter(stringWriter));
      return buildPreprocessorErrorMessage(originalDirective, cause.toString(), stringWriter.toString());
    }
    catch (Exception e)
    {
      StringWriter stringWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stringWriter));
      return buildPreprocessorErrorMessage(originalDirective, e.toString(), stringWriter.toString());
    }
  }

  /**
   * Builds a user-friendly error message when a preprocessor directive fails.
   * <p>
   * The message includes the directive that failed, the error details, the full stack trace for
   * debugging, and instructions for filing a bug report using {@code /cat:feedback}.
   *
   * @param originalDirective the original preprocessor directive text that failed
   * @param errorMsg the error summary from the exception (via {@code toString()})
   * @param stackTrace the full stack trace from the exception
   * @return a user-friendly error message with bug report instructions
   */
  static String buildPreprocessorErrorMessage(String originalDirective, String errorMsg,
    String stackTrace)
  {
    return """

      ---
      **Preprocessor Error**

      A preprocessor directive failed while loading this skill.

      **Directive:** `%s`
      **Error:** %s

      **Stack Trace:**
      ```
      %s
      ```

      To report this bug, run: `/cat:feedback`
      ---

      """.formatted(originalDirective, errorMsg, stackTrace.stripTrailing());
  }

  /**
   * Resolves a single {@code ${name}} variable inside a directive string.
   * <p>
   * {@code ${CLAUDE_SKILL_DIR}} is resolved by GetSkill to the skill's directory
   * ({@code pluginRoot/skills/{skill-name}/}). All other variables are resolved via
   * {@link System#getenv(String)}. Unknown variables (not set in the environment)
   * are passed through as {@code ${name}} literals.
   *
   * @param varName the variable name (without ${} delimiters)
   * @param skillName the skill name, used to resolve {@code ${CLAUDE_SKILL_DIR}}
   * @return the resolved value, or the original {@code ${varName}} literal if undefined
   */
  private String resolveVariable(String varName, String skillName)
  {
    if (varName.equals("CLAUDE_SKILL_DIR"))
      return pluginRoot.resolve("skills").resolve(stripPrefix(skillName)).toString();
    String envValue = System.getenv(varName);
    if (envValue == null)
      return "${" + varName + "}";
    return envValue;
  }

  /**
   * Computes a SHA-256 hex digest of the {@code first-use.md} file for the given skill.
   * <p>
   * Applies the same {@code -agent} fallback logic as {@link #loadRawContent}: if the skill has
   * no {@code first-use.md} of its own and its name ends with {@code -agent}, the parent skill's
   * {@code first-use.md} is tried.
   *
   * @param skillName the skill name (bare or qualified)
   * @return the SHA-256 hex digest of the file contents
   * @throws IOException if the file does not exist or cannot be read
   */
  private String computeFirstUseHash(String skillName) throws IOException
  {
    Path skillsDir = pluginRoot.resolve("skills").toAbsolutePath().normalize();
    String dirName = stripPrefix(skillName);
    Path firstUsePath = pluginRoot.resolve("skills/" + dirName + "/first-use.md").toAbsolutePath().normalize();
    if (!firstUsePath.startsWith(skillsDir))
    {
      throw new IllegalArgumentException("skillName contains path traversal: '" + skillName +
        "'. Expected path under: " + skillsDir);
    }
    if (Files.notExists(firstUsePath) && dirName.endsWith("-agent"))
    {
      String parentDirName = dirName.substring(0, dirName.length() - "-agent".length());
      firstUsePath = pluginRoot.resolve("skills/" + parentDirName + "/first-use.md").toAbsolutePath().normalize();
      if (!firstUsePath.startsWith(skillsDir))
      {
        throw new IllegalArgumentException("skillName (parent) contains path traversal: '" + skillName +
          "'. Expected path under: " + skillsDir);
      }
    }
    if (Files.notExists(firstUsePath))
      throw new IOException(
        "first-use.md not found for skill '" + skillName + "': " + firstUsePath + ". " +
          "Every skill must have a first-use.md file.");
    byte[] fileBytes = Files.readAllBytes(firstUsePath);
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(fileBytes);
      return HEX_FORMAT.formatHex(hashBytes);
    }
    catch (NoSuchAlgorithmException e)
    {
      // SHA-256 is guaranteed by the Java SE specification to be available on all compliant JVMs.
      throw new AssertionError("SHA-256 MessageDigest not available", e);
    }
  }

  /**
   * Marks a skill as loaded by writing the SHA-256 hash of its {@code first-use.md} to a marker
   * file in the loaded directory.
   * <p>
   * The {@code qualifiedName} must include the plugin prefix (e.g., {@code "cat:git-rebase-agent"}).
   * The {@code skillName} is used to locate {@code first-use.md} and compute its hash.
   *
   * @param qualifiedName the fully-qualified skill name
   * @param skillName the skill name (bare or qualified), used to compute the {@code first-use.md} hash
   * @throws IOException if {@code first-use.md} is not found or the marker file cannot be written
   */
  private void markSkillLoaded(String qualifiedName, String skillName) throws IOException
  {
    String hash = computeFirstUseHash(skillName);
    skillHashes.put(qualifiedName, hash);
    String encodedName = URLEncoder.encode(qualifiedName, UTF_8);
    Files.writeString(loadedDir.resolve(encodedName), hash, UTF_8);
  }

  /**
   * Qualifies a skill name with the plugin prefix if not already qualified.
   * <p>
   * For example, {@code "git-rebase-agent"} becomes {@code "cat:git-rebase-agent"}.
   * Names that already contain {@code ':'} are returned unchanged.
   *
   * @param skillName the skill name (bare or qualified)
   * @return the qualified skill name
   * @throws NullPointerException if {@code skillName} is null
   */
  private String qualifySkillName(String skillName)
  {
    if (skillName.contains(":"))
      return skillName;
    return pluginPrefix + ":" + skillName;
  }

  /**
   * Resolves a relative path against the projects base directory and validates that the result does not escape
   * the base directory via path traversal.
   *
   * @param baseDir the base directory (must already be absolute and normalized)
   * @param relativePath the relative path to resolve
   * @param parameterDescription a description of the parameter(s) for use in error messages
   * @return the resolved and normalized path
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if the resolved path escapes {@code baseDir}
   */
  public static Path resolveAndValidateContainment(Path baseDir, String relativePath,
    String parameterDescription)
  {
    requireThat(baseDir, "baseDir").isNotNull();
    requireThat(relativePath, "relativePath").isNotNull();
    requireThat(parameterDescription, "parameterDescription").isNotNull();

    Path resolved = baseDir.resolve(relativePath).toAbsolutePath().normalize();
    if (!resolved.startsWith(baseDir))
    {
      throw new IllegalArgumentException(parameterDescription + " contains path traversal: '" +
        relativePath + "'. Expected path under: " + baseDir);
    }
    return resolved;
  }

  /**
   * Loads and outputs a skill, writing the result to the given stream.
   * <p>
   * Extracted from {@link #main(String[])} to enable testing without requiring hook stdin input.
   *
   * @param scope the scope providing access to plugin root, project paths, and config directory
   * @param args command-line arguments: {@code skill-name catAgentId [skill-args...]}
   * @param out the stream to write skill content to
   * @throws IOException if the skill cannot be loaded
   * @throws NullPointerException if {@code scope}, {@code args}, or {@code out} are null
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out)
    throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    String skillName = args[0];
    List<String> skillArgs = List.of(args).subList(1, args.length);
    GetSkill loader = new GetSkill(scope, skillArgs);
    String result = loader.load(skillName);
    out.print(result);
  }

  /**
   * Entry point for the {@code get-skill} CLI tool.
   * <p>
   * Invoked by the Skill tool preprocessor as:
   * <p>
   * {@code skill-name catAgentId [skill-args...]}
   * <p>
   * The plugin root and project directory are read from the JVM environment via {@link MainClaudeTool}.
   * The agent ID is passed as the first skill argument ({@code skill-args[0]}, i.e., {@code $0}).
   *
   * @param args command-line arguments: skill-name catAgentId [skill-args...]
   */
  public static void main(String[] args)
  {
    if (args.length < 1)
    {
      System.err.println(
        "Usage: get-skill <skill-name> <catAgentId> [skill-args...]");
      System.exit(1);
    }
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.err.println("Error loading skill: " + e.getMessage());
        System.exit(1);
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetSkill.class);
        log.error("Unexpected error", e);
        System.err.println("Error loading skill: " +
          Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        System.exit(1);
      }
    }
  }
}

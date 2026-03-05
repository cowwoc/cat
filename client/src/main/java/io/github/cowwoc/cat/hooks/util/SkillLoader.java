/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.ShellParser;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads skill content from a plugin's skill directory structure.
 * <p>
 * On first invocation for a given skill, loads the full content. On subsequent invocations
 * within the same session, generates a shorter reference text dynamically instead.
 * <p>
 * <b>Skill directory structure:</b>
 * <pre>
 * plugin-root/
 *   skills/
 *     {skill-name}/
 *       first-use.md            — Skill content with optional {@code <output>} tag
 * </pre>
 * <p>
 * <b>Tag-based content:</b> The first-use.md file may contain an optional {@code <output>} tag that separates
 * static instructions from dynamic preprocessor content. Everything before the last {@code <output>} tag
 * is treated as skill instructions. On first use, instructions are wrapped in
 * {@code <instructions skill="X">} tags and followed by an execution trigger. On subsequent uses, only
 * the execution trigger is generated. The {@code <output>} section is always wrapped in
 * {@code <output skill="X">} tags (where X is the skill name) and appended on every invocation.
 * <p>
 * <b>File inclusion via @path:</b> Lines in skill content starting with {@code @} followed by a
 * relative path containing at least one {@code /} (e.g., {@code @concepts/version-paths.md},
 * {@code @config/settings.yaml}) are replaced with the raw file contents (no wrapping). Any file extension
 * is allowed. Paths are resolved relative to the plugin root. Variable substitution is applied to the
 * inlined content. Missing files cause an {@link IOException}.
 * <p>
 * <b>Variable substitution:</b> The following built-in placeholders are replaced in all loaded content:
 * <ul>
 *   <li>{@code ${CLAUDE_PLUGIN_ROOT}} — plugin root directory path</li>
 *   <li>{@code ${CLAUDE_SESSION_ID}} — current session identifier</li>
 *   <li>{@code ${CLAUDE_PROJECT_DIR}} — project directory path</li>
 * </ul>
 * <p>
 * <b>Positional arguments:</b> Skills reference pre-tokenized arguments as {@code $0}, {@code $1},
 * etc. The first positional argument ({@code $0}) is the agent ID, which identifies the agent instance
 * and is used to determine the per-agent marker file path. Arguments with spaces are preserved in the
 * substitution. Use the {@code argument-hint} frontmatter field to document expected arguments.
 * <p>
 * Undefined variables are passed through unchanged, matching Claude Code's native behavior.
 * <p>
 * <b>Preprocessor directives:</b> Lines containing {@code !`"path" [args]`} patterns are processed
 * after variable substitution. When the path's filename matches a launcher in the {@code client/bin/}
 * lookup directory, instantiates the class as a {@link SkillOutput} and calls
 * {@link SkillOutput#getOutput(String[])} to replace the directive with the output.
 * <p>
 * <b>Usage:</b> {@code skill-loader <skill-name> <catAgentId> [skill-args...]}
 * <br>
 * The {@code catAgentId} argument is mandatory. Main agents pass {@code ${CLAUDE_SESSION_ID}};
 * subagents pass the value injected by SubagentStartHook (e.g., {@code {sessionId}/subagents/{agent_id}}).
 * The {@code pluginRoot} and {@code projectDir} are read from the JVM environment via {@link JvmScope}.
 *
 * @see io.github.cowwoc.cat.hooks.session.ClearSkillMarker
 */
public final class SkillLoader
{
  /**
   * Standard subdirectory within a session directory for storing per-subagent marker files.
   */
  public static final String SUBAGENTS_DIR = "subagents";

  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern PATH_PATTERN = Pattern.compile("^@(.+/.+)$", Pattern.MULTILINE);
  private static final Pattern PREPROCESSOR_DIRECTIVE_PATTERN = Pattern.compile(
    "!`\"([^\"]+)\"(\\s+[^`]+)?`");
  private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
    "\\A---\\n.*?\\n---\\n?", Pattern.DOTALL);
  private static final Pattern OUTPUT_TAG_PATTERN = Pattern.compile(
    "<output(?:\\s[^>]*)?>(.+?)</output>", Pattern.DOTALL);
  private static final Pattern INLINE_BACKTICK_PATTERN = Pattern.compile("`[^`\n]+`");
  private static final Pattern POSITIONAL_ARG_PATTERN = Pattern.compile("\\$(\\d+)");
  private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("^```[a-z]*\\s*$(.+?)^```\\s*$",
    Pattern.DOTALL | Pattern.MULTILINE);
  private static final String LAUNCHER_DIRECTORY = "client/bin";
  /**
   * Parsed content from a {@code -first-use} SKILL.md file.
   * <p>
   * The {@code instructions} are output directly on first use. The {@code outputBody}
   * is wrapped in {@code <output skill="X">} tags on every invocation.
   *
   * @param instructions the content before the {@code <output>} tag
   * @param outputBody the content inside the {@code <output>} tag (may be empty)
   */
  private record ParsedContent(String instructions, String outputBody)
  {
    /**
     * Creates a new ParsedContent instance.
     *
     * @param instructions the content before the {@code <output>} tag
     * @param outputBody the content inside the {@code <output>} tag (may be empty)
     * @throws NullPointerException if {@code instructions} or {@code outputBody} are null
     */
    private ParsedContent
    {
      requireThat(instructions, "instructions").isNotNull();
      requireThat(outputBody, "outputBody").isNotNull();
    }
  }

  private final JvmScope scope;
  private final Path pluginRoot;
  private final String projectDir;
  private final List<String> skillArgs;
  private final Path agentMarkerFile;
  private final Set<String> loadedSkills;

  /**
   * Creates a new SkillLoader instance.
   * <p>
   * The CAT agent ID is derived from the first positional skill argument ({@code skillArgs.get(0)}). If
   * the first element contains a space, it is split on the first space: the prefix becomes the agent ID
   * ({@code $0}) and the remainder is inserted as {@code $1}, shifting any existing {@code $1}..$N
   * arguments to {@code $2}..$N+1. This handles callers that pass {@code "catAgentId description text"}
   * as a single quoted argument. If the first element is blank (e.g., when the user invokes a slash
   * command directly without passing args), the session ID from {@code CLAUDE_SESSION_ID} is used as the
   * fallback agent ID. If {@code skillArgs} is empty, the SKILL.md is misconfigured (missing
   * {@code "$0"}) and the constructor fails fast.
   * <p>
   * The plugin root and project directory are read from {@code scope} via
   * {@link JvmScope#getClaudePluginRoot()} and {@link JvmScope#getClaudeProjectDir()}.
   *
   * @param scope the JVM scope for accessing shared services and environment paths
   * @param skillArgs pre-tokenized positional arguments; the first element ({@code $0}) is the CAT agent ID,
   *   optionally followed by a space and description text to insert as {@code $1}
   * @throws NullPointerException if {@code scope} or {@code skillArgs} are null
   * @throws IllegalArgumentException if {@code skillArgs} is empty, or if the first element (catAgentId)
   *   is blank and {@code CLAUDE_SESSION_ID} is also unavailable
   * @throws IOException if the plugin root directory does not exist, or if the agent marker file cannot be read
   */
  public SkillLoader(JvmScope scope, List<String> skillArgs) throws IOException
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
    // When $0 is blank (user invoked slash command directly), fall back to CLAUDE_SESSION_ID.
    String catAgentId;
    List<String> tokens = new ArrayList<>(skillArgs);
    String firstArg = skillArgs.getFirst();
    int spaceIdx = firstArg.indexOf(' ');
    if (spaceIdx >= 0)
    {
      catAgentId = firstArg.substring(0, spaceIdx);
      String remainder = firstArg.substring(spaceIdx + 1);
      tokens.set(0, catAgentId);
      tokens.add(1, remainder);
    }
    else
    {
      if (firstArg.isBlank())
        catAgentId = scope.getClaudeSessionId();
      else
        catAgentId = firstArg;
      tokens.set(0, catAgentId);
    }

    this.scope = scope;
    this.pluginRoot = scope.getClaudePluginRoot();
    if (!Files.isDirectory(pluginRoot))
    {
      throw new IOException(
        "Plugin root directory does not exist or is not a directory: " + pluginRoot + ". " +
          "Ensure CLAUDE_PLUGIN_ROOT points to a valid plugin installation directory.");
    }
    this.projectDir = scope.getClaudeProjectDir().toString();
    this.skillArgs = List.copyOf(tokens);

    Path baseDir = scope.getSessionBasePath().toAbsolutePath().normalize();
    Path agentDir = resolveAndValidateContainment(baseDir, catAgentId,
      "catAgentId");
    this.agentMarkerFile = agentDir.resolve("skills-loaded");

    Files.createDirectories(agentMarkerFile.getParent());
    this.loadedSkills = new HashSet<>();

    if (Files.exists(agentMarkerFile))
    {
      String content = Files.readString(agentMarkerFile, StandardCharsets.UTF_8);
      for (String line : content.split("\n"))
      {
        String trimmed = line.strip();
        if (!trimmed.isEmpty())
          loadedSkills.add(trimmed);
      }
    }
  }

  /**
   * Loads a skill, returning full content on first load or a dynamically generated reference on
   * subsequent loads.
   * <p>
   * When the skill has a {@code -first-use} companion SKILL.md with an {@code <output>} tag,
   * the instructions before the tag are wrapped in {@code <instructions skill="X">} tags on first
   * load, followed by an execution trigger. On subsequent loads, only the execution trigger is
   * emitted. The {@code <output>} section (dynamic preprocessor content) is always wrapped in
   * {@code <output skill="X">} tags and appended regardless of whether it is the first or
   * subsequent load.
   *
   * @param skillName the skill name
   * @return the skill content with environment variables substituted
   * @throws NullPointerException if {@code skillName} is null
   * @throws IllegalArgumentException if {@code skillName} is blank
   * @throws IOException if skill files cannot be read
   */
  public String load(String skillName) throws IOException
  {
    requireThat(skillName, "skillName").isNotBlank();

    String rawContent = loadRawContent(skillName);
    String content = stripFrontmatter(rawContent);
    return processContent(skillName, content);
  }

  /**
   * Processes loaded skill content by applying the first-use/reference logic and variable substitution.
   * <p>
   * Variable substitution and preprocessor directive expansion run first, so that dynamically
   * generated {@code <output>} tags (produced by preprocessor directives) are visible to
   * {@code parseContent()}.
   * <p>
   * For skills with {@code <output>} tags: on first use, wraps instructions in
   * {@code <instructions skill="X">} tags followed by an execution trigger. On subsequent uses,
   * only the execution trigger is emitted. The {@code <output>} section is always appended.
   *
   * @param skillName the skill name
   * @param content the skill content with frontmatter already stripped
   * @return the processed skill output
   * @throws IOException if processing fails
   */
  private String processContent(String skillName, String content) throws IOException
  {
    String expanded = substituteVars(content);
    ParsedContent parsed = parseContent(expanded);

    if (parsed != null)
    {
      StringBuilder output = new StringBuilder(4096);
      String executeRef = "Execute the <instructions skill=\"" + skillName +
        "\"> block from earlier in this conversation, using the updated <output skill=\"" +
        skillName + "\"> tag below.";
      if (loadedSkills.contains(skillName))
      {
        output.append(executeRef);
      }
      else
      {
        output.append("<instructions skill=\"").append(skillName).append("\">\n").
          append(parsed.instructions()).
          append("\n</instructions>\n\n").
          append(executeRef);
        markSkillLoaded(skillName);
      }
      if (!parsed.outputBody().isEmpty())
      {
        output.append("\n\n<output skill=\"").append(skillName).append("\">\n").
          append(parsed.outputBody()).append("\n</output>");
      }
      return output.toString();
    }

    // Content without tags — variable substitution already applied
    StringBuilder output = new StringBuilder(4096);
    if (loadedSkills.contains(skillName))
    {
      output.append("""
        The skill instructions were already loaded earlier in this conversation.
        Use the Skill tool to invoke this skill again with the same arguments.
        The skill script generates fresh, accurate output on every invocation.
        Execute the skill instructions in FULL - do NOT summarize, paraphrase, or abbreviate any output.""");
    }
    else
    {
      output.append(expanded);
      markSkillLoaded(skillName);
    }
    return output.toString();
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
    String dirName = stripPrefix(skillName);
    Path contentPath = pluginRoot.resolve("skills/" + dirName + "/first-use.md");

    // Fall back to parent skill's first-use.md for *-agent variants that have none of their own.
    // Only one level of -agent suffix is stripped: "add-agent" → "add", not "add-agent-agent" → "add".
    if (!Files.exists(contentPath) && dirName.endsWith("-agent"))
    {
      String parentDirName = dirName.substring(0, dirName.length() - "-agent".length());
      contentPath = pluginRoot.resolve("skills/" + parentDirName + "/first-use.md");
    }

    return Files.readString(contentPath, StandardCharsets.UTF_8);
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
  private static String stripPrefix(String qualifiedName)
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
   * {@code -first-use} SKILL.md files which carry frontmatter for Claude Code's own use.
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
   * Replaces inline backtick-quoted segments (e.g., {@code `<output skill="X">`}) with
   * equal-length space sequences, producing a sanitized copy of the content.
   * <p>
   * This prevents {@code OUTPUT_TAG_PATTERN} from matching {@code <output>} tags that appear
   * inside backtick-quoted text in instruction prose.
   * <p>
   * Only inline backticks are replaced. Fenced code blocks (triple-backtick) are not affected
   * because triple-backtick fences do not match the single-backtick pattern used here.
   * <p>
   * Replacements use equal-length space sequences (not shorter tokens) to preserve the string
   * length invariant. This ensures that character positions found by pattern matching on the
   * sanitized copy remain valid indices into the original content.
   *
   * @param content the original content
   * @return a sanitized copy where inline backtick segments are replaced with spaces
   */
  private static String sanitizeInlineBackticks(String content)
  {
    Matcher matcher = INLINE_BACKTICK_PATTERN.matcher(content);
    StringBuilder sanitized = new StringBuilder(content);
    while (matcher.find())
    {
      int start = matcher.start();
      int end = matcher.end();
      for (int i = start; i < end; ++i)
        sanitized.setCharAt(i, ' ');
    }
    return sanitized.toString();
  }

  /**
   * Parses content into instructions and output body using the {@code <output>} tag as delimiter.
   * <p>
   * Everything before the last {@code <output>} tag is treated as instructions. The content inside
   * the last {@code <output>} tag is the preprocessor directive body.
   * <p>
   * Inline backtick-quoted segments (e.g., {@code `<output skill="X">`}) are ignored during
   * tag detection, so literal references in instruction prose do not corrupt the parse.
   * <p>
   * {@code <output>} tags inside fenced code blocks (triple-backtick) are also ignored, so
   * example skill files shown in documentation do not corrupt the parse.
   * <p>
   * <b>Index invariant:</b> {@code sanitizeInlineBackticks} replaces segments with equal-length
   * spaces, preserving string length. This means positions found in the sanitized copy apply
   * directly to the original content for substring extraction.
   *
   * @param content the content to parse
   * @return the parsed content, or {@code null} if no real {@code <output>} tag is found
   */
  private static ParsedContent parseContent(String content)
  {
    String sanitized = sanitizeInlineBackticks(content);
    Set<int[]> codeBlockRegions = findCodeBlockRegions(sanitized);
    Matcher outputMatcher = OUTPUT_TAG_PATTERN.matcher(sanitized);
    int lastStart = -1;
    int lastGroupStart = -1;
    int lastGroupEnd = -1;
    while (outputMatcher.find())
    {
      if (isInsideCodeBlock(outputMatcher.start(), codeBlockRegions))
        continue;
      lastStart = outputMatcher.start();
      lastGroupStart = outputMatcher.start(1);
      lastGroupEnd = outputMatcher.end(1);
    }
    if (lastStart == -1)
      return null;
    String instructions = content.substring(0, lastStart).strip();
    String lastBody = content.substring(lastGroupStart, lastGroupEnd).strip();
    return new ParsedContent(instructions, lastBody);
  }

  /**
   * Expands @path references in content.
   * <p>
   * Lines starting with {@code @} followed by a relative path containing at least one {@code /}
   * (e.g., {@code @concepts/version-paths.md}, {@code @config/settings.yaml}) are replaced with
   * the raw file contents. Any file extension is allowed. Paths are resolved relative to the plugin
   * root. Missing files cause an {@link IOException}.
   *
   * @param content the content to process
   * @return the content with all @path references expanded
   * @throws IOException if a referenced file cannot be read or circular reference is detected
   */
  private String expandPaths(String content) throws IOException
  {
    return expandPaths(content, new HashSet<>());
  }

  /**
   * Expands @path references in content with cycle detection.
   *
   * @param content the content to process
   * @param visitedPaths the set of paths already being expanded (for cycle detection)
   * @return the content with all @path references expanded
   * @throws IOException if a referenced file cannot be read or circular reference is detected
   */
  private String expandPaths(String content, Set<Path> visitedPaths) throws IOException
  {
    Set<int[]> codeBlockRegions = findCodeBlockRegions(content);
    Matcher matcher = PATH_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      if (isInsideCodeBlock(matcher.start(), codeBlockRegions))
      {
        result.append(content, lastEnd, matcher.end());
        lastEnd = matcher.end();
        continue;
      }
      result.append(content, lastEnd, matcher.start());
      String relativePath = matcher.group(1);
      Path filePath = pluginRoot.resolve(relativePath).toAbsolutePath().normalize();
      Path normalizedPluginRoot = pluginRoot.toAbsolutePath().normalize();
      if (!filePath.startsWith(normalizedPluginRoot))
      {
        throw new IOException("@path reference '" + relativePath + "' resolves outside the plugin root. " +
          "Resolved to: " + filePath + ". Plugin root: " + normalizedPluginRoot);
      }
      if (!Files.exists(filePath))
      {
        throw new IOException("@path reference '" + relativePath + "' not found. " +
          "Resolved to: " + filePath);
      }
      if (visitedPaths.contains(filePath))
      {
        throw new IOException("Circular @path reference detected: " + relativePath + ". " +
          "Resolved to: " + filePath);
      }
      visitedPaths.add(filePath);
      String fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
      String expandedContent = expandPaths(fileContent, visitedPaths);
      visitedPaths.remove(filePath);
      result.append(expandedContent);
      if (!expandedContent.endsWith("\n"))
        result.append('\n');
      lastEnd = matcher.end();
    }
    result.append(content.substring(lastEnd));

    return result.toString();
  }

  /**
   * Finds all code block regions (between ``` fences) in the content.
   *
   * @param content the content to scan
   * @return a set of int arrays where each array contains [start, end] positions of a code block
   */
  private static Set<int[]> findCodeBlockRegions(String content)
  {
    Set<int[]> regions = new HashSet<>();
    Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
    while (matcher.find())
      regions.add(new int[]{matcher.start(), matcher.end()});
    return regions;
  }

  /**
   * Returns {@code true} if the given position falls inside any of the provided code block regions.
   *
   * @param position the character position to test
   * @param regions the code block regions to check against
   * @return {@code true} if position is inside a code block, {@code false} otherwise
   */
  private static boolean isInsideCodeBlock(int position, Set<int[]> regions)
  {
    for (int[] region : regions)
    {
      if (position >= region[0] && position < region[1])
        return true;
    }
    return false;
  }

  /**
   * Substitutes variable placeholders in content.
   * <p>
   * Replaces built-in variables:
   * <ul>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}} - plugin root directory path</li>
   *   <li>{@code ${CLAUDE_SESSION_ID}} - current session identifier</li>
   *   <li>{@code ${CLAUDE_PROJECT_DIR}} - project directory path</li>
   * </ul>
   * <p>
   * After variable substitution, processes preprocessor directives to invoke Java classes in-JVM.
   *
   * @param content the content to process
   * @return the content with all variables substituted and preprocessor directives processed
   * @throws IOException if variable resolution fails
   */
  private String substituteVars(String content) throws IOException
  {
    String expanded = expandPaths(content);

    // Pass 1: resolve ${VAR_NAME} built-in variables
    Matcher varMatcher = VAR_PATTERN.matcher(expanded);
    StringBuilder varResult = new StringBuilder();
    int lastEnd = 0;

    while (varMatcher.find())
    {
      varResult.append(expanded, lastEnd, varMatcher.start());
      String varName = varMatcher.group(1);
      String replacement = resolveVariable(varName);
      varResult.append(replacement);
      lastEnd = varMatcher.end();
    }
    varResult.append(expanded.substring(lastEnd));

    // Pass 2: resolve $N positional arguments
    String afterVars = varResult.toString();
    Matcher argMatcher = POSITIONAL_ARG_PATTERN.matcher(afterVars);
    StringBuilder argResult = new StringBuilder();
    lastEnd = 0;

    while (argMatcher.find())
    {
      argResult.append(afterVars, lastEnd, argMatcher.start());
      int index = Integer.parseInt(argMatcher.group(1));
      if (index >= 0 && index < skillArgs.size())
        argResult.append(skillArgs.get(index));
      else
        argResult.append(argMatcher.group(0));
      lastEnd = argMatcher.end();
    }
    argResult.append(afterVars.substring(lastEnd));

    return processPreprocessorDirectives(argResult.toString());
  }

  /**
   * Processes preprocessor directives in content.
   * <p>
   * Scans for patterns like {@code !`"path/to/launcher" [args]`} and when the launcher's filename
   * matches a file in the {@code client/bin/} launcher lookup directory, instantiates the corresponding
   * Java class as a {@link SkillOutput} and calls {@link SkillOutput#getOutput(String[])} to replace
   * the directive with the output.
   *
   * @param content the content to process
   * @return the content with preprocessor directives replaced by their output
   * @throws IOException if directive processing fails
   */
  private String processPreprocessorDirectives(String content) throws IOException
  {
    Matcher matcher = PREPROCESSOR_DIRECTIVE_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      result.append(content, lastEnd, matcher.start());
      String launcherPath = matcher.group(1);
      String argumentsToken = matcher.group(2);
      String[] arguments;
      if (argumentsToken != null)
      {
        List<String> tokenList = ShellParser.tokenize(argumentsToken.strip());
        arguments = tokenList.toArray(new String[0]);
      }
      else
        arguments = new String[0];

      String originalDirective = matcher.group(0);
      String output = executeDirective(launcherPath, arguments, originalDirective);
      result.append(output);
      lastEnd = matcher.end();
    }
    result.append(content.substring(lastEnd));

    return result.toString();
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

    String launcherContent = Files.readString(expectedLauncher, StandardCharsets.UTF_8);
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
      Object instance = targetClass.getConstructor(JvmScope.class).newInstance(scope);
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
      String errorMsg = cause.getMessage();
      if (errorMsg == null)
        errorMsg = cause.getClass().getName();
      return buildPreprocessorErrorMessage(originalDirective, errorMsg);
    }
    catch (Exception e)
    {
      String errorMsg = e.getMessage();
      if (errorMsg == null)
        errorMsg = e.getClass().getName();
      return buildPreprocessorErrorMessage(originalDirective, errorMsg);
    }
  }

  /**
   * Builds a user-friendly error message when a preprocessor directive fails.
   * <p>
   * The message includes the directive that failed, the error details, and instructions for
   * filing a bug report using {@code /cat:feedback}.
   *
   * @param originalDirective the original preprocessor directive text that failed
   * @param errorMsg the error message from the exception
   * @return a user-friendly error message with bug report instructions
   */
  private static String buildPreprocessorErrorMessage(String originalDirective, String errorMsg)
  {
    return """

      ---
      **Preprocessor Error**

      A preprocessor directive failed while loading this skill.

      **Directive:** `%s`
      **Error:** %s

      To report this bug, run: `/cat:feedback`
      ---

      """.formatted(originalDirective, errorMsg);
  }

  /**
   * Resolves a single {@code ${VAR}} variable to its value.
   * <p>
   * Handles built-in variables only. Positional arguments ({@code $0}, {@code $1}, etc.) are
   * resolved separately in {@link #substituteVars(String)}. Unknown variables are passed through
   * as {@code ${varName}} literals, matching Claude Code's native behavior.
   *
   * @param varName the variable name (without ${} delimiters)
   * @return the resolved value, or the original {@code ${varName}} literal if undefined
   */
  private String resolveVariable(String varName)
  {
    return switch (varName)
    {
      case "CLAUDE_PLUGIN_ROOT" -> pluginRoot.toString();
      case "CLAUDE_SESSION_ID" -> scope.getClaudeSessionId();
      case "CLAUDE_PROJECT_DIR" -> projectDir;
      // Pass through unknown variables unchanged (matches Claude Code's native behavior)
      default -> "${" + varName + "}";
    };
  }

  /**
   * Marks a skill as loaded in the agent marker file.
   *
   * @param skillName the skill name
   * @throws IOException if the agent marker file cannot be written
   */
  private void markSkillLoaded(String skillName) throws IOException
  {
    loadedSkills.add(skillName);
    Files.writeString(agentMarkerFile, skillName + "\n", StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND);
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
   * Main method for command-line execution.
   * <p>
   * Invoked as: java -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader
   * skill-name catAgentId [skill-args...]
   * <p>
   * The plugin root and project directory are read from the JVM environment via {@link MainJvmScope}.
   * The agent ID is passed as the first skill argument ({@code skill-args[0]}, i.e., {@code $0}).
   * Blank-agent-ID fallback is handled by the constructor.
   *
   * @param args command-line arguments: skill-name catAgentId [skill-args...]
   */
  public static void main(String[] args)
  {
    if (args.length < 1)
    {
      System.err.println(
        "Usage: skill-loader <skill-name> <catAgentId> [skill-args...]");
      System.exit(1);
    }

    String skillName = args[0];
    List<String> skillArgs = List.of(args).subList(1, args.length);

    try (JvmScope scope = new MainJvmScope())
    {
      SkillLoader loader = new SkillLoader(scope, skillArgs);
      String result = loader.load(skillName);
      System.out.print(result);
    }
    catch (IOException e)
    {
      System.err.println("Error loading skill: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SkillLoader.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}

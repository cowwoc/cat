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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
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
 *     {skill-name}-first-use/
 *       SKILL.md                — Skill content with optional {@code <output>} tag
 * </pre>
 * <p>
 * <b>Tag-based content:</b> The SKILL.md file may contain an optional {@code <output>} tag that separates
 * static instructions from dynamic preprocessor content. Everything before the {@code <output>} tag is
 * treated as skill instructions. On first use, the instructions are output directly. On subsequent uses,
 * a brief reference instruction is generated dynamically. The {@code <output>} section is always wrapped
 * in {@code <output skill="X">} tags (where X is the skill name) and appended on every invocation.
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
 * Undefined variables are passed through unchanged, matching Claude Code's native behavior.
 * <p>
 * <b>Preprocessor directives:</b> Lines containing {@code !`"path" [args]`} patterns are processed
 * after variable substitution. When the path references a known Java launcher in {@code hooks/bin/},
 * instantiates the class as a {@link SkillOutput} and calls {@link SkillOutput#getOutput(String[])}
 * to replace the directive with the output.
 *
 * @see io.github.cowwoc.cat.hooks.session.ClearSkillMarkers
 */
public final class SkillLoader
{
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern PATH_PATTERN = Pattern.compile("^@(.+/.+)$", Pattern.MULTILINE);
  private static final Pattern PREPROCESSOR_DIRECTIVE_PATTERN = Pattern.compile(
    "!`\"([^\"]+)\"(\\s+[^`]+)?`");
  private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
    "\\A---\\n.*?\\n---\\n?", Pattern.DOTALL);
  private static final Pattern OUTPUT_TAG_PATTERN = Pattern.compile(
    "<output>(.*?)</output>", Pattern.DOTALL);

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
  private final String sessionId;
  private final String projectDir;
  private final Path sessionFile;
  private final Set<String> loadedSkills;

  /**
   * Creates a new SkillLoader instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @param pluginRoot the Claude plugin root directory
   * @param sessionId the Claude session ID
   * @param projectDir the Claude project directory (may be empty)
   * @throws NullPointerException if {@code scope}, {@code pluginRoot}, or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code pluginRoot} or {@code sessionId} are blank
   * @throws IOException if the session file cannot be read
   */
  public SkillLoader(JvmScope scope, String pluginRoot, String sessionId, String projectDir) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectDir, "projectDir").isNotNull();

    this.scope = scope;
    this.pluginRoot = Paths.get(pluginRoot);
    this.sessionId = sessionId;
    this.projectDir = projectDir;
    this.sessionFile = Paths.get(System.getProperty("java.io.tmpdir"), "cat-skills-loaded-" + sessionId);
    this.loadedSkills = new HashSet<>();

    if (Files.exists(sessionFile))
    {
      String content = Files.readString(sessionFile, StandardCharsets.UTF_8);
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
   * the instructions before the tag are returned on first load and a brief reference instruction
   * on subsequent loads. The {@code <output>} section (dynamic preprocessor content) is always
   * wrapped in {@code <output skill="X">} tags and appended regardless of whether it is the
   * first or subsequent load.
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

    String content = loadContent(skillName);
    ParsedContent parsed = parseContent(content);

    if (parsed != null)
    {
      // -first-use pattern with <output> tag
      StringBuilder output = new StringBuilder(4096);
      if (loadedSkills.contains(skillName))
      {
        output.append("The `<output skill=\"").append(skillName).
          append("\">` tag below contains updated pre-rendered terminal output. ").
          append("Follow the same instructions from earlier: copy the tag content ").
          append("character-for-character into your response.");
      }
      else
      {
        output.append(substituteVars(parsed.instructions()));
        markSkillLoaded(skillName);
      }
      // Always execute <output> preprocessor (fresh data each invocation)
      if (!parsed.outputBody().isEmpty())
      {
        String processedOutput = substituteVars(parsed.outputBody());
        output.append("\n\n<output skill=\"").append(skillName).append("\">\n").
          append(processedOutput).append("\n</output>");
      }
      return output.toString();
    }

    // Content without tags — apply variable substitution
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
      output.append(substituteVars(content));
      markSkillLoaded(skillName);
    }
    return output.toString();
  }


  /**
   * Loads the skill content from the {@code -first-use} companion SKILL.md.
   * <p>
   * Returns content with YAML frontmatter stripped but without variable substitution (so tags can
   * be parsed first by {@link #load(String)}).
   *
   * @param skillName the skill name
   * @return the content, or empty string if no content file exists
   * @throws IOException if content file cannot be read
   */
  private String loadContent(String skillName) throws IOException
  {
    Path contentPath = pluginRoot.resolve("skills/" + skillName + "-first-use/SKILL.md");
    if (!Files.exists(contentPath))
      return "";
    String content = Files.readString(contentPath, StandardCharsets.UTF_8);
    content = stripFrontmatter(content);
    return content;
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
   * Parses content into instructions and output body using the {@code <output>} tag as delimiter.
   * <p>
   * Everything before the {@code <output>} tag is treated as instructions. The content inside
   * the {@code <output>} tag is the preprocessor directive body.
   *
   * @param content the content to parse
   * @return the parsed content, or {@code null} if no {@code <output>} tag is found
   */
  private static ParsedContent parseContent(String content)
  {
    Matcher outputMatcher = OUTPUT_TAG_PATTERN.matcher(content);
    if (!outputMatcher.find())
      return null;
    String instructions = content.substring(0, outputMatcher.start()).strip();
    String outputBody = outputMatcher.group(1).strip();
    return new ParsedContent(instructions, outputBody);
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
    Matcher matcher = PATH_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      result.append(content, lastEnd, matcher.start());
      String relativePath = matcher.group(1);
      Path filePath = pluginRoot.resolve(relativePath).toAbsolutePath().normalize();
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
    Matcher matcher = VAR_PATTERN.matcher(expanded);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      result.append(expanded, lastEnd, matcher.start());
      String varName = matcher.group(1);
      String replacement = resolveVariable(varName);
      result.append(replacement);
      lastEnd = matcher.end();
    }
    result.append(expanded.substring(lastEnd));

    return processPreprocessorDirectives(result.toString());
  }

  /**
   * Processes preprocessor directives in content.
   * <p>
   * Scans for patterns like {@code !`"path/to/launcher" [args]`} and when the launcher refers to a
   * known Java class in {@code hooks/bin/}, instantiates the class as a {@link SkillOutput} and
   * calls {@link SkillOutput#getOutput(String[])} to replace the directive with the output.
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
        arguments = argumentsToken.strip().split("\\s+");
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
    Path expectedLauncher = pluginRoot.resolve("hooks/bin/" + launcherName);

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
   * Resolves a single variable to its value.
   *
   * @param varName the variable name (without ${} delimiters)
   * @return the resolved value, or the original {@code ${varName}} literal if undefined
   */
  private String resolveVariable(String varName)
  {
    if (varName.equals("CLAUDE_PLUGIN_ROOT"))
      return pluginRoot.toString();
    if (varName.equals("CLAUDE_SESSION_ID"))
      return sessionId;
    if (varName.equals("CLAUDE_PROJECT_DIR"))
      return projectDir;

    // Pass through unknown variables unchanged (matches Claude Code's native behavior)
    return "${" + varName + "}";
  }

  /**
   * Marks a skill as loaded in the session file.
   *
   * @param skillName the skill name
   * @throws IOException if the session file cannot be written
   */
  private void markSkillLoaded(String skillName) throws IOException
  {
    loadedSkills.add(skillName);
    Files.writeString(sessionFile, skillName + "\n", StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Provides CLI entry point to replace the original load-skill script.
   * Invoked as: java -cp hooks.jar io.github.cowwoc.cat.hooks.util.SkillLoader
   * plugin-root skill-name session-id [project-dir]
   *
   * @param args command-line arguments: plugin-root skill-name session-id [project-dir]
   */
  public static void main(String[] args)
  {
    if (args.length < 3 || args.length > 4)
    {
      System.err.println("Usage: load-skill <plugin-root> <skill-name> <session-id> [project-dir]");
      System.exit(1);
    }

    String pluginRoot = args[0];
    String skillName = args[1];
    String sessionId = args[2];
    String projectDir;
    if (args.length == 4)
      projectDir = args[3];
    else
      projectDir = "";

    try (JvmScope scope = new MainJvmScope())
    {
      SkillLoader loader = new SkillLoader(scope, pluginRoot, sessionId, projectDir);
      String result = loader.load(skillName);
      System.out.print(result);
    }
    catch (IOException e)
    {
      System.err.println("Error loading skill: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(SkillLoader.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }
}

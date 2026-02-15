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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * within the same session, loads a shorter reference text instead.
 * <p>
 * <b>Skill directory structure:</b>
 * <pre>
 * plugin-root/
 *   skills/
 *     reference.md              — Reload text returned on 2nd+ invocations
 *     {skill-name}/
 *       first-use.md            — Main skill content (loaded on first invocation)
 * </pre>
 * <p>
 * <b>File inclusion via @path:</b> Lines in {@code first-use.md} starting with {@code @} followed by a
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
 * <p>
 * <b>License header stripping:</b> If {@code first-use.md} starts with an HTML comment block
 * containing a copyright notice, it is stripped before returning.
 */
public final class SkillLoader
{
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern PATH_PATTERN = Pattern.compile("^@(.+/.+)$", Pattern.MULTILINE);
  private static final Pattern LICENSE_HEADER_PATTERN = Pattern.compile(
    "\\A(---\\n.*?\\n---\\n)?<!--\\n.*?Copyright.*?-->\\n?\\n?",
    Pattern.DOTALL);
  private static final Pattern PREPROCESSOR_DIRECTIVE_PATTERN = Pattern.compile(
    "!`\"([^\"]+)\"(\\s+[^`]+)?`");

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
   * Loads a skill, returning full content on first load or reference on subsequent loads.
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

    StringBuilder output = new StringBuilder(4096);

    if (loadedSkills.contains(skillName))
    {
      String reference = loadReference();
      output.append(reference);
    }
    else
    {
      String content = loadContent(skillName);
      output.append(content);
      markSkillLoaded(skillName);
    }

    return output.toString();
  }


  /**
   * Loads the reference text for subsequent skill invocations.
   *
   * @return the reference content with variables substituted, or empty string if no reference
   * @throws IOException if reference file cannot be read
   */
  private String loadReference() throws IOException
  {
    Path referencePath = pluginRoot.resolve("skills/reference.md");
    if (!Files.exists(referencePath))
      return "";

    String reference = Files.readString(referencePath, StandardCharsets.UTF_8);
    reference = stripLicenseHeader(reference);
    return substituteVars(reference);
  }


  /**
   * Loads the main skill content from first-use.md.
   *
   * @param skillName the skill name
   * @return the content with variables substituted, or empty string if no content file
   * @throws IOException if content file cannot be read
   */
  private String loadContent(String skillName) throws IOException
  {
    Path contentPath = pluginRoot.resolve("skills/" + skillName + "/first-use.md");
    if (!Files.exists(contentPath))
      return "";

    String content = Files.readString(contentPath, StandardCharsets.UTF_8);
    content = stripLicenseHeader(content);
    return substituteVars(content);
  }

  /**
   * Strips license header HTML comments from the beginning of markdown content.
   * <p>
   * Removes {@code <!-- ... -->} blocks at the start of content (optionally preceded by YAML
   * frontmatter) that contain a copyright notice. This saves tokens when serving skill content.
   *
   * @param content the content to process
   * @return the content with license header removed, or unchanged if no license header found
   */
  private static String stripLicenseHeader(String content)
  {
    Matcher matcher = LICENSE_HEADER_PATTERN.matcher(content);
    if (!matcher.find())
      return content;
    String frontmatter = matcher.group(1);
    if (frontmatter != null)
      return frontmatter + content.substring(matcher.end());
    return content.substring(matcher.end());
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
      return originalDirective;

    return invokeSkillOutput(className, arguments, originalDirective);
  }

  /**
   * Extracts the fully-qualified class name from a launcher script.
   * <p>
   * Looks for patterns like: {@code java -m module/class}
   *
   * @param launcherContent the launcher script content
   * @return the fully-qualified class name, or empty string if not found
   */
  private String extractClassName(String launcherContent)
  {
    Pattern pattern = Pattern.compile("java.*?-m\\s+(\\S+)/(\\S+)");
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
    catch (Exception e)
    {
      Throwable cause = e;
      if (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null)
        cause = e.getCause();
      String errorMsg = cause.getMessage();
      if (errorMsg == null)
        errorMsg = cause.getClass().getName();
      return "<error>Preprocessor directive failed for \"" + originalDirective + "\": " + errorMsg + "</error>";
    }
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
      java.nio.file.StandardOpenOption.CREATE,
      java.nio.file.StandardOpenOption.APPEND);
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

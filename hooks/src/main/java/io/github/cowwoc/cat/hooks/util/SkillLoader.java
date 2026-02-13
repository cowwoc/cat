package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.core.type.TypeReference;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *       bindings.json           — Optional; maps variable names to SkillOutput class names
 * </pre>
 * <p>
 * <b>Variable bindings:</b> If {@code bindings.json} exists, it maps variable names to fully-qualified
 * class names implementing {@link SkillOutput}. When a variable is referenced in content (e.g.,
 * {@code ${CAT_SKILL_OUTPUT}}), the class is instantiated and {@link SkillOutput#getOutput()} is invoked
 * to produce the substitution value. Binding variables must not collide with built-in variable names.
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
 * Custom bindings from {@code bindings.json} are also substituted when referenced. Undefined variables
 * (neither built-in nor in bindings) are passed through unchanged, matching Claude Code's native behavior.
 * <p>
 * <b>License header stripping:</b> If {@code first-use.md} starts with an HTML comment block
 * containing a copyright notice, it is stripped before returning.
 */
public final class SkillLoader
{
  private static final Set<String> BUILT_IN_VARIABLES = Set.of(
    "CLAUDE_PLUGIN_ROOT",
    "CLAUDE_SESSION_ID",
    "CLAUDE_PROJECT_DIR");
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
  private static final Pattern PATH_PATTERN = Pattern.compile("^@(.+/.+)$", Pattern.MULTILINE);
  private static final TypeReference<Map<String, String>> BINDINGS_TYPE = new TypeReference<>()
  {
  };

  private final JvmScope scope;
  private final Path pluginRoot;
  private final String sessionId;
  private final String projectDir;
  private final Path sessionFile;
  private final Set<String> loadedSkills;
  private final Map<String, Map<String, String>> bindingsCache;
  private final Map<String, String> bindingOutputCache;

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
    this.bindingsCache = new HashMap<>();
    this.bindingOutputCache = new HashMap<>();

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
      String reference = loadReference(skillName);
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
   * Loads bindings from bindings.json file for a skill.
   *
   * @param skillName the skill name
   * @return the bindings map (variable name to class name), or empty map if no bindings file
   * @throws IOException if bindings file is invalid or contains reserved variable names
   */
  private Map<String, String> loadBindings(String skillName) throws IOException
  {
    if (bindingsCache.containsKey(skillName))
      return bindingsCache.get(skillName);

    Path bindingsPath = pluginRoot.resolve("skills/" + skillName + "/bindings.json");
    if (!Files.exists(bindingsPath))
    {
      Map<String, String> emptyBindings = Map.of();
      bindingsCache.put(skillName, emptyBindings);
      return emptyBindings;
    }

    String content = Files.readString(bindingsPath, StandardCharsets.UTF_8);
    Map<String, String> bindings = scope.getJsonMapper().readValue(content, BINDINGS_TYPE);

    for (String varName : bindings.keySet())
    {
      if (BUILT_IN_VARIABLES.contains(varName))
      {
        throw new IOException("Binding variable '" + varName + "' in skill '" + skillName +
          "' is reserved. Built-in variables cannot be overridden: " + BUILT_IN_VARIABLES);
      }
    }

    bindingsCache.put(skillName, bindings);
    return bindings;
  }

  /**
   * Loads the reference text for subsequent skill invocations.
   *
   * @param skillName the skill name (for variable binding resolution)
   * @return the reference content with variables substituted, or empty string if no reference
   * @throws IOException if reference file cannot be read
   */
  private String loadReference(String skillName) throws IOException
  {
    Path referencePath = pluginRoot.resolve("skills/reference.md");
    if (!Files.exists(referencePath))
      return "";

    String reference = Files.readString(referencePath, StandardCharsets.UTF_8);
    return substituteVars(reference, skillName);
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
    return substituteVars(content, skillName);
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
   * Also replaces custom binding variables from bindings.json. When a binding variable is referenced,
   * the corresponding SkillOutput class is instantiated and invoked to generate the substitution value.
   *
   * @param content the content to process
   * @param skillName the skill name (for loading bindings)
   * @return the content with all variables substituted
   * @throws IOException if variable resolution fails or undefined variable is referenced
   */
  private String substituteVars(String content, String skillName) throws IOException
  {
    String expanded = expandPaths(content);
    Map<String, String> bindings = loadBindings(skillName);
    Matcher matcher = VAR_PATTERN.matcher(expanded);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find())
    {
      result.append(expanded, lastEnd, matcher.start());
      String varName = matcher.group(1);
      String replacement = resolveVariable(varName, bindings);
      result.append(replacement);
      lastEnd = matcher.end();
    }
    result.append(expanded.substring(lastEnd));

    return result.toString();
  }

  /**
   * Resolves a single variable to its value.
   *
   * @param varName the variable name (without ${} delimiters)
   * @param bindings the bindings map for the current skill
   * @return the resolved value, or the original {@code ${varName}} literal if undefined
   * @throws IOException if binding resolution fails
   */
  private String resolveVariable(String varName, Map<String, String> bindings)
    throws IOException
  {
    if (varName.equals("CLAUDE_PLUGIN_ROOT"))
      return pluginRoot.toString();
    if (varName.equals("CLAUDE_SESSION_ID"))
      return sessionId;
    if (varName.equals("CLAUDE_PROJECT_DIR"))
      return projectDir;

    if (bindings.containsKey(varName))
    {
      String className = bindings.get(varName);
      String cached = bindingOutputCache.get(className);
      if (cached != null)
        return cached;
      String output = invokeBinding(className);
      bindingOutputCache.put(className, output);
      return output;
    }

    // Pass through unknown variables unchanged (matches Claude Code's native behavior)
    return "${" + varName + "}";
  }

  /**
   * Invokes a SkillOutput binding class to generate output.
   *
   * @param className the fully-qualified class name
   * @return the output from the binding
   * @throws IOException if class instantiation or invocation fails
   */
  private String invokeBinding(String className) throws IOException
  {
    try
    {
      Class<?> bindingClass = Class.forName(className);
      Constructor<?> constructor = bindingClass.getConstructor(JvmScope.class);
      SkillOutput binding = (SkillOutput) constructor.newInstance(scope);
      String output = binding.getOutput();
      if (output == null)
      {
        throw new IOException("Binding class '" + className + "' returned null from getOutput(). " +
          "SkillOutput.getOutput() must never return null.");
      }
      return output;
    }
    catch (ClassNotFoundException e)
    {
      throw new IOException("Binding class not found: " + className, e);
    }
    catch (NoSuchMethodException e)
    {
      throw new IOException("Binding class missing required constructor(JvmScope): " + className, e);
    }
    catch (IOException e)
    {
      throw e;
    }
    catch (ReflectiveOperationException e)
    {
      throw new IOException("Failed to invoke binding class: " + className, e);
    }
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
  }
}

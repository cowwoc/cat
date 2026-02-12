package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

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
 *       content.md              — Main skill content (loaded on first invocation)
 *       includes.txt            — Optional; one relative path per line listing files to include
 *       handler.sh              — Optional; executable script whose stdout is prepended
 * </pre>
 * <p>
 * <b>Included files:</b> Each path in {@code includes.txt} is resolved relative to the plugin root.
 * Included files are wrapped in {@code &lt;include path="..."&gt;...&lt;/include&gt;} XML tags.
 * <p>
 * <b>Variable substitution:</b> The following placeholders are replaced in all loaded content:
 * <ul>
 *   <li>{@code ${CLAUDE_PLUGIN_ROOT}} — plugin root directory path</li>
 *   <li>{@code ${CLAUDE_SESSION_ID}} — current session identifier</li>
 *   <li>{@code ${CLAUDE_PROJECT_DIR}} — project directory path</li>
 * </ul>
 * <p>
 * <b>License header stripping:</b> If {@code content.md} starts with an HTML comment block
 * containing a copyright notice, it is stripped before returning.
 */
public final class SkillLoader
{
  private final Path pluginRoot;
  private final String sessionId;
  private final String projectDir;
  private final Path sessionFile;
  private final Set<String> loadedSkills;

  /**
   * Creates a new SkillLoader instance.
   *
   * @param pluginRoot the Claude plugin root directory
   * @param sessionId the Claude session ID
   * @param projectDir the Claude project directory (may be empty)
   * @throws NullPointerException if {@code pluginRoot} or {@code sessionId} are null
   * @throws IllegalArgumentException if {@code pluginRoot} or {@code sessionId} are blank
   * @throws IOException if the session file cannot be read
   */
  public SkillLoader(String pluginRoot, String sessionId, String projectDir) throws IOException
  {
    requireThat(pluginRoot, "pluginRoot").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(projectDir, "projectDir").isNotNull();

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

    Path handlerPath = pluginRoot.resolve("skills/" + skillName + "/handler.sh");
    if (Files.exists(handlerPath) && Files.isExecutable(handlerPath))
    {
      String handlerOutput = executeHandler(handlerPath);
      output.append(substituteVars(handlerOutput));
    }

    if (loadedSkills.contains(skillName))
    {
      Path referencePath = pluginRoot.resolve("skills/reference.md");
      if (Files.exists(referencePath))
      {
        String reference = Files.readString(referencePath, StandardCharsets.UTF_8);
        output.append(substituteVars(reference));
      }
    }
    else
    {
      Path includesPath = pluginRoot.resolve("skills/" + skillName + "/includes.txt");
      if (Files.exists(includesPath))
      {
        String includesList = Files.readString(includesPath, StandardCharsets.UTF_8);
        for (String line : includesList.split("\n"))
        {
          String trimmed = line.strip();
          if (!trimmed.isEmpty())
          {
            Path includeFile = pluginRoot.resolve(trimmed);
            if (Files.exists(includeFile))
            {
              output.append("<include path=\"").append(trimmed).append("\">\n");
              String includeContent = Files.readString(includeFile, StandardCharsets.UTF_8);
              output.append(substituteVars(includeContent));
              if (!includeContent.endsWith("\n"))
                output.append('\n');
              output.append("</include>\n");
            }
          }
        }
      }

      Path contentPath = pluginRoot.resolve("skills/" + skillName + "/content.md");
      if (Files.exists(contentPath))
      {
        String content = Files.readString(contentPath, StandardCharsets.UTF_8);
        output.append(substituteVars(content));
      }

      markSkillLoaded(skillName);
    }

    return output.toString();
  }

  /**
   * Executes a skill handler script and returns its output.
   *
   * @param handlerPath the path to the handler script
   * @return the handler output
   * @throws IOException if the handler execution fails
   */
  private String executeHandler(Path handlerPath) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder(handlerPath.toString());
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try
    {
      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("Handler script failed with exit code " + exitCode + ": " + handlerPath);
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for handler: " + handlerPath, e);
    }
    return output;
  }

  /**
   * Substitutes environment variable placeholders in content.
   * <p>
   * Replaces these placeholders with their configured values:
   * <ul>
   *   <li>{@code ${CLAUDE_PLUGIN_ROOT}} - plugin root directory path</li>
   *   <li>{@code ${CLAUDE_SESSION_ID}} - current session identifier</li>
   *   <li>{@code ${CLAUDE_PROJECT_DIR}} - project directory path</li>
   * </ul>
   *
   * @param content the content to process
   * @return the content with all recognized placeholders replaced
   */
  private String substituteVars(String content)
  {
    String result = content;
    result = result.replace("${CLAUDE_PLUGIN_ROOT}", pluginRoot.toString());
    result = result.replace("${CLAUDE_SESSION_ID}", sessionId);
    result = result.replace("${CLAUDE_PROJECT_DIR}", projectDir);
    return result;
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
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
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

    SkillLoader loader = new SkillLoader(pluginRoot, sessionId, projectDir);
    try
    {
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

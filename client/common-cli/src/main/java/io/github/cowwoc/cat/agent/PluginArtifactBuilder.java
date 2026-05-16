/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds flattened runtime-specific plugin artifacts.
 * <p>
 * The source tree keeps runtime-neutral and runtime-specific instruction files separate. Release artifacts
 * expose only the content relevant to one runtime, inline always-loaded shared instruction fragments, and
 * strip source license headers from files that are injected into agent context.
 */
public final class PluginArtifactBuilder
{
  private static final Pattern FILE_REFERENCE = Pattern.compile(
    "(?<![A-Za-z0-9_./-])([A-Za-z0-9][A-Za-z0-9_.-]*\\.[A-Za-z0-9][A-Za-z0-9_.-]*)(?![A-Za-z0-9_./-])");
  private static final Pattern RENDER_OUTPUT_DIRECTIVE =
    Pattern.compile("(?m)^\\s*<!--\\s*cat:render-output\\b(.*?)\\s*-->\\s*$");
  private static final Pattern RENDER_OUTPUT_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
  private static final Pattern RENDER_OUTPUT_PLACEHOLDER = Pattern.compile("<[A-Za-z0-9][A-Za-z0-9_.-]*>");
  private static final Pattern MARKDOWN_LICENSE_HEADER = Pattern.compile(
    "\\A(?<frontmatter>---\\R.*?\\R---\\R)?" +
      "<!--\\R" +
      "Copyright \\(c\\) 2026 Gili Tzabari\\. All rights reserved\\.\\R" +
      "Licensed under the CAT Commercial License\\.\\R" +
      "See LICENSE\\.md in the project root for license terms\\.\\R" +
      "-->\\R",
    Pattern.DOTALL);
  private static final Pattern HASH_LICENSE_HEADER = Pattern.compile(
    "\\A" +
      "# Copyright \\(c\\) 2026 Gili Tzabari\\. All rights reserved\\.\\R" +
      "#\\R" +
      "# Licensed under the CAT Commercial License\\.\\R" +
      "# See LICENSE\\.md in the project root for license terms\\.\\R");
  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final Path pluginDir;
  private final Path clientDir;
  private final Path cliJlinkRoot;
  private final Path targetDir;

  /**
   * Creates a new builder.
   *
   * @param pluginDir the plugin source directory
   * @param clientDir the Maven parent client directory
   * @param targetDir the output directory for flattened runtime artifacts
   * @throws NullPointerException if any argument is null
   */
  public PluginArtifactBuilder(Path pluginDir, Path clientDir, Path targetDir)
  {
    this.pluginDir = pluginDir.toAbsolutePath().normalize();
    this.clientDir = clientDir.toAbsolutePath().normalize();
    this.cliJlinkRoot = this.clientDir.resolve("distribution/target/jlink");
    this.targetDir = targetDir.toAbsolutePath().normalize();
  }

  /**
   * Builds all runtime artifacts.
   *
   * @throws IOException if file operations fail
   */
  public void build() throws IOException
  {
    if (Files.isDirectory(targetDir, LinkOption.NOFOLLOW_LINKS))
      deleteDirectory(targetDir);
    buildRuntime(Runtime.CLAUDE);
    buildRuntime(Runtime.CODEX);
    System.out.println("Built runtime plugin artifacts:");
    System.out.println("  " + targetDir.resolve(Runtime.CLAUDE.directoryName));
    System.out.println("  " + targetDir.resolve(Runtime.CODEX.directoryName));
  }

  /**
   * Builds one runtime-specific plugin artifact.
   *
   * @param runtime the runtime to build
   * @throws IOException if file operations fail
   */
  private void buildRuntime(Runtime runtime) throws IOException
  {
    Path target = targetDir.resolve(runtime.directoryName);
    Files.createDirectories(target);

    copyCommonPluginFiles(runtime, target);
    copyTree(pluginDir.resolve(runtime.manifestDirectory), target.resolve(runtime.manifestDirectory));

    Files.createDirectories(target.resolve("rules"));
    copyTree(pluginDir.resolve("rules/common"), target.resolve("rules/common"));
    copyTree(pluginDir.resolve("rules").resolve(runtime.directoryName),
      target.resolve("rules").resolve(runtime.directoryName));

    Files.createDirectories(target.resolve("hooks"));
    copyTree(pluginDir.resolve("hooks/common"), target.resolve("hooks/common"));
    copyTree(pluginDir.resolve("hooks").resolve(runtime.directoryName),
      target.resolve("hooks").resolve(runtime.directoryName));
    copyFile(pluginDir.resolve("hooks").resolve(runtime.directoryName).resolve("hooks.json"),
      target.resolve("hooks/hooks.json"));

    Files.createDirectories(target.resolve("skills"));
    copySkillSet(runtime, pluginDir.resolve("skills/common"), target.resolve("skills"));
    copyRuntimeSkillSet(runtime, pluginDir.resolve("skills").resolve(runtime.directoryName), target.resolve("skills"));

    Files.createDirectories(target.resolve("agents"));
    if (runtime == Runtime.CLAUDE)
      copyInstructionTree(runtime, pluginDir.resolve("agents/claude"), target.resolve("agents"));
    else
      copyInstructionTree(runtime, pluginDir.resolve("agents/codex"), target.resolve("agents"));
    deleteNamedFiles(target.resolve("agents"), "README.md");

    stripAndVerifyAgentFacingFiles(target);
    writeRuntimeVersion(target, runtime.manifestDirectory);
    makeShellScriptsExecutable(target);
    verifyRuntimeArtifact(target);
  }

  /**
   * Copies plugin files that are shared by all runtime artifacts.
   *
   * @param runtime the runtime being built
   * @param target the root directory of the runtime artifact currently being assembled
   * @throws IOException if file operations fail
   */
  private void copyCommonPluginFiles(Runtime runtime, Path target) throws IOException
  {
    copyTree(pluginDir.resolve(".git-filter-repo-config"), target.resolve(".git-filter-repo-config"));
    copyTree(pluginDir.resolve("concepts"), target.resolve("concepts"));
    copyTree(pluginDir.resolve("config"), target.resolve("config"));
    copyTree(pluginDir.resolve("lang"), target.resolve("lang"));
    copyTree(pluginDir.resolve("migrations"), target.resolve("migrations"));
    copyTree(pluginDir.resolve("scripts"), target.resolve("scripts"));
    copyTree(pluginDir.resolve("templates"), target.resolve("templates"));

    copyFile(pluginDir.resolve("emoji-widths.json"), target.resolve("emoji-widths.json"));
    copyFile(pluginDir.resolve("package.json"), target.resolve("package.json"));
    copyFile(pluginDir.resolve("package-lock.json"), target.resolve("package-lock.json"));
    copyFile(clientDir.getParent().resolve("LICENSE.md"), target.resolve("LICENSE.md"));

    Path runtimeJlinkDir = cliJlinkRoot.resolve(runtime.directoryName);
    if (Files.isDirectory(runtimeJlinkDir, LinkOption.NOFOLLOW_LINKS))
      copyTree(runtimeJlinkDir, target.resolve("client"));
  }

  /**
   * Copies all runtime-visible skills from a skill source directory.
   *
   * @param runtime the runtime being built
   * @param source the source skill root
   * @param target the target skill root
   * @throws IOException if file operations fail
   */
  private void copySkillSet(Runtime runtime, Path source, Path target) throws IOException
  {
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
      return;
    try (Stream<Path> stream = Files.list(source))
    {
      List<Path> skillDirectories = stream.
        filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).
        filter(path -> Files.isRegularFile(path.resolve("SKILL.md"), LinkOption.NOFOLLOW_LINKS)).
        sorted(Comparator.comparing(path -> path.getFileName().toString())).
        toList();
      for (Path skillDirectory : skillDirectories)
      {
        Path targetSkill = target.resolve(skillDirectory.getFileName());
        if (Files.exists(targetSkill, LinkOption.NOFOLLOW_LINKS))
          deleteDirectory(targetSkill);
        copyRuntimeSkillTree(runtime, skillDirectory, targetSkill);
      }
    }
  }

  /**
   * Copies runtime-specific skill directories and overlays shared skill support files when present.
   *
   * @param runtime the runtime being built
   * @param source the runtime-specific skill root
   * @param target the target skill root
   * @throws IOException if file operations fail
   */
  private void copyRuntimeSkillSet(Runtime runtime, Path source, Path target) throws IOException
  {
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
      return;
    try (Stream<Path> stream = Files.list(source))
    {
      List<Path> skillDirectories = stream.
        filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).
        filter(path -> Files.isRegularFile(path.resolve("SKILL.md"), LinkOption.NOFOLLOW_LINKS)).
        sorted(Comparator.comparing(path -> path.getFileName().toString())).
        toList();
      for (Path skillDirectory : skillDirectories)
      {
        Path targetSkill = target.resolve(skillDirectory.getFileName());
        if (Files.exists(targetSkill, LinkOption.NOFOLLOW_LINKS))
          deleteDirectory(targetSkill);
        Path commonSkill = pluginDir.resolve("skills/common").resolve(skillDirectory.getFileName());
        if (Files.isDirectory(commonSkill, LinkOption.NOFOLLOW_LINKS))
          copyRuntimeSkillTree(runtime, commonSkill, targetSkill);
        copyRuntimeSkillTree(runtime, skillDirectory, targetSkill);
      }
    }
  }

  /**
   * Checks whether an include target may be expanded for the runtime artifact.
   *
   * @param runtime the runtime being built
   * @param path the candidate include target
   * @return true if the target is in a runtime-visible source tree
   */
  private boolean isAllowedIncludeTarget(Runtime runtime, Path path)
  {
    if (!path.startsWith(pluginDir) || isSourceOnlyPath(pluginDir.relativize(path)))
      return false;
    List<Path> allowedRoots = List.of(
      pluginDir.resolve("agents/common"),
      pluginDir.resolve("skills/common"),
      pluginDir.resolve("skills/include"),
      pluginDir.resolve("rules/common"),
      pluginDir.resolve("hooks/common"),
      pluginDir.resolve("concepts"),
      pluginDir.resolve("agents").resolve(runtime.directoryName),
      pluginDir.resolve("skills").resolve(runtime.directoryName),
      pluginDir.resolve("rules").resolve(runtime.directoryName),
      pluginDir.resolve("hooks").resolve(runtime.directoryName));
    for (Path allowedRoot : allowedRoots)
    {
      if (path.startsWith(allowedRoot.toAbsolutePath().normalize()))
        return true;
    }
    return false;
  }

  /**
   * Removes source license headers from generated agent-facing text files and verifies that source-only
   * markers are gone.
   *
   * @param target the runtime artifact root
   * @throws IOException if file operations fail
   */
  private void stripAndVerifyAgentFacingFiles(Path target) throws IOException
  {
    for (Path directory : List.of(target.resolve("agents"), target.resolve("commands"), target.resolve("concepts"),
      target.resolve("rules"), target.resolve("skills")))
    {
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        continue;
      Files.walkFileTree(directory, new SimpleFileVisitor<>()
      {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
        {
          if (isMarkdownOrToml(file))
          {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (file.getFileName().toString().endsWith(".md"))
              text = stripMarkdownLicenseHeader(text);
            else
              text = HASH_LICENSE_HEADER.matcher(text).replaceFirst("");
            FileSystemUtils.writeStringIfChanged(file, text);
            if (containsSourceLicenseText(text))
              throw new IllegalStateException("Runtime artifact contains source license text: " + file);
            if (text.contains("cat:include"))
              throw new IllegalStateException("Runtime artifact contains unresolved cat:include marker: " + file);
            if (text.contains("cat:render-output"))
              throw new IllegalStateException("Runtime artifact contains unresolved cat:render-output marker: " + file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  /**
   * Removes the standard source license header from Markdown content.
   *
   * @param text the source text
   * @return the text without the license header
   */
  private String stripMarkdownLicenseHeader(String text)
  {
    Matcher matcher = MARKDOWN_LICENSE_HEADER.matcher(text);
    if (!matcher.find())
      return text;
    String frontmatter = matcher.group("frontmatter");
    if (frontmatter == null)
      return matcher.replaceFirst("");
    return frontmatter + text.substring(matcher.end());
  }

  /**
   * Removes source license headers from Markdown or TOML content.
   *
   * @param text the source text
   * @return the text without source license headers
   */
  private String stripSourceLicenseHeader(String text)
  {
    return HASH_LICENSE_HEADER.matcher(stripMarkdownLicenseHeader(text)).replaceFirst("");
  }

  /**
   * Writes the runtime artifact version file from its plugin manifest.
   *
   * @param target the runtime artifact root
   * @param manifestDirectory the runtime manifest directory
   * @throws IOException if file operations fail
   */
  private void writeRuntimeVersion(Path target, String manifestDirectory) throws IOException
  {
    Path manifest = target.resolve(manifestDirectory).resolve("plugin.json");
    Path versionFile = target.resolve("client/VERSION");
    if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) ||
      !Files.isDirectory(versionFile.getParent(), LinkOption.NOFOLLOW_LINKS))
    {
      return;
    }
    JsonNode json = jsonMapper.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    JsonNode version = json.get("version");
    if (version == null || !version.isString())
      throw new IllegalStateException("Missing string version in " + manifest);
    Files.writeString(versionFile, version.stringValue() + "\n", StandardCharsets.UTF_8);
  }

  /**
   * Deletes all files with the requested name below a root directory.
   *
   * @param root the directory to search
   * @param fileName the file name to delete
   * @throws IOException if file operations fail
   */
  private void deleteNamedFiles(Path root, String fileName) throws IOException
  {
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      return;
    try (Stream<Path> stream = Files.walk(root))
    {
      List<Path> files = stream.
        filter(path -> path.getFileName().toString().equals(fileName)).
        sorted(Comparator.reverseOrder()).
        toList();
      for (Path file : files)
        Files.deleteIfExists(file);
    }
  }

  /**
   * Marks shell scripts in the runtime artifact as executable.
   *
   * @param root the directory to scan
   * @throws IOException if file operations fail
   */
  private void makeShellScriptsExecutable(Path root) throws IOException
  {
    Files.walkFileTree(root, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        if (file.getFileName().toString().endsWith(".sh"))
          ExecutableFiles.makeExecutable(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Verifies that the runtime artifact does not expose source-only content.
   *
   * @param target the runtime artifact root
   * @throws IOException if file operations fail
   */
  private void verifyRuntimeArtifact(Path target) throws IOException
  {
    Path commonAgents = target.resolve("agents/common");
    if (Files.exists(commonAgents, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalStateException("Runtime artifact must not contain common agent sources: " + commonAgents);
    verifyNoSourceOnlySkillFiles(target.resolve("skills"));
  }

  /**
   * Verifies that source-only skill test files were excluded from the runtime artifact.
   *
   * @param skillsRoot the runtime skill root
   * @throws IOException if file operations fail
   */
  private void verifyNoSourceOnlySkillFiles(Path skillsRoot) throws IOException
  {
    if (!Files.isDirectory(skillsRoot, LinkOption.NOFOLLOW_LINKS))
      return;
    try (Stream<Path> stream = Files.walk(skillsRoot))
    {
      List<Path> invalidFiles = stream.
        filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).
        filter(path -> isSourceOnlyPath(skillsRoot.relativize(path))).
        sorted().
        toList();
      if (!invalidFiles.isEmpty())
        throw new IllegalStateException("Runtime artifact contains source-only skill files: " + invalidFiles);
    }
  }

  /**
   * Checks whether text still contains source license header fragments.
   *
   * @param text the text to inspect
   * @return true if source license text remains
   */
  private boolean containsSourceLicenseText(String text)
  {
    return text.contains("Copyright (c) 2026 Gili Tzabari. All rights reserved.") ||
      text.contains("Licensed under the CAT Commercial License.") ||
      text.contains("See LICENSE.md in the project root for license terms.");
  }

  /**
   * Copies a directory tree if the source exists.
   *
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if file operations fail
   */
  private void copyTree(Path source, Path target) throws IOException
  {
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
      return;
    Files.walkFileTree(source, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(dir);
        Files.createDirectories(target.resolve(relative));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(file);
        copyFileSystemEntry(file, target.resolve(relative));
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Copies instruction files while expanding runtime-allowed includes.
   *
   * @param runtime the runtime being built
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if file operations fail
   */
  private void copyInstructionTree(Runtime runtime, Path source, Path target) throws IOException
  {
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
      return;
    Files.walkFileTree(source, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(dir);
        Files.createDirectories(target.resolve(relative));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(file);
        Path targetFile = target.resolve(relative);
        if (isMarkdownOrToml(file))
          copyInstructionFile(runtime, file, targetFile);
        else
          copyFileSystemEntry(file, targetFile);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Copies runtime-visible skill files and their referenced companion files.
   *
   * @param runtime the runtime being built
   * @param source the source skill directory
   * @param target the target skill directory
   * @throws IOException if file operations fail
   */
  private void copyRuntimeSkillTree(Runtime runtime, Path source, Path target) throws IOException
  {
    Set<Path> runtimeFiles = getRuntimeSkillFiles(source);
    Files.walkFileTree(source, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(dir);
        if (isSourceOnlyPath(relative))
          return FileVisitResult.SKIP_SUBTREE;
        Files.createDirectories(target.resolve(relative));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        Path relative = source.relativize(file);
        if (runtimeFiles.contains(relative))
        {
          Path targetFile = target.resolve(relative);
          if (isMarkdownOrToml(file))
            copyInstructionFile(runtime, file, targetFile);
          else
            copyFileSystemEntry(file, targetFile);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Copies an instruction file after removing source headers and expanding includes.
   *
   * @param runtime the runtime being built
   * @param source the source instruction file
   * @param target the target instruction file
   * @throws IOException if file operations fail
   */
  private void copyInstructionFile(Runtime runtime, Path source, Path target) throws IOException
  {
    Files.createDirectories(target.getParent());
    String text = stripSourceLicenseHeader(Files.readString(source, StandardCharsets.UTF_8));
    text = SourceIncludeProcessor.expand(source, text, path -> isAllowedIncludeTarget(runtime, path),
      this::stripSourceLicenseHeader);
    text = replaceRuntimePlaceholders(runtime, text);
    FileSystemUtils.writeStringIfChanged(target, text);
  }

  /**
   * Replaces runtime-specific placeholders in generated instruction text.
   *
   * @param runtime the runtime being built
   * @param text    the source text
   * @return text with runtime placeholders resolved
   */
  private String replaceRuntimePlaceholders(Runtime runtime, String text)
  {
    return replaceRenderOutputDirectives(runtime, text).
      replace("${CAT_COMMAND_PREFIX}", commandPrefix(runtime)).
      replace("${CAT_CONFIG_SETTINGS_RENDER_STEP}", configSettingsRenderStep(runtime));
  }

  /**
   * Replaces source-only output rendering directives with runtime-specific instructions.
   *
   * @param runtime the runtime being built
   * @param text    the source text
   * @return text with output rendering directives resolved
   */
  private String replaceRenderOutputDirectives(Runtime runtime, String text)
  {
    Matcher matcher = RENDER_OUTPUT_DIRECTIVE.matcher(text);
    return matcher.replaceAll(result -> Matcher.quoteReplacement(renderOutput(runtime, result.group(1))));
  }

  /**
   * Returns runtime-specific instructions for invoking a deterministic output command.
   *
   * @param runtime the runtime being built
   * @param rawArguments directive arguments
   * @return runtime-specific output rendering instructions
   */
  private String renderOutput(Runtime runtime, String rawArguments)
  {
    List<String> tokens = Stream.of(rawArguments.strip().split("\\s+")).
      filter(token -> !token.isEmpty()).
      toList();
    if (tokens.isEmpty())
      throw new IllegalStateException("cat:render-output command must not be blank");
    for (String token : tokens)
      validateRenderOutputToken(token);
    if (isPlaceholder(tokens.getFirst()))
      throw new IllegalStateException("cat:render-output command must not be a placeholder: " + tokens.getFirst());
    return switch (runtime)
    {
      case CLAUDE -> renderClaudeOutput(tokens);
      case CODEX -> renderCodexOutput(tokens);
    };
  }

  /**
   * Returns Claude-specific preprocessor output rendering instructions.
   *
   * @param tokens command tokens
   * @return Claude-specific output rendering instructions
   */
  private String renderClaudeOutput(List<String> tokens)
  {
    StringBuilder command = new StringBuilder(256);
    command.append(renderOutputPurpose()).append("\n\n").
      append("!`: \"${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}\"; " +
        "if [ -z \"${CAT_PLUGIN_DATA:-}\" ]; then echo \"CAT_PLUGIN_DATA is required\" >&2; exit 1; fi; " +
        "\"${CAT_PLUGIN_ROOT}/client/bin/").append(tokens.getFirst()).append('"');
    int argumentIndex = 1;
    for (String token : tokens.subList(1, tokens.size()))
    {
      command.append(' ');
      if (isPlaceholder(token))
      {
        command.append("\"$").append(argumentIndex).append('"');
        ++argumentIndex;
      }
      else
        command.append('"').append(token).append('"');
    }
    return command.append('`').toString();
  }

  /**
   * Returns Codex-specific Bash output rendering instructions.
   *
   * @param tokens command tokens
   * @return Codex-specific output rendering instructions
   */
  private String renderCodexOutput(List<String> tokens)
  {
    StringBuilder output = new StringBuilder(512);
    output.append(renderOutputPurpose()).append("\n\n").
      append("Run the deterministic implementation through Bash");
    List<String> placeholders = tokens.stream().
      filter(PluginArtifactBuilder::isPlaceholder).
      toList();
    if (!placeholders.isEmpty())
    {
      output.append(", replacing ");
      for (int i = 0; i < placeholders.size(); ++i)
      {
        if (i > 0)
        {
          if (i == placeholders.size() - 1)
            output.append(" and ");
          else
            output.append(", ");
        }
        output.append('`').append(placeholders.get(i)).append('`');
      }
      output.append(" with the skill arguments");
    }
    output.append(":\n\n```bash\nif [ -z \"${CAT_PLUGIN_DATA:-}\" ]; then\n").
      append("  echo \"CAT_PLUGIN_DATA is required\" >&2\n").
      append("  exit 1\n").
      append("fi\n\"${CAT_PLUGIN_ROOT}/client/bin/").
      append(tokens.getFirst()).append('"');
    for (String token : tokens.subList(1, tokens.size()))
      output.append(' ').append('"').append(token).append('"');
    return output.append("\n```").toString();
  }

  /**
   * Returns the common instruction text for deterministic output rendering.
   *
   * @return the common output rendering purpose
   */
  private static String renderOutputPurpose()
  {
    return "Render the display with the deterministic Java output command. Return the generated display exactly.";
  }

  /**
   * Indicates whether a token is an argument placeholder.
   *
   * @param token the token
   * @return true if the token is a placeholder
   */
  private static boolean isPlaceholder(String token)
  {
    return RENDER_OUTPUT_PLACEHOLDER.matcher(token).matches();
  }

  /**
   * Validates a directive token before generating shell instructions.
   *
   * @param token the token to validate
   * @throws IllegalStateException if the token is invalid
   */
  private static void validateRenderOutputToken(String token)
  {
    if (RENDER_OUTPUT_TOKEN.matcher(token).matches() || isPlaceholder(token))
      return;
    throw new IllegalStateException("Invalid cat:render-output token: " + token);
  }

  /**
   * Returns the command prefix used to invoke CAT skills in a runtime.
   *
   * @param runtime the runtime being built
   * @return the command prefix
   */
  private String commandPrefix(Runtime runtime)
  {
    return switch (runtime)
    {
      case CLAUDE -> "/";
      case CODEX -> "$";
    };
  }

  /**
   * Returns the runtime-specific instruction for rendering the initial config settings box.
   *
   * @param runtime the runtime being built
   * @return the runtime-specific instruction text
   */
  private String configSettingsRenderStep(Runtime runtime)
  {
    return switch (runtime)
    {
      case CLAUDE -> """
        The rendered settings box is injected below by Claude's silent preprocessor. Output only the complete inner
        content of the last `<output type="config.settings">` tag exactly as-is before prompting.

        """ + "!`: \"${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}\"; " +
        "\"${CAT_PLUGIN_ROOT}/client/bin/get-output\" \"$0\" config.settings`";
      case CODEX -> "INVOKE: Skill(\"cat:get-output\", args=\"config.settings\")";
    };
  }

  /**
   * Finds skill files that should be included in a runtime artifact.
   *
   * @param source the source skill directory
   * @return relative paths to runtime-visible skill files
   * @throws IOException if file operations fail
   */
  private Set<Path> getRuntimeSkillFiles(Path source) throws IOException
  {
    Map<String, Path> candidateByFileName = new HashMap<>();
    Set<Path> included = new LinkedHashSet<>();
    try (Stream<Path> stream = Files.walk(source))
    {
      List<Path> candidates = stream.
        filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).
        map(source::relativize).
        filter(relative -> !isSourceOnlyPath(relative)).
        sorted().
        toList();
      for (Path candidate : candidates)
      {
        addCandidate(candidateByFileName, candidate);
        String fileName = candidate.getFileName().toString();
        if (fileName.equals("SKILL.md") || fileName.equals("first-use.md"))
          included.add(candidate);
      }
    }

    Queue<Path> pending = new ArrayDeque<>(included);
    while (!pending.isEmpty())
    {
      Path current = pending.remove();
      if (!isMarkdownOrToml(current))
        continue;
      String text = Files.readString(source.resolve(current), StandardCharsets.UTF_8);
      Matcher matcher = FILE_REFERENCE.matcher(text);
      while (matcher.find())
      {
        Path referenced = candidateByFileName.get(matcher.group(1));
        if (referenced != null && included.add(referenced))
        {
          pending.add(referenced);
        }
      }
    }
    return included;
  }

  /**
   * Adds a runtime skill companion-file candidate by file name.
   *
   * @param candidateByFileName candidate paths keyed by file name
   * @param candidate the candidate path to add
   */
  private void addCandidate(Map<String, Path> candidateByFileName, Path candidate)
  {
    String fileName = candidate.getFileName().toString();
    Path previous = candidateByFileName.putIfAbsent(fileName, candidate);
    if (previous != null)
    {
      throw new IllegalStateException("Duplicate runtime skill companion filename '" + fileName +
        "' in " + previous + " and " + candidate);
    }
  }

  /**
   * Checks whether a path is authoring-only and should be excluded from runtime artifacts.
   *
   * @param relative the path relative to the scanned root
   * @return true if the path is source-only
   */
  private boolean isSourceOnlyPath(Path relative)
  {
    for (Path part : relative)
    {
      String name = part.toString();
      if (name.equals("tests") || name.equals("instruction-test"))
        return true;
    }
    Path fileNamePath = relative.getFileName();
    if (fileNamePath == null)
      return false;
    String fileName = fileNamePath.toString();
    return fileName.endsWith(".bats");
  }

  /**
   * Copies a regular file if the source exists.
   *
   * @param source the source file
   * @param target the target file
   * @throws IOException if file operations fail
   */
  private void copyFile(Path source, Path target) throws IOException
  {
    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
      return;
    Files.createDirectories(target.getParent());
    copyFileSystemEntry(source, target);
  }

  /**
   * Copies a file-system entry, resolving jlink symlinks into regular files.
   *
   * @param source the source entry
   * @param target the target entry
   * @throws IOException if file operations fail
   */
  private void copyFileSystemEntry(Path source, Path target) throws IOException
  {
    if (Files.isSymbolicLink(source))
    {
      copyJlinkSymlinkTarget(source, target);
      return;
    }
    Files.createDirectories(target.getParent());
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
      Files.delete(target);
    Files.copy(source, target);
  }

  /**
   * Copies the safe target of a jlink symlink into the runtime artifact.
   *
   * @param source the symlink source
   * @param target the target file
   * @throws IOException if the link target is unsafe or file operations fail
   */
  private void copyJlinkSymlinkTarget(Path source, Path target) throws IOException
  {
    if (!source.startsWith(cliJlinkRoot))
      throw new IOException("Refusing to copy symbolic link into plugin artifact: " + source);

    Path linkTarget = Files.readSymbolicLink(source);
    Path resolvedTarget;
    if (linkTarget.isAbsolute())
      resolvedTarget = linkTarget.normalize();
    else
      resolvedTarget = source.getParent().resolve(linkTarget).normalize();
    if (!resolvedTarget.startsWith(cliJlinkRoot) ||
      !Files.isRegularFile(resolvedTarget, LinkOption.NOFOLLOW_LINKS))
    {
      throw new IOException("Refusing to copy unsafe jlink symbolic link into plugin artifact: " +
        source + " -> " + linkTarget);
    }

    Files.createDirectories(target.getParent());
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
      Files.delete(target);
    Files.copy(resolvedTarget, target);
  }

  /**
   * Deletes a directory tree.
   *
   * @param directory the directory to delete
   * @throws IOException if file operations fail
   */
  private void deleteDirectory(Path directory) throws IOException
  {
    try (Stream<Path> walk = Files.walk(directory))
    {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path path : paths)
      {
        path.toFile().setWritable(true, false);
        Files.deleteIfExists(path);
      }
    }
  }

  /**
   * Checks whether a path points to a Markdown or TOML file.
   *
   * @param path the path to inspect
   * @return true if the file extension is Markdown or TOML
   */
  private boolean isMarkdownOrToml(Path path)
  {
    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return fileName.endsWith(".md") || fileName.endsWith(".toml");
  }

  /**
   * Entry point.
   *
   * @param args {@code <plugin-dir> <client-dir> <target-dir>}
   * @throws IOException if file operations fail
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length != 3)
      throw new IllegalArgumentException(
        "Usage: build-runtime-artifacts <plugin-dir> <client-dir> <target-dir>");
    new PluginArtifactBuilder(Path.of(args[0]), Path.of(args[1]), Path.of(args[2])).build();
  }

  private enum Runtime
  {
    CLAUDE("claude", ".claude-plugin"),
    CODEX("codex", ".codex-plugin");

    private final String directoryName;
    private final String manifestDirectory;

    /**
     * Creates a runtime descriptor.
     *
     * @param directoryName the runtime directory name
     * @param manifestDirectory the plugin manifest directory name
     */
    Runtime(String directoryName, String manifestDirectory)
    {
      this.directoryName = directoryName;
      this.manifestDirectory = manifestDirectory;
    }
  }
}

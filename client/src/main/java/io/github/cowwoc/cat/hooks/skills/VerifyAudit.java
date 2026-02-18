/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.jackson.DefaultJacksonValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * CLI tool for extracting and reporting on verify-implementation audit data.
 * <p>
 * Provides subcommands:
 * <ul>
 *   <li>{@code prepare} - Validates arguments, parses PLAN.md, generates prompts, and verifies
 *     files in a single step</li>
 *   <li>{@code report} - Renders a formatted audit report from verification results</li>
 * </ul>
 */
public final class VerifyAudit
{
  private static final Pattern FULL_PATH_PATTERN =
    Pattern.compile("\\b([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+\\.[a-z]+)\\b");
  private static final Pattern FILENAME_PATTERN = Pattern.compile("\\b([a-zA-Z0-9_-]+\\.[a-z]{1,4})\\b");
  private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*-\\s+([^\\s]+)");
  private static final Pattern FILES_LABEL_PATTERN =
    Pattern.compile("Files?:\\s+([^\\s,]+(?:\\.[a-z]+)?)", Pattern.CASE_INSENSITIVE);
  private static final Pattern DELETE_FILE_PATTERN = Pattern.compile("([a-zA-Z0-9_/.-]+\\.[a-z]+)");
  private static final TypeReference<Map<String, String>> MAP_STRING_TYPE = new TypeReference<>()
  {
  };

  private final JvmScope scope;

  /**
   * Creates a new VerifyAudit instance.
   *
   * @param scope the JVM scope providing JSON mapper and display utilities
   * @throws NullPointerException if {@code scope} is null
   */
  public VerifyAudit(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Parses PLAN.md and extracts acceptance criteria, file specs, and grouped criteria.
   * <p>
   * Returns a JSON object with the following schema:
   * <pre>{@code
   * {
   *   "criteria": ["criterion text 1", "criterion text 2", ...],
   *   "file_specs": {
   *     "modify": ["path/to/file1.ext", "path/to/file2.ext", ...],
   *     "delete": ["path/to/deleted.ext", ...]
   *   },
   *   "groups": [
   *     {
   *       "files": ["path/to/file1.ext", ...],
   *       "criteria": ["criterion text", ...]
   *     },
   *     ...
   *   ]
   * }
   * }</pre>
   * <p>
   * The {@code criteria} array contains all acceptance criteria extracted from the "Acceptance Criteria"
   * section. The {@code file_specs} object lists files to modify or delete. The {@code groups} array
   * organizes criteria by their file dependencies for optimized verification.
   *
   * @param planPath path to PLAN.md file
   * @return JSON string with criteria, file_specs, and groups
   * @throws NullPointerException if {@code planPath} is null
   * @throws IOException if the file cannot be read
   */
  public String parse(Path planPath) throws IOException
  {
    requireThat(planPath, "planPath").isNotNull();

    String content = Files.readString(planPath);
    List<String> criteria = extractAcceptanceCriteria(content);
    FileSpecs fileSpecs = extractFileSpecs(content);
    List<CriteriaGroup> groups = groupCriteriaByFiles(criteria, fileSpecs);

    ObjectNode root = scope.getJsonMapper().createObjectNode();
    ArrayNode criteriaArray = scope.getJsonMapper().createArrayNode();
    for (String criterion : criteria)
      criteriaArray.add(criterion);
    root.set("criteria", criteriaArray);

    ObjectNode fileSpecsNode = scope.getJsonMapper().createObjectNode();
    ArrayNode modifyArray = scope.getJsonMapper().createArrayNode();
    for (String file : fileSpecs.modify())
      modifyArray.add(file);
    ArrayNode deleteArray = scope.getJsonMapper().createArrayNode();
    for (String file : fileSpecs.delete())
      deleteArray.add(file);
    fileSpecsNode.set("modify", modifyArray);
    fileSpecsNode.set("delete", deleteArray);
    root.set("file_specs", fileSpecsNode);

    ArrayNode groupsArray = scope.getJsonMapper().createArrayNode();
    for (CriteriaGroup group : groups)
    {
      ObjectNode groupNode = scope.getJsonMapper().createObjectNode();
      ArrayNode filesArray = scope.getJsonMapper().createArrayNode();
      for (String file : group.files())
        filesArray.add(file);
      groupNode.set("files", filesArray);

      ArrayNode critArray = scope.getJsonMapper().createArrayNode();
      for (String criterion : group.criteria())
        critArray.add(criterion);
      groupNode.set("criteria", critArray);
      groupsArray.add(groupNode);
    }
    root.set("groups", groupsArray);

    return scope.getJsonMapper().writeValueAsString(root);
  }

  /**
   * Generates a formatted audit report from verification results.
   * <p>
   * Expects a JSON input with the following schema:
   * <pre>{@code
   * {
   *   "criteria_results": [
   *     {
   *       "criterion": "criterion text",
   *       "status": "Done|Partial|Missing",
   *       "evidence": [
   *         {"type": "file_exists|content_match|command_output", "detail": "description"}
   *       ],
   *       "notes": "optional notes"
   *     },
   *     ...
   *   ],
   *   "file_results": {
   *     "modify": {
   *       "path/to/file.ext": "exists_and_modified|exists_not_modified|missing"
   *     },
   *     "delete": {
   *       "path/to/deleted.ext": "deleted_confirmed|never_existed|still_exists"
   *     }
   *   }
   * }
   * }</pre>
   * <p>
   * Returns a formatted report with box summary, criteria details, file details, and ends with a JSON
   * summary line:
   * <pre>{@code
   * {"total": N, "done": N, "partial": N, "missing": N,
   *  "file_done": N, "file_missing": N, "assessment": "COMPLETE|PARTIAL|INCOMPLETE"}
   * }</pre>
   *
   * @param issueId the issue ID
   * @param resultsJson JSON verification results from stdin
   * @return formatted report with JSON summary line
   * @throws NullPointerException if {@code issueId} or {@code resultsJson} are null
   * @throws IOException if parsing fails
   */
  public String report(String issueId, String resultsJson) throws IOException
  {
    requireThat(issueId, "issueId").isNotNull();
    requireThat(resultsJson, "resultsJson").isNotNull();

    JsonNode root = scope.getJsonMapper().readTree(resultsJson);
    JsonNode criteriaResults = root.path("criteria_results");
    JsonNode fileResults = root.path("file_results");

    int total = 0;
    int done = 0;
    int partial = 0;
    int missing = 0;

    for (JsonNode result : criteriaResults)
    {
      ++total;
      String status = result.path("status").asString();
      if (status.equals("Done"))
        ++done;
      else if (status.equals("Partial"))
        ++partial;
      else if (status.equals("Missing"))
        ++missing;
    }

    JsonNode modifyResults = fileResults.path("modify");
    FileStatusCounts modifyCounts = countFileStatus(modifyResults, "exists_and_modified");

    JsonNode deleteResults = fileResults.path("delete");
    FileStatusCounts deleteCounts = countFileStatus(deleteResults, "deleted_confirmed");

    int fileDone = modifyCounts.done() + deleteCounts.done();
    int fileMissing = modifyCounts.missing() + deleteCounts.missing();

    String assessment;
    if (missing > 0 || fileMissing > 0)
      assessment = "INCOMPLETE";
    else if (partial > 0)
      assessment = "PARTIAL";
    else
      assessment = "COMPLETE";

    String reportBox = renderReportBox(issueId, total, done, partial, missing, fileDone, fileMissing);
    String criteriaDetails = renderCriteriaDetails(criteriaResults);
    String fileDetails = renderFileDetails(fileResults);

    StringBuilder output = new StringBuilder(1024);
    output.append(reportBox).append('\n').append('\n').
      append(criteriaDetails).append('\n').append('\n').
      append(fileDetails).append('\n');

    ObjectNode summary = scope.getJsonMapper().createObjectNode();
    summary.put("total", total);
    summary.put("done", done);
    summary.put("partial", partial);
    summary.put("missing", missing);
    summary.put("file_done", fileDone);
    summary.put("file_missing", fileMissing);
    summary.put("assessment", assessment);
    output.append(scope.getJsonMapper().writeValueAsString(summary));

    return output.toString();
  }

  /**
   * Performs all preparation work for a verify-implementation audit in a single step.
   * <p>
   * Takes raw ARGUMENTS JSON on stdin and:
   * <ol>
   *   <li>Extracts and validates issue_id, issue_path, worktree_path from JSON</li>
   *   <li>Parses PLAN.md to extract criteria, file specs, and groups</li>
   *   <li>Generates verification subagent prompts from groups</li>
   *   <li>Verifies file existence and git history against file specs</li>
   * </ol>
   * <p>
   * Returns a JSON object:
   * <pre>{@code
   * {
   *   "issue_id": "2.1-issue-name",
   *   "issue_path": "/path/to/issue",
   *   "worktree_path": "/path/to/worktree",
   *   "criteria_count": 3,
   *   "file_count": 2,
   *   "prompts": [
   *     {"group_index": 0, "prompt": "...full prompt text..."}
   *   ],
   *   "file_results": {
   *     "modify": {"file1.java": "exists_and_modified"},
   *     "delete": {}
   *   }
   * }
   * }</pre>
   *
   * @param argumentsJson the JSON string containing issue_id, issue_path, worktree_path
   * @return JSON string with all preparation results
   * @throws NullPointerException if {@code argumentsJson} is null
   * @throws IllegalArgumentException if required fields are missing or PLAN.md does not exist
   * @throws IOException if JSON parsing or file operations fail
   */
  public String prepare(String argumentsJson) throws IOException
  {
    requireThat(argumentsJson, "argumentsJson").isNotNull();

    JsonNode argsRoot = scope.getJsonMapper().readTree(argumentsJson);

    String issueId = requireThat(argsRoot, "argumentsJson").
      property("issue_id").isString().getValue().asString();
    requireThat(issueId, "argumentsJson.issue_id").isNotBlank();

    String issuePath = requireThat(argsRoot, "argumentsJson").
      property("issue_path").isString().getValue().asString();
    requireThat(issuePath, "argumentsJson.issue_path").isNotBlank();

    String worktreePath = requireThat(argsRoot, "argumentsJson").
      property("worktree_path").isString().getValue().asString();
    requireThat(worktreePath, "argumentsJson.worktree_path").isNotBlank();

    Path issueDir = Path.of(issuePath);
    Path planFile = issueDir.resolve("PLAN.md");
    if (!Files.exists(planFile))
      throw new IllegalArgumentException("PLAN.md not found at " + issuePath);

    String content = Files.readString(planFile);
    List<String> criteria = extractAcceptanceCriteria(content);
    FileSpecs fileSpecs = extractFileSpecs(content);
    List<CriteriaGroup> groups = groupCriteriaByFiles(criteria, fileSpecs);

    int fileCount = fileSpecs.modify().size() + fileSpecs.delete().size();

    List<String> prompts = new ArrayList<>();
    for (CriteriaGroup group : groups)
      prompts.add(buildPromptForGroup(buildGroupNode(group)));

    String fileSpecsJson = buildFileSpecsJson(fileSpecs);
    String fileResultsJson = verifyFilesInternal(fileSpecsJson, Path.of(worktreePath));

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.put("issue_id", issueId);
    result.put("issue_path", issuePath);
    result.put("worktree_path", worktreePath);
    result.put("criteria_count", criteria.size());
    result.put("file_count", fileCount);

    ArrayNode promptsArray = scope.getJsonMapper().createArrayNode();
    for (int idx = 0; idx < prompts.size(); ++idx)
    {
      ObjectNode promptEntry = scope.getJsonMapper().createObjectNode();
      promptEntry.put("group_index", idx);
      promptEntry.put("prompt", prompts.get(idx));
      promptsArray.add(promptEntry);
    }
    result.set("prompts", promptsArray);

    JsonNode fileResultsNode = scope.getJsonMapper().readTree(fileResultsJson);
    result.set("file_results", fileResultsNode);

    return scope.getJsonMapper().writeValueAsString(result);
  }

  /**
   * Builds a JsonNode representing a criteria group, suitable for passing to buildPromptForGroup.
   *
   * @param group the criteria group
   * @return a JsonNode with "files" and "criteria" arrays
   */
  private JsonNode buildGroupNode(CriteriaGroup group)
  {
    ObjectNode groupNode = scope.getJsonMapper().createObjectNode();
    ArrayNode filesArray = scope.getJsonMapper().createArrayNode();
    for (String file : group.files())
      filesArray.add(file);
    groupNode.set("files", filesArray);

    ArrayNode criteriaArray = scope.getJsonMapper().createArrayNode();
    for (String criterion : group.criteria())
      criteriaArray.add(criterion);
    groupNode.set("criteria", criteriaArray);

    return groupNode;
  }

  /**
   * Builds a file specs JSON string from a FileSpecs object.
   *
   * @param fileSpecs the file specifications
   * @return JSON string with "modify" and "delete" arrays
   * @throws IOException if serialization fails
   */
  private String buildFileSpecsJson(FileSpecs fileSpecs) throws IOException
  {
    ObjectNode root = scope.getJsonMapper().createObjectNode();
    ArrayNode modifyArray = scope.getJsonMapper().createArrayNode();
    for (String file : fileSpecs.modify())
      modifyArray.add(file);
    ArrayNode deleteArray = scope.getJsonMapper().createArrayNode();
    for (String file : fileSpecs.delete())
      deleteArray.add(file);
    root.set("modify", modifyArray);
    root.set("delete", deleteArray);
    return scope.getJsonMapper().writeValueAsString(root);
  }

  /**
   * Verifies file existence and git history against file specifications.
   * <p>
   * For "modify" files: checks if the file exists on disk AND git log shows modifications.
   * For "delete" files: checks if the file does NOT exist AND git log shows it previously existed.
   *
   * @param fileSpecsJson JSON with "modify" and "delete" string arrays
   * @param directory the working directory for git commands
   * @return JSON object mapping each file to its verification status
   * @throws NullPointerException if {@code fileSpecsJson} or {@code directory} are null
   * @throws IOException if JSON parsing fails
   */
  private String verifyFilesInternal(String fileSpecsJson, Path directory) throws IOException
  {
    JsonNode root = scope.getJsonMapper().readTree(fileSpecsJson);
    JsonNode modifyNode = root.path("modify");
    JsonNode deleteNode = root.path("delete");

    Map<String, String> modifyResults = new LinkedHashMap<>();
    if (modifyNode.isArray())
    {
      for (JsonNode fileNode : modifyNode)
      {
        String file = fileNode.asString();
        Path filePath = directory.resolve(file);

        if (Files.exists(filePath))
        {
          String gitOutput = runGitLog(directory, file);
          if (gitOutput != null && !gitOutput.isEmpty())
            modifyResults.put(file, "exists_and_modified");
          else
            modifyResults.put(file, "exists_not_modified");
        }
        else
        {
          modifyResults.put(file, "missing");
        }
      }
    }

    Map<String, String> deleteResults = new LinkedHashMap<>();
    if (deleteNode.isArray())
    {
      for (JsonNode fileNode : deleteNode)
      {
        String file = fileNode.asString();
        Path filePath = directory.resolve(file);

        if (!Files.exists(filePath))
        {
          String gitOutput = runGitLogAll(directory, file);
          if (gitOutput != null && !gitOutput.isEmpty())
            deleteResults.put(file, "deleted_confirmed");
          else
            deleteResults.put(file, "never_existed");
        }
        else
        {
          deleteResults.put(file, "still_exists");
        }
      }
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    ObjectNode modifyObj = scope.getJsonMapper().createObjectNode();
    for (Map.Entry<String, String> entry : modifyResults.entrySet())
      modifyObj.put(entry.getKey(), entry.getValue());
    ObjectNode deleteObj = scope.getJsonMapper().createObjectNode();
    for (Map.Entry<String, String> entry : deleteResults.entrySet())
      deleteObj.put(entry.getKey(), entry.getValue());
    result.set("modify", modifyObj);
    result.set("delete", deleteObj);

    return scope.getJsonMapper().writeValueAsString(result);
  }

  /**
   * Builds a verification prompt for a single criteria group.
   *
   * @param group the group node with "files" and "criteria" arrays
   * @return the prompt text
   */
  private String buildPromptForGroup(JsonNode group)
  {
    JsonNode filesNode = group.path("files");
    JsonNode criteriaNode = group.path("criteria");

    List<String> files = new ArrayList<>();
    for (JsonNode f : filesNode)
      files.add(f.asString());
    List<String> criteria = new ArrayList<>();
    for (JsonNode c : criteriaNode)
      criteria.add(c.asString());

    StringBuilder fileList = new StringBuilder();
    for (String f : files)
      fileList.append("- ").append(f).append('\n');

    String criterionSection;
    if (criteria.size() == 1)
    {
      criterionSection = "## Acceptance Criterion\n" + criteria.get(0);
    }
    else
    {
      StringBuilder lines = new StringBuilder();
      for (int i = 0; i < criteria.size(); ++i)
        lines.append(i + 1).append(". ").append(criteria.get(i)).append('\n');
      criterionSection = "## Acceptance Criteria\n" + lines.toString().stripTrailing();
    }

    String taskVerb;
    if (criteria.size() == 1)
      taskVerb = "this criterion is";
    else
      taskVerb = "EACH criterion is";

    return """
      You are a verification agent auditing implementation compliance with planned acceptance criteria.

      %s

      ## Task
      Verify whether %s satisfied in the codebase.

      Use these tools to investigate:
      - Read: Check file contents
      - Glob: Find files by pattern
      - Grep: Search for specific content
      - Bash: Run verification commands (git log, test commands, etc.)

      ## Evidence Requirements
      For each criterion, provide:
      1. Status: Done|Partial|Missing
      2. Evidence: File paths, line numbers, command outputs
      3. Notes: Any discrepancies or concerns

      If a criterion cannot be definitively verified, set status to Missing with an explanation \
      of why verification was not possible. Do not guess or assume.

      ## File Context
      These files were specified in PLAN.md:
      %s
      ## Response Format
      Return JSON array with one entry per criterion (even if only one criterion):
      [
        {
          "criterion": "criterion text",
          "status": "Done|Partial|Missing",
          "evidence": [
            {"type": "file_exists|content_match|command_output", "detail": "..."}
          ],
          "notes": "Any additional observations"
        }
      ]

      Be thorough but efficient. Check actual implementation, not just file existence.""".
      formatted(criterionSection, taskVerb, fileList.toString());
  }

  /**
   * Runs git log for a file in the specified directory.
   *
   * @param directory the working directory
   * @param file the file path relative to the directory
   * @return git log output, or null on error
   */
  private String runGitLog(Path directory, String file)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "log", "--oneline", "--follow", "-5", "--", file);
      pb.directory(directory.toFile());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (output.length() > 0)
            output.append('\n');
          output.append(line);
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        return null;
      return output.toString();
    }
    catch (IOException | InterruptedException _)
    {
      return null;
    }
  }

  /**
   * Runs git log --all for a file in the specified directory to check historical existence.
   *
   * @param directory the working directory
   * @param file the file path relative to the directory
   * @return git log output, or null on error
   */
  private String runGitLogAll(Path directory, String file)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "log", "--all", "--oneline", "-5", "--", file);
      pb.directory(directory.toFile());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (output.length() > 0)
            output.append('\n');
          output.append(line);
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        return null;
      return output.toString();
    }
    catch (IOException | InterruptedException _)
    {
      return null;
    }
  }

  /**
   * Extracts acceptance criteria from PLAN.md content.
   *
   * @param content the PLAN.md file content
   * @return list of criteria text
   */
  private List<String> extractAcceptanceCriteria(String content)
  {
    List<String> criteria = new ArrayList<>();
    boolean inSection = false;

    for (String line : content.split("\n"))
    {
      if (line.startsWith("## Acceptance Criteria"))
      {
        inSection = true;
        continue;
      }
      if (inSection && line.startsWith("##"))
        break;
      if (inSection)
      {
        String stripped = line.strip();
        if (stripped.startsWith("- [ ]"))
        {
          String criterion = stripped.substring(6).strip();
          criteria.add(criterion);
        }
        else if (stripped.startsWith("- [x]"))
        {
          String criterion = stripped.substring(6).strip();
          criteria.add(criterion);
        }
      }
    }

    return criteria;
  }

  /**
   * Extracts file specifications from PLAN.md content.
   *
   * @param content the PLAN.md file content
   * @return file specs (modify and delete lists)
   */
  private FileSpecs extractFileSpecs(String content)
  {
    Set<String> modifySet = new LinkedHashSet<>();
    Set<String> deleteSet = new LinkedHashSet<>();

    String[] lines = content.split("\n");
    boolean inModify = false;
    boolean inDelete = false;
    boolean inSteps = false;

    for (String line : lines)
    {
      if (line.startsWith("## Files to Modify"))
      {
        inModify = true;
        inDelete = false;
        inSteps = false;
        continue;
      }
      if (line.startsWith("## Files to Delete"))
      {
        inModify = false;
        inDelete = true;
        inSteps = false;
        continue;
      }
      if (line.startsWith("## Execution Steps"))
      {
        inModify = false;
        inDelete = false;
        inSteps = true;
        continue;
      }
      if (line.startsWith("##"))
      {
        inModify = false;
        inDelete = false;
        inSteps = false;
        continue;
      }

      if (inModify)
        extractFileFromListSection(line, modifySet);
      if (inDelete)
        extractFileFromListSection(line, deleteSet);
      if (inSteps)
        extractFilesFromExecutionSteps(line, modifySet, deleteSet);
    }

    return new FileSpecs(new ArrayList<>(modifySet), new ArrayList<>(deleteSet));
  }

  /**
   * Extracts file paths from a list section.
   *
   * @param line the line to parse
   * @param targetSet the set to add files to
   */
  private void extractFileFromListSection(String line, Set<String> targetSet)
  {
    if (!line.strip().startsWith("-"))
      return;

    Matcher matcher = LIST_ITEM_PATTERN.matcher(line);
    if (matcher.find())
    {
      String item = matcher.group(1);
      if (item.contains("/") || item.contains("."))
        targetSet.add(item);
    }
  }

  /**
   * Extracts file paths from Execution Steps section.
   *
   * @param line the line to parse
   * @param modify the set to add modification files to
   * @param delete the set to add deletion files to
   */
  private void extractFilesFromExecutionSteps(String line, Set<String> modify, Set<String> delete)
  {
    Matcher filesMatcher = FILES_LABEL_PATTERN.matcher(line);
    if (filesMatcher.find())
    {
      String filePath = filesMatcher.group(1);
      modify.add(filePath);
    }

    Matcher pathMatcher = FULL_PATH_PATTERN.matcher(line);
    while (pathMatcher.find())
    {
      String filePath = pathMatcher.group(1);
      modify.add(filePath);
    }

    if (line.toLowerCase(Locale.ROOT).matches(".*\\b(delete|remove|rm)\\b.*"))
    {
      Matcher deleteMatcher = DELETE_FILE_PATTERN.matcher(line);
      if (deleteMatcher.find())
      {
        String filePath = deleteMatcher.group(1);
        delete.add(filePath);
      }
    }
  }

  /**
   * Extracts file references from a criterion text.
   *
   * @param criterionText the criterion text
   * @param fileSpecs all file specs from PLAN.md
   * @return set of referenced files (all file specs if none mentioned)
   */
  private Set<String> extractFileReferences(String criterionText, FileSpecs fileSpecs)
  {
    Set<String> referenced = new HashSet<>();
    List<String> allFileSpecs = new ArrayList<>();
    allFileSpecs.addAll(fileSpecs.modify());
    allFileSpecs.addAll(fileSpecs.delete());

    Matcher pathMatcher = FULL_PATH_PATTERN.matcher(criterionText);
    while (pathMatcher.find())
      referenced.add(pathMatcher.group(1));

    Matcher fileMatcher = FILENAME_PATTERN.matcher(criterionText);
    while (fileMatcher.find())
    {
      String filename = fileMatcher.group(1);
      for (String spec : allFileSpecs)
      {
        if (spec.endsWith(filename))
          referenced.add(spec);
      }
    }

    if (referenced.isEmpty())
      return new HashSet<>(allFileSpecs);

    return referenced;
  }

  /**
   * Groups criteria by their file dependencies.
   *
   * @param criteria list of acceptance criteria
   * @param fileSpecs file specifications
   * @return list of criteria groups
   */
  private List<CriteriaGroup> groupCriteriaByFiles(List<String> criteria, FileSpecs fileSpecs)
  {
    Map<String, List<String>> groups = new HashMap<>();

    for (String criterion : criteria)
    {
      Set<String> fileSet = extractFileReferences(criterion, fileSpecs);
      String sortedKey = fileSet.stream().sorted().collect(Collectors.joining(","));
      if (!groups.containsKey(sortedKey))
        groups.put(sortedKey, new ArrayList<>());
      groups.get(sortedKey).add(criterion);
    }

    return groups.entrySet().stream().
      map(entry ->
      {
        Set<String> files;
        if (entry.getKey().isEmpty())
        {
          files = new HashSet<>();
        }
        else
        {
          files = new HashSet<>(List.of(entry.getKey().split(",")));
        }
        return new CriteriaGroup(files, entry.getValue());
      }).
      collect(Collectors.toList());
  }

  /**
   * Renders the summary box at the top of the report.
   *
   * @param issueId the issue ID
   * @param total total criteria count
   * @param done done count
   * @param partial partial count
   * @param missing missing count
   * @param fileDone file done count
   * @param fileMissing file missing count
   * @return formatted box output
   */
  private String renderReportBox(String issueId, int total, int done, int partial, int missing,
    int fileDone, int fileMissing)
  {
    String headerContent = DisplayUtils.BOX_HORIZONTAL + " AUDIT REPORT: " + issueId + " ";
    int headerWidth = scope.getDisplayUtils().displayWidth(headerContent);

    int contentWidth = Math.max(headerWidth, 76);
    int topPadding = contentWidth - headerWidth;
    if (topPadding < 0)
      topPadding = 0;
    String topDashes = DisplayUtils.BOX_HORIZONTAL.repeat(topPadding);
    String topLine = DisplayUtils.BOX_TOP_LEFT + headerContent + topDashes + DisplayUtils.BOX_TOP_RIGHT;

    StringBuilder output = new StringBuilder(512);
    output.append(topLine).append('\n');
    appendBoxLine(output, " Summary", contentWidth);
    appendBoxLine(output, "   Acceptance Criteria:", contentWidth);
    appendBoxLine(output, "     Total:        " + total, contentWidth);
    appendBoxLine(output, "     ✓ Done:       " + done, contentWidth);
    appendBoxLine(output, "     ◐ Partial:    " + partial, contentWidth);
    appendBoxLine(output, "     ✗ Missing:    " + missing, contentWidth);
    appendBoxLine(output, "   File Verifications:", contentWidth);
    appendBoxLine(output, "     ✓ Done:       " + fileDone, contentWidth);
    appendBoxLine(output, "     ✗ Missing:    " + fileMissing, contentWidth);
    output.append(DisplayUtils.BOX_BOTTOM_LEFT).append(DisplayUtils.BOX_HORIZONTAL.repeat(contentWidth)).
      append(DisplayUtils.BOX_BOTTOM_RIGHT);

    return output.toString();
  }

  /**
   * Appends a single line to the box with proper borders and padding.
   *
   * @param output the StringBuilder to append to
   * @param content the line content
   * @param boxWidth the total width of the box content area
   */
  private void appendBoxLine(StringBuilder output, String content, int boxWidth)
  {
    int contentDisplayWidth = scope.getDisplayUtils().displayWidth(content);
    int padding = boxWidth - contentDisplayWidth;
    output.append(DisplayUtils.BOX_VERTICAL).append(content).
      append(" ".repeat(padding)).
      append(DisplayUtils.BOX_VERTICAL).append('\n');
  }

  /**
   * Counts done and missing statuses in file results.
   *
   * @param results the file results node (modify or delete)
   * @param successStatus the status value that indicates success (e.g., "exists_and_modified" or
   *   "deleted_confirmed")
   * @return counts of done and missing files
   */
  private FileStatusCounts countFileStatus(JsonNode results, String successStatus)
  {
    int done = 0;
    int missing = 0;

    if (results.isObject() && results.size() > 0)
    {
      Map<String, String> statusMap = scope.getJsonMapper().convertValue(results, MAP_STRING_TYPE);
      for (String status : statusMap.values())
      {
        if (status.equals(successStatus))
          ++done;
        else
          ++missing;
      }
    }

    return new FileStatusCounts(done, missing);
  }

  /**
   * Renders criteria verification details.
   *
   * @param criteriaResults the criteria results array
   * @return formatted output
   */
  private String renderCriteriaDetails(JsonNode criteriaResults)
  {
    StringBuilder output = new StringBuilder(512);
    output.append("━".repeat(80)).append('\n').
      append("ACCEPTANCE CRITERIA VERIFICATION").append('\n').
      append("━".repeat(80)).append('\n').append('\n');

    int index = 1;
    for (JsonNode result : criteriaResults)
    {
      String criterion = result.path("criterion").asString();
      String status = result.path("status").asString();
      String statusSymbol;
      if (status.equals("Done"))
        statusSymbol = "✓";
      else if (status.equals("Partial"))
        statusSymbol = "◐";
      else
        statusSymbol = "✗";

      output.append(index).append(". ").append(statusSymbol).append(' ').append(status).
        append(": ").append(criterion).append('\n');

      JsonNode evidence = result.path("evidence");
      if (evidence.isArray() && !evidence.isEmpty())
      {
        output.append("   Evidence:").append('\n');
        for (JsonNode ev : evidence)
        {
          String type = ev.path("type").asString();
          String detail = ev.path("detail").asString();
          output.append("   - ").append(type).append(": ").append(detail).append('\n');
        }
      }

      String notes = result.path("notes").asString("");
      if (!notes.isEmpty())
        output.append("   Notes: ").append(notes).append('\n');

      output.append('\n');
      ++index;
    }

    return output.toString();
  }

  /**
   * Renders file verification details.
   *
   * @param fileResults the file results node
   * @return formatted output
   */
  private String renderFileDetails(JsonNode fileResults)
  {
    StringBuilder output = new StringBuilder(256);
    output.append("━".repeat(80)).append('\n').
      append("FILE SPECIFICATIONS").append('\n').
      append("━".repeat(80)).append('\n').append('\n');

    JsonNode modifyResults = fileResults.path("modify");
    if (modifyResults.isObject() && modifyResults.size() > 0)
    {
      output.append("Files to Modify:").append('\n');
      Map<String, String> modifyMap = scope.getJsonMapper().convertValue(modifyResults, MAP_STRING_TYPE);
      for (Map.Entry<String, String> entry : modifyMap.entrySet())
      {
        String file = entry.getKey();
        String status = entry.getValue();
        String symbol;
        if (status.equals("exists_and_modified"))
          symbol = "✓";
        else
          symbol = "✗";
        output.append("  ").append(symbol).append(' ').append(file).append(" -> ").append(status).append('\n');
      }
      output.append('\n');
    }

    JsonNode deleteResults = fileResults.path("delete");
    if (deleteResults.isObject() && deleteResults.size() > 0)
    {
      output.append("Files to Delete:").append('\n');
      Map<String, String> deleteMap = scope.getJsonMapper().convertValue(deleteResults, MAP_STRING_TYPE);
      for (Map.Entry<String, String> entry : deleteMap.entrySet())
      {
        String file = entry.getKey();
        String status = entry.getValue();
        String symbol;
        if (status.equals("deleted_confirmed"))
          symbol = "✓";
        else
          symbol = "✗";
        output.append("  ").append(symbol).append(' ').append(file).append(" -> ").append(status).append('\n');
      }
      output.append('\n');
    }

    return output.toString();
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command-line arguments
   * @throws IOException if operations fail
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      System.out.println("""
        Usage: verify-audit <subcommand> [options]

        Subcommands:
          report <issue-id>             Generate audit report from stdin JSON
          prepare                      Validate args, parse PLAN.md, generate prompts, verify files

        Examples:
          echo '{"criteria_results": [...]}' | verify-audit report 2.1-issue-name
          echo '{"issue_id":"..."}' | verify-audit prepare""");
      return;
    }

    String subcommand = args[0];
    try (JvmScope scope = new MainJvmScope())
    {
      VerifyAudit audit = new VerifyAudit(scope);

      switch (subcommand)
      {
        case "report" ->
        {
          if (args.length < 2)
            throw new IllegalArgumentException("report subcommand requires issue-id argument");
          String issueId = args[1];
          System.out.println(audit.report(issueId, new String(System.in.readAllBytes(),
            StandardCharsets.UTF_8)));
        }
        case "prepare" ->
        {
          System.out.println(audit.prepare(new String(System.in.readAllBytes(),
            StandardCharsets.UTF_8)));
        }
        default -> throw new IllegalArgumentException("Unknown subcommand: " + subcommand);
      }
    }
  }

  /**
   * File specifications (modify and delete lists).
   *
   * @param modify files to modify
   * @param delete files to delete
   */
  private record FileSpecs(List<String> modify, List<String> delete)
  {
  }

  /**
   * Criteria group with shared file dependencies.
   *
   * @param files the set of files referenced by these criteria
   * @param criteria the list of criteria texts
   */
  private record CriteriaGroup(Set<String> files, List<String> criteria)
  {
  }

  /**
   * File status counts for done and missing files.
   *
   * @param done count of files with success status
   * @param missing count of files with non-success status
   */
  private record FileStatusCounts(int done, int missing)
  {
  }
}

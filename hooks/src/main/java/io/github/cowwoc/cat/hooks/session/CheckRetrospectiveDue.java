package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Checks whether a retrospective is due based on time elapsed or mistake count.
 * <p>
 * Reads configuration from {@code .claude/cat/retrospectives/index.json} and checks
 * two triggers:
 * <ul>
 *   <li>Time-based: days since last retrospective exceeds threshold (default 14)</li>
 *   <li>Count-based: accumulated mistakes exceed threshold (default 10)</li>
 * </ul>
 * If either trigger fires, outputs a reminder to stderr.
 */
public final class CheckRetrospectiveDue implements SessionStartHandler
{
  private static final int DEFAULT_TRIGGER_DAYS = 14;
  private static final int DEFAULT_MISTAKE_THRESHOLD = 10;
  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * Creates a new CheckRetrospectiveDue handler.
   */
  public CheckRetrospectiveDue()
  {
  }

  /**
   * Checks if a retrospective is due and returns a reminder if so.
   *
   * @param input the hook input
   * @return a result with stderr reminder if retrospective is due, empty otherwise
   * @throws NullPointerException if input is null
   * @throws WrappedCheckedException if the retrospective configuration cannot be read
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");

    Path projectPath = Path.of(projectDir);

    // Early exit if not in a CAT project
    if (!Files.isDirectory(projectPath.resolve(".planning")))
      return Result.empty();

    Path retroDir = projectPath.resolve(".claude/cat/retrospectives");
    if (!Files.isDirectory(retroDir))
      return Result.empty();

    Path indexFile = retroDir.resolve("index.json");
    try
    {
      return checkRetrospective(indexFile, retroDir);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Performs the retrospective check using the index file and retrospective directory.
   *
   * @param indexFile the path to index.json
   * @param retroDir the retrospectives directory
   * @return the result
   * @throws IOException if the index file or retrospectives directory cannot be read
   */
  private Result checkRetrospective(Path indexFile, Path retroDir) throws IOException
  {
    int triggerDays = DEFAULT_TRIGGER_DAYS;
    int mistakeThreshold = DEFAULT_MISTAKE_THRESHOLD;
    String lastRetro = "";
    int mistakeCount = 0;

    if (Files.isRegularFile(indexFile))
    {
      JsonNode root = mapper.readTree(Files.readString(indexFile));

      JsonNode config = root.get("config");
      if (config != null)
      {
        JsonNode triggerNode = config.get("trigger_interval_days");
        if (triggerNode != null && triggerNode.isNumber())
          triggerDays = triggerNode.asInt();
        JsonNode thresholdNode = config.get("mistake_count_threshold");
        if (thresholdNode != null && thresholdNode.isNumber())
          mistakeThreshold = thresholdNode.asInt();
      }

      JsonNode lastRetroNode = root.get("last_retrospective");
      if (lastRetroNode != null && lastRetroNode.isString())
        lastRetro = lastRetroNode.asString();

      JsonNode mistakeCountNode = root.get("mistake_count_since_last");
      if (mistakeCountNode != null && mistakeCountNode.isNumber())
        mistakeCount = mistakeCountNode.asInt();
    }

    boolean retroDue = false;
    String triggerReason = "";

    // Check 1: Time-based trigger
    if (lastRetro.isEmpty() || lastRetro.equals("null"))
    {
      // No retrospective ever run - check if we have any mistakes logged
      int totalMistakes = countMistakesFromFiles(retroDir);
      if (totalMistakes > 0)
      {
        retroDue = true;
        triggerReason = "First retrospective with " + totalMistakes + " logged mistakes";
      }
    }
    else
    {
      long daysSince = daysSinceDate(lastRetro);
      if (daysSince >= triggerDays)
      {
        retroDue = true;
        triggerReason = daysSince + " days since last retrospective (threshold: " + triggerDays + ")";
      }
    }

    // Check 2: Mistake count trigger
    if (!retroDue && mistakeCount >= mistakeThreshold)
    {
      retroDue = true;
      triggerReason = mistakeCount + " mistakes accumulated (threshold: " + mistakeThreshold + ")";
    }

    if (!retroDue)
      return Result.empty();

    String reminder = "\n" +
      "================================================================================\n" +
      "RETROSPECTIVE DUE\n" +
      "================================================================================\n" +
      "\n" +
      "Trigger: " + triggerReason + "\n" +
      "\n" +
      "A retrospective review is recommended to analyze accumulated mistakes and\n" +
      "identify recurring patterns that need systemic fixes.\n" +
      "\n" +
      "SUGGESTED ACTION: Invoke the retrospective skill:\n" +
      "\n" +
      "  Skill: retrospective\n" +
      "\n" +
      "This will:\n" +
      "1. Aggregate all mistakes since last retrospective\n" +
      "2. Identify recurring patterns\n" +
      "3. Check effectiveness of previous action items\n" +
      "4. Generate new action items for systemic fixes\n" +
      "\n" +
      "================================================================================\n";

    return Result.stderr(reminder);
  }

  /**
   * Counts total mistakes from mistakes-*.json files in the retrospectives directory.
   *
   * @param retroDir the retrospectives directory
   * @return the total number of mistakes
   * @throws IOException if the retrospectives directory cannot be read
   */
  private int countMistakesFromFiles(Path retroDir) throws IOException
  {
    int total = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(retroDir, "mistakes-*.json"))
    {
      for (Path file : stream)
      {
        try
        {
          JsonNode root = mapper.readTree(Files.readString(file));
          JsonNode mistakes = root.get("mistakes");
          if (mistakes != null && mistakes.isArray())
            total += mistakes.size();
        }
        catch (IOException _)
        {
          // Skip malformed files
        }
      }
    }
    return total;
  }

  /**
   * Calculates the number of days since a given ISO date string.
   *
   * @param dateString the date string to parse
   * @return the number of days since the date
   * @throws DateTimeParseException if {@code dateString} cannot be parsed as an ISO instant
   */
  private long daysSinceDate(String dateString)
  {
    Instant lastDate = Instant.parse(dateString);
    Instant now = Instant.now();
    return ChronoUnit.DAYS.between(lastDate, now);
  }
}

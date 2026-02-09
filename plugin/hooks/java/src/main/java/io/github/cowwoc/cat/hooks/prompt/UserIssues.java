package io.github.cowwoc.cat.hooks.prompt;

import io.github.cowwoc.cat.hooks.PromptHandler;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Detects user-reported issues and flags them as detection gaps requiring TDD.
 */
public final class UserIssues implements PromptHandler
{
  private static final Set<String> ISSUE_PATTERNS = Set.of(
    "this is wrong", "this is incorrect", "that's wrong", "that's incorrect",
    "bug in", "there's a bug", "doesn't work", "isn't working", "not working",
    "broken", "should be", "should have been", "you missed", "missing from",
    "forgot to", "failed to", "why didn't", "why isn't", "still showing",
    "still has", "still contains", "didn't catch", "wasn't caught",
    "wasn't detected", "not detected", "false positive", "false negative",
    "incorrect output", "wrong output", "wrong result", "incorrect result",
    "ignored", "you ignored", "didn't acknowledge", "didn't respond to",
    "expected behavior");

  /**
   * Creates a new user issues handler.
   */
  public UserIssues()
  {
    // Handler class
  }

  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    String promptLower = prompt.toLowerCase(Locale.ROOT);
    String matchedPattern = null;

    for (String pattern : ISSUE_PATTERNS)
    {
      if (promptLower.contains(pattern))
      {
        matchedPattern = pattern;
        break;
      }
    }

    if (matchedPattern == null)
      return "";

    String timestamp = Instant.now().toString();
    String gapId = "GAP-" + Instant.now().getEpochSecond();

    recordGap(sessionId, gapId, matchedPattern, prompt, timestamp);

    return "DETECTION GAP IDENTIFIED\n" +
      "\n" +
      "  The user reported an issue that our validation didn't catch.\n" +
      "  This is a DETECTION GAP requiring TDD workflow.\n" +
      "\n" +
      "  Pattern matched: \"" + matchedPattern + "\"\n" +
      "  Gap ID: " + gapId + "\n" +
      "\n" +
      "  REQUIRED WORKFLOW (Test-Driven Bug Fix):\n" +
      "  1. Write a FAILING test that reproduces the user's issue\n" +
      "  2. Verify the test FAILS (proves it catches the bug)\n" +
      "  3. Fix the code\n" +
      "  4. Verify the test PASSES\n" +
      "\n" +
      "  Invoke: Skill: tdd-implementation";
  }

  /**
   * Records a detection gap to a JSON file.
   *
   * @param sessionId the session ID
   * @param gapId the gap identifier
   * @param pattern the matched pattern
   * @param prompt the user's prompt (truncated)
   * @param timestamp the ISO timestamp
   */
  private void recordGap(String sessionId, String gapId, String pattern, String prompt, String timestamp)
  {
    Path gapsFile = Path.of("/tmp/pending_detection_gaps_" + sessionId + ".json");
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      ObjectNode data;
      if (Files.exists(gapsFile))
        data = (ObjectNode) mapper.readTree(Files.readString(gapsFile));
      else
      {
        data = mapper.createObjectNode();
        data.put("created", timestamp);
        data.set("gaps", mapper.createArrayNode());
      }

      ArrayNode gaps = (ArrayNode) data.get("gaps");
      ObjectNode gap = mapper.createObjectNode();
      gap.put("id", gapId);
      gap.put("pattern", pattern);
      String userMessage;
      if (prompt.length() > 500)
        userMessage = prompt.substring(0, 500);
      else
        userMessage = prompt;
      gap.put("user_message", userMessage);
      gap.put("timestamp", timestamp);
      gap.put("status", "pending_tdd");
      gap.put("test_written", false);
      gaps.add(gap);

      Files.writeString(gapsFile, mapper.writeValueAsString(data));
    }
    catch (IOException _)
    {
      // Non-blocking - silently ignore
    }
  }
}

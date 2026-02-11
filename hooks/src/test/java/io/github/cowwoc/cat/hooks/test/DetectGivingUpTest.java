package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.prompt.DetectGivingUp;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DetectGivingUp handler.
 * <p>
 * Tests verify pattern detection, rate limiting, session tracking, and quote removal.
 */
public final class DetectGivingUpTest
{
  /**
   * An AutoCloseable session that cleans up its /tmp directory on close.
   *
   * @param sessionId the session ID
   * @param sessionDir the session directory path
   */
  private record TestSession(String sessionId, Path sessionDir) implements AutoCloseable
  {
    /**
     * Creates a new test session.
     *
     * @param sessionId the session ID
     * @param sessionDir the session directory path
     * @throws NullPointerException if any parameter is null
     */
    public TestSession
    {
      requireThat(sessionId, "sessionId").isNotNull();
      requireThat(sessionDir, "sessionDir").isNotNull();
    }

    @Override
    public void close() throws IOException
    {
      if (Files.exists(sessionDir))
        deleteDirectory(sessionDir);
    }

    /**
     * Recursively deletes a directory.
     *
     * @param dir the directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(Path dir) throws IOException
    {
      if (!Files.exists(dir))
        return;

      if (Files.isDirectory(dir))
      {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir))
        {
          for (Path entry : stream)
            deleteDirectory(entry);
        }
      }

      Files.delete(dir);
    }
  }

  /**
   * Verifies that empty prompt returns empty string.
   */
  @Test
  public void emptyPromptReturnsEmpty()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String result = handler.check("", "test-session-1");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that normal text without patterns returns empty string.
   */
  @Test
  public void normalTextReturnsEmpty()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String result = handler.check(
      "Let me implement this feature properly.", "test-session-2");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies constraint rationalization detection with composable keywords.
   */
  @Test
  public void detectsConstraintRationalization() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the complexity of this task, I'll skip the advanced features.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
      requireThat(result, "result").contains("PERSISTENCE REQUIRED");
    }
  }

  /**
   * Verifies detection of specific constraint rationalization pattern.
   */
  @Test
  public void detectsSpecificConstraintPattern() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Given the complexity of properly implementing this, " +
        "let me take a different approach.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    }
  }

  /**
   * Verifies code disabling pattern detection.
   */
  @Test
  public void detectsCodeDisabling() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The test is failing, so I'll temporarily disable this code.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
      requireThat(result, "result").contains("DEBUGGING REQUIRED");
    }
  }

  /**
   * Verifies broken code removal pattern detection.
   */
  @Test
  public void detectsBrokenCodeRemoval() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The test passes without the custom deserializer, " +
        "so let me remove it.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    }
  }

  /**
   * Verifies compilation abandonment pattern detection.
   */
  @Test
  public void detectsCompilationAbandonment() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to the compilation error with complex JPMS issues, " +
        "I'll simplify by removing the dependency.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("COMPILATION DEBUGGING ABANDONMENT DETECTED");
      requireThat(result, "result").contains("SYSTEMATIC APPROACH REQUIRED");
    }
  }

  /**
   * Verifies permission seeking pattern detection.
   */
  @Test
  public void detectsPermissionSeeking() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Would you like me to continue with implementation " +
        "or select a different task?";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("PROTOCOL VIOLATION DETECTED");
      requireThat(result, "result").contains("AUTONOMOUS COMPLETION REQUIRED");
    }
  }

  /**
   * Verifies time estimate permission seeking pattern.
   */
  @Test
  public void detectsTimeEstimatePermissionSeeking() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "This will take 2-3 days for implementation. Should I proceed?";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("PROTOCOL VIOLATION DETECTED");
    }
  }

  /**
   * Verifies quoted text is ignored to prevent false positives.
   */
  @Test
  public void quotedTextIgnored() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "The user said \"given the complexity, I'll skip this\" " +
        "but I will implement it fully.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies unbalanced quotes are not removed.
   */
  @Test
  public void unbalancedQuotesKept() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt = "Due to complexity\" I'll skip this feature.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    }
  }

  /**
   * Verifies session tracking prevents duplicate notifications.
   */
  @Test
  public void sessionTrackingPreventsDuplicates() throws IOException
  {
    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
    Clock clock1 = Clock.fixed(baseTime, ZoneOffset.UTC);
    Clock clock2 = Clock.fixed(baseTime.plusSeconds(2), ZoneOffset.UTC);

    DetectGivingUp handler1 = new DetectGivingUp(clock1);
    String prompt = "Given the complexity, I'll skip this.";
    try (TestSession session = createTestSession())
    {
      String result1 = handler1.check(prompt, session.sessionId());
      requireThat(result1, "result1").isNotEmpty();

      DetectGivingUp handler2 = new DetectGivingUp(clock2);
      String result2 = handler2.check(prompt, session.sessionId());
      requireThat(result2, "result2").isEmpty();
    }
  }

  /**
   * Verifies rate limiting works correctly.
   */
  @Test
  public void rateLimitingWorks() throws IOException
  {
    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
    Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
    DetectGivingUp handler = new DetectGivingUp(clock);
    try (TestSession session = createTestSession())
    {
      String result1 = handler.check(
        "Given the complexity, I'll skip this.", session.sessionId());
      requireThat(result1, "result1").isNotEmpty();

      String result2 = handler.check(
        "Due to time constraints, let me simplify.", session.sessionId());
      requireThat(result2, "result2").isEmpty();
    }
  }

  /**
   * Verifies constraint keyword detection.
   */
  @Test
  public void detectsConstraintKeywords() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();

    String[] prompts = {
      "Due to time constraints, I'll defer this.",
      "The token budget is too high, so I'll skip this.",
      "Given the difficult nature of this, let me simplify.",
      "The volume of changes needed makes me " +
        "recommend a different approach."
    };

    for (int i = 0; i < prompts.length; ++i)
    {
      try (TestSession session = createTestSession())
      {
        String result = handler.check(prompts[i], session.sessionId());
        requireThat(result, "result" + i).isNotEmpty();
      }
    }
  }

  /**
   * Verifies abandonment action keyword detection.
   */
  @Test
  public void detectsAbandonmentActions() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();

    String[] prompts = {
      "Due to complexity, I'll skip the advanced features.",
      "Given the difficulty, let me simplify this.",
      "The volume is high, so I recommend a different approach.",
      "Time constraints mean I need to defer this."
    };

    for (int i = 0; i < prompts.length; ++i)
    {
      try (TestSession session = createTestSession())
      {
        String result = handler.check(prompts[i], session.sessionId());
        requireThat(result, "result" + i).isNotEmpty();
      }
    }
  }

  /**
   * Verifies MVP rationalization is detected.
   */
  @Test
  public void detectsMvpRationalization() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Due to token constraints, I'll create a solid MVP " +
        "instead of the full implementation.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("GIVING UP PATTERN DETECTED");
    }
  }

  /**
   * Verifies removing exception handler is detected.
   */
  @Test
  public void detectsExceptionHandlerRemoval() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "To fix the compilation error, I'll remove the exception handler.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("CODE DISABLING ANTI-PATTERN DETECTED");
    }
  }

  /**
   * Verifies numbered options with permission language is detected.
   */
  @Test
  public void detectsNumberedOptionsWithPermission() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Would you like to: 1. Continue with this approach " +
        "2. Try a different method?";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("PROTOCOL VIOLATION DETECTED");
    }
  }

  /**
   * Verifies prompt length limit is enforced.
   */
  @Test
  public void promptLengthLimitEnforced() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String longPrompt =
      "X".repeat(150_000) + " Given the complexity, I'll skip this.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(longPrompt, session.sessionId());
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies session ID with only special characters is rejected.
   */
  @Test
  public void sessionIdWithOnlySpecialCharsRejected()
  {
    DetectGivingUp handler = new DetectGivingUp();
    String result = handler.check(
      "Given the complexity, I'll skip this.", "../../../");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies module not found abandonment is detected.
   */
  @Test
  public void detectsModuleNotFoundAbandonment() throws IOException
  {
    DetectGivingUp handler = new DetectGivingUp();
    String prompt =
      "Module not found error persists with this complexity, " +
        "so let me simplify by removing this dependency.";
    try (TestSession session = createTestSession())
    {
      String result = handler.check(prompt, session.sessionId());
      requireThat(result, "result").contains("COMPILATION DEBUGGING ABANDONMENT DETECTED");
    }
  }

  /**
   * Verifies rate limit allows execution at exact boundary (timeDiff == 1 second).
   */
  @Test
  public void rateLimitAllowsAtExactBoundary() throws IOException
  {
    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
    long baseEpochSeconds = baseTime.getEpochSecond();
    Clock clock = Clock.fixed(baseTime.plusSeconds(1), ZoneOffset.UTC);

    try (TestSession session = createTestSession())
    {
      // Manually create session dir and rate limit file at baseTime
      Files.createDirectories(session.sessionDir());
      Files.writeString(
        session.sessionDir().resolve(".rate_limit_giving_up"),
        String.valueOf(baseEpochSeconds));

      // Clock is 1 second later - timeDiff == 1, which is NOT < 1, so should pass
      DetectGivingUp handler = new DetectGivingUp(clock);
      String result = handler.check(
        "Given the complexity, I'll skip this.", session.sessionId());
      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies rate limit blocks execution within boundary (timeDiff == 0).
   */
  @Test
  public void rateLimitBlocksWithinBoundary() throws IOException
  {
    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
    long baseEpochSeconds = baseTime.getEpochSecond();
    Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

    try (TestSession session = createTestSession())
    {
      // Manually create session dir and rate limit file at same time
      Files.createDirectories(session.sessionDir());
      Files.writeString(
        session.sessionDir().resolve(".rate_limit_giving_up"),
        String.valueOf(baseEpochSeconds));

      // Clock is same second - timeDiff == 0, which IS < 1, so should block
      DetectGivingUp handler = new DetectGivingUp(clock);
      String result = handler.check(
        "Given the complexity, I'll skip this.", session.sessionId());
      requireThat(result, "result").isEmpty();
    }
  }

  /**
   * Verifies stale session directories older than 7 days are cleaned up.
   */
  @Test
  public void cleansUpStaleSessionDirs() throws IOException
  {
    Instant now = Instant.parse("2025-01-15T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    // Create a stale session dir (8 days old)
    Path staleDir = Path.of("/tmp", "claude-hooks-session-stale-test-" + System.nanoTime());
    Files.createDirectories(staleDir);
    Files.writeString(staleDir.resolve("marker"), "test");
    Instant eightDaysAgo = now.minus(Duration.ofDays(8));
    Files.setLastModifiedTime(staleDir,
      FileTime.from(eightDaysAgo));

    try (TestSession session = createTestSession())
    {
      // Trigger detection (which triggers cleanup)
      DetectGivingUp handler = new DetectGivingUp(clock);
      handler.check(
        "Given the complexity, I'll skip this.", session.sessionId());

      // Stale dir should be cleaned up
      requireThat(Files.exists(staleDir), "staleDirExists").isFalse();
    }
    finally
    {
      // Cleanup in case test fails
      if (Files.exists(staleDir))
      {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(staleDir))
        {
          for (Path entry : stream)
            Files.delete(entry);
        }
        Files.delete(staleDir);
      }
    }
  }

  /**
   * Verifies recent session directories are preserved during cleanup.
   */
  @Test
  public void preservesFreshSessionDirs() throws IOException
  {
    Instant now = Instant.parse("2025-01-15T00:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    // Create a fresh session dir (1 day old)
    Path freshDir = Path.of("/tmp", "claude-hooks-session-fresh-test-" + System.nanoTime());
    Files.createDirectories(freshDir);
    Files.writeString(freshDir.resolve("marker"), "test");
    Instant oneDayAgo = now.minus(Duration.ofDays(1));
    Files.setLastModifiedTime(freshDir,
      FileTime.from(oneDayAgo));

    try (TestSession session = createTestSession())
    {
      // Trigger detection (which triggers cleanup)
      DetectGivingUp handler = new DetectGivingUp(clock);
      handler.check(
        "Given the complexity, I'll skip this.", session.sessionId());

      // Fresh dir should NOT be cleaned up
      requireThat(Files.exists(freshDir), "freshDirExists").isTrue();
    }
    finally
    {
      // Always clean up test dir
      if (Files.exists(freshDir))
      {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(freshDir))
        {
          for (Path entry : stream)
            Files.delete(entry);
        }
        Files.delete(freshDir);
      }
    }
  }

  /**
   * Creates a test session with a unique ID and auto-cleanup.
   *
   * @return a new TestSession that cleans up on close
   */
  private TestSession createTestSession()
  {
    String sessionId = "test-" + System.nanoTime();
    Path sessionDir = Path.of("/tmp", "claude-hooks-session-" + sessionId);
    return new TestSession(sessionId, sessionDir);
  }
}

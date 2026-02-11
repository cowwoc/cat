package io.github.cowwoc.cat.hooks.read.pre;

import io.github.cowwoc.cat.hooks.ReadHandler;
import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tracks sequential Read/Glob/Grep operations and suggests batching.
 * <p>
 * Advisory only - never blocks operations.
 */
public final class PredictBatchOpportunity implements ReadHandler
{
  private static final int WINDOW_SECONDS = 30;
  private static final int THRESHOLD = 3;
  private static final int WARNING_COOLDOWN = 60;
  private static final Set<String> SUPPORTED_TOOLS = Set.of("Read", "Glob", "Grep");

  private final JsonMapper mapper;

  /**
   * Creates a new batch opportunity predictor.
   *
   * @param mapper the JSON mapper to use for state serialization
   * @throws NullPointerException if mapper is null
   */
  public PredictBatchOpportunity(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  @Override
  public Result check(String toolName, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    if (!SUPPORTED_TOOLS.contains(toolName))
      return Result.allow();

    String filePath = "";
    if (toolInput != null)
    {
      JsonNode pathNode = toolInput.get("file_path");
      if (pathNode == null)
        pathNode = toolInput.get("path");
      if (pathNode == null)
        pathNode = toolInput.get("pattern");
      if (pathNode != null && pathNode.isString())
      {
        String value = pathNode.asString();
        if (value != null)
          filePath = value;
      }
    }

    int timestamp = (int) (System.currentTimeMillis() / 1000);
    Path trackerFile = Path.of("/tmp/batch_tracker_" + sessionId + ".json");
    TrackerState state = loadTracker(trackerFile);

    // Add this operation
    List<Operation> operations = new ArrayList<>(state.operations());
    operations.add(new Operation(toolName, filePath, timestamp));

    // Clean old operations
    int cutoff = timestamp - WINDOW_SECONDS;
    operations.removeIf(op -> op.timestamp() < cutoff);

    int recentCount = operations.size();
    int timeSinceWarning = timestamp - state.lastWarning();

    // Check if we should warn
    if (recentCount >= THRESHOLD && timeSinceWarning > WARNING_COOLDOWN)
    {
      TrackerState newState = new TrackerState(operations, state.warningsShown() + 1, timestamp);
      saveTracker(trackerFile, newState);

      StringBuilder recentPaths = new StringBuilder();
      for (Operation op : operations)
      {
        if (!recentPaths.isEmpty())
          recentPaths.append(", ");
        String pathToAppend;
        if (op.path().isEmpty())
          pathToAppend = "(unknown)";
        else
          pathToAppend = op.path();
        recentPaths.append(pathToAppend);
      }

      return Result.warn(buildWarning(recentCount, toolName, WINDOW_SECONDS, recentPaths.toString()));
    }

    // Update state
    saveTracker(trackerFile, new TrackerState(operations, state.warningsShown(), state.lastWarning()));
    return Result.allow();
  }

  /**
   * An operation record.
   *
   * @param tool the tool name
   * @param path the file path or pattern
   * @param timestamp the Unix timestamp
   */
  private record Operation(String tool, String path, int timestamp)
  {
    /**
     * Creates an operation record.
     *
     * @param tool the tool name
     * @param path the file path or pattern
     * @param timestamp the Unix timestamp
     */
    public Operation
    {
      // Record validation
    }
  }

  /**
   * Tracker state record.
   *
   * @param operations list of recent operations
   * @param warningsShown count of warnings shown
   * @param lastWarning Unix timestamp of last warning
   */
  private record TrackerState(List<Operation> operations, int warningsShown, int lastWarning)
  {
    /**
     * Creates a tracker state.
     *
     * @param operations list of recent operations
     * @param warningsShown count of warnings shown
     * @param lastWarning Unix timestamp of last warning
     */
    public TrackerState
    {
      // Record validation
    }
  }

  /**
   * Loads tracker state from file.
   *
   * @param trackerFile the tracker file path
   * @return the loaded state or default state
   */
  private TrackerState loadTracker(Path trackerFile)
  {
    if (!Files.exists(trackerFile))
      return new TrackerState(List.of(), 0, 0);
    try
    {
      JsonNode node = mapper.readTree(Files.readString(trackerFile));
      List<Operation> ops = new ArrayList<>();
      JsonNode opsNode = node.get("operations");
      if (opsNode != null && opsNode.isArray())
      {
        for (JsonNode opNode : opsNode)
        {
          String tool = "";
          String path = "";
          int ts = 0;
          if (opNode.get("tool") != null)
          {
            String v = opNode.get("tool").asString();
            if (v != null)
              tool = v;
          }
          if (opNode.get("path") != null)
          {
            String v = opNode.get("path").asString();
            if (v != null)
              path = v;
          }
          if (opNode.get("timestamp") != null)
            ts = opNode.get("timestamp").asInt();
          ops.add(new Operation(tool, path, ts));
        }
      }
      int warningsShown = 0;
      if (node.get("warnings_shown") != null)
        warningsShown = node.get("warnings_shown").asInt();
      int lastWarning = 0;
      if (node.get("last_warning") != null)
        lastWarning = node.get("last_warning").asInt();
      return new TrackerState(ops, warningsShown, lastWarning);
    }
    catch (IOException _)
    {
      return new TrackerState(List.of(), 0, 0);
    }
  }

  /**
   * Saves tracker state to file.
   *
   * @param trackerFile the tracker file path
   * @param state the state to save
   */
  private void saveTracker(Path trackerFile, TrackerState state)
  {
    try
    {
      ObjectNode node = mapper.createObjectNode();
      ArrayNode opsNode = mapper.createArrayNode();
      for (Operation op : state.operations())
      {
        ObjectNode opNode = mapper.createObjectNode();
        opNode.put("tool", op.tool());
        opNode.put("path", op.path());
        opNode.put("timestamp", op.timestamp());
        opsNode.add(opNode);
      }
      node.set("operations", opsNode);
      node.put("warnings_shown", state.warningsShown());
      node.put("last_warning", state.lastWarning());
      Files.writeString(trackerFile, mapper.writeValueAsString(node));
    }
    catch (IOException _)
    {
      // Ignore
    }
  }

  /**
   * Builds the warning message.
   *
   * @param count the operation count
   * @param toolName the tool name
   * @param windowSeconds the time window
   * @param recentPaths the recent paths
   * @return the warning message
   */
  private String buildWarning(int count, String toolName, int windowSeconds, String recentPaths)
  {
    return "\nBATCH OPPORTUNITY DETECTED\n\n" +
      count + " sequential " + toolName + " operations in the last " + windowSeconds + "s.\n\n" +
      "Recent targets: " + recentPaths + "\n\n" +
      "CONSIDER BATCHING:\n" +
      "  - Use parallel tool calls in a single message\n" +
      "  - Use Glob with pattern matching instead of multiple Reads\n" +
      "  - Use Grep with broader scope instead of multiple searches\n" +
      "  - Use the batch-read skill for coordinated file reading\n\n" +
      "EFFICIENCY TIP:\n" +
      "  Instead of:  Read file1, Read file2, Read file3 (sequential)\n" +
      "  Use:         Read file1 + Read file2 + Read file3 (parallel in one message)\n";
  }
}

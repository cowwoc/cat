/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

/**
 * A segment of an assistant turn consisting of a text block and the file paths of adjacent tool_use blocks.
 * <p>
 * Each text block in an assistant message may be adjacent to tool_use blocks at the immediately preceding
 * (i-1) or immediately following (i+1) index positions. The file paths extracted from those adjacent
 * tool_use blocks are stored here to enable context-aware giving-up detection.
 * <p>
 * A pure-text segment (from a turn with no tool_use blocks at all) has both file-path fields set to
 * {@code null}.
 *
 * @param text          the text content of the block
 * @param aboveFilePath the file path extracted from the tool_use block immediately preceding this text
 *                      block, or {@code null} if there is no such block or it has no recognizable file path
 * @param belowFilePath the file path extracted from the tool_use block immediately following this text
 *                      block, or {@code null} if there is no such block or it has no recognizable file path
 */
public record TurnSegment(String text, String aboveFilePath, String belowFilePath)
{
}

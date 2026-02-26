/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.util.IssueDiscovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides cross-module access to private methods of {@link IssueDiscovery}.
 * <p>
 * Part of the SharedSecrets mechanism that enables tests to invoke private methods without using
 * reflection.
 */
@FunctionalInterface
public interface IssueDiscoveryAccess
{
  /**
   * Parses the status field from STATE.md lines and validates it against canonical values.
   *
   * @param lines the lines from the STATE.md file
   * @param statePath the path to the STATE.md file (used in error messages only)
   * @return the validated status string
   * @throws IOException if the status field is missing or the status value is non-canonical
   */
  String getIssueStatus(List<String> lines, Path statePath) throws IOException;
}

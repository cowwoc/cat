/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.io.IOException;

/**
 * Opens a URL in the user's default browser.
 */
@FunctionalInterface
public interface BrowserOpener
{
  /**
   * Opens the given URL.
   *
   * @param url the URL to open
   * @throws IOException if the browser cannot be opened
   */
  void open(String url) throws IOException;
}

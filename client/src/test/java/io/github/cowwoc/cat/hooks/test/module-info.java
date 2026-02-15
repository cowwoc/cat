/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
module io.github.cowwoc.cat.hooks.test
{
  requires io.github.cowwoc.cat.hooks;
  requires org.testng;
  requires io.github.cowwoc.requirements13.java;
  requires tools.jackson.core;
  requires tools.jackson.databind;
  requires io.github.cowwoc.pouch10.core;

  exports io.github.cowwoc.cat.hooks.test to org.testng, io.github.cowwoc.cat.hooks;
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
module io.github.cowwoc.cat.common.cli
{
  requires tools.jackson.databind;
  requires tools.jackson.dataformat.yaml;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.requirements13.jackson;
  requires io.github.cowwoc.pouch10.core;
  requires io.github.javadiffutils;
  requires jtokkit;
  requires java.net.http;
  requires org.slf4j;
  requires ch.qos.logback.classic;

  exports io.github.cowwoc.cat.agent;
  exports io.github.cowwoc.cat.engine;
  exports io.github.cowwoc.cat.hook;
  exports io.github.cowwoc.cat.hook.bash;
  exports io.github.cowwoc.cat.tool;
  exports io.github.cowwoc.cat.tool.licensing;
  exports io.github.cowwoc.cat.tool.skills;
  exports io.github.cowwoc.cat.tool.util;
}

/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
module io.github.cowwoc.cat.client.claude
{
  requires tools.jackson.databind;
  requires tools.jackson.dataformat.yaml;
  requires io.github.cowwoc.requirements13.java;
  requires io.github.cowwoc.requirements13.jackson;
  requires io.github.cowwoc.pouch10.core;
  requires jtokkit;
  requires io.github.javadiffutils;
  requires java.net.http;
  requires org.slf4j;
  requires ch.qos.logback.classic;

  exports io.github.cowwoc.cat.claude.tool;
  exports io.github.cowwoc.cat.claude.tool.post;
  exports io.github.cowwoc.cat.claude.hook;
  exports io.github.cowwoc.cat.claude.hook.ask;
  exports io.github.cowwoc.cat.claude.hook.bash;
  exports io.github.cowwoc.cat.claude.hook.bash.post;
  exports io.github.cowwoc.cat.claude.hook.edit;
  exports io.github.cowwoc.cat.claude.hook.failure;
  exports io.github.cowwoc.cat.claude.hook.licensing;
  exports io.github.cowwoc.cat.claude.hook.prompt;
  exports io.github.cowwoc.cat.claude.hook.read.post;
  exports io.github.cowwoc.cat.claude.hook.read.pre;
  exports io.github.cowwoc.cat.claude.hook.session;
  exports io.github.cowwoc.cat.claude.hook.skills;
  exports io.github.cowwoc.cat.claude.hook.task;
  exports io.github.cowwoc.cat.claude.hook.util;
  exports io.github.cowwoc.cat.claude.hook.write;
  exports io.github.cowwoc.cat.claude.internal to io.github.cowwoc.cat.client.claude.test;
  opens io.github.cowwoc.cat.claude.hook.skills to tools.jackson.databind;
  opens io.github.cowwoc.cat.claude.hook to io.github.cowwoc.cat.client.claude.test;
  opens io.github.cowwoc.cat.claude.hook.util to io.github.cowwoc.cat.client.claude.test;
}

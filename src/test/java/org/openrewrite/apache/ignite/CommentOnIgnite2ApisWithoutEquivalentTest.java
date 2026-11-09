/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.ignite;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CommentOnIgnite2ApisWithoutEquivalentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CommentOnIgnite2ApisWithoutEquivalent())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2"));
    }

    @DocumentExample
    @Test
    void commentAboveStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteDataStreamer;

              class Loader {
                  void load(Ignite ignite) {
                      int loaded = 0;

                      try (IgniteDataStreamer<Long, String> streamer = ignite.dataStreamer("people")) {
                          streamer.addData(1L, "Ada");
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteDataStreamer;

              class Loader {
                  void load(Ignite ignite) {
                      int loaded = 0;

                      // TODO Ignite 3: stream data into a table with KeyValueView.streamData(Flow.Publisher, DataStreamerOptions) or RecordView.streamData(...).
                      try (IgniteDataStreamer<Long, String> streamer = ignite.dataStreamer("people")) {
                          streamer.addData(1L, "Ada");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameTodoOnceAndChainedCallsOnce() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteCache;

              class Stats {
                  int total(IgniteCache<String, String> a, IgniteCache<String, String> b) {
                      return a.size() + b.size();
                  }

                  Object binary(IgniteCache<String, String> cache) {
                      return cache.withKeepBinary().get("k");
                  }
              }
              """,
            """
              import org.apache.ignite.IgniteCache;

              class Stats {
                  int total(IgniteCache<String, String> a, IgniteCache<String, String> b) {
                      // TODO Ignite 3: KeyValueView has no size(); count the rows with SELECT COUNT(*) through ignite.sql().
                      return a.size() + b.size();
                  }

                  Object binary(IgniteCache<String, String> cache) {
                      // TODO Ignite 3: there are no binary objects; table.keyValueView() reads and writes the columns as a KeyValueView<Tuple, Tuple>.
                      return cache.withKeepBinary().get("k");
                  }
              }
              """
          )
        );
    }

    @Test
    void existingCommentsAreKept() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;

              class Counters {
                  long next(Ignite ignite) {
                      // one sequence for the whole cluster
                      return ignite.atomicSequence("ids", 0, true).incrementAndGet();
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;

              class Counters {
                  long next(Ignite ignite) {
                      // one sequence for the whole cluster
                      // TODO Ignite 3: has no equivalent of this Ignite 2 API.
                      return ignite.atomicSequence("ids", 0, true).incrementAndGet();
                  }
              }
              """
          )
        );
    }

    @Test
    void cacheOperationsWithEquivalentAreNotCommented() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteCache;

              class Store {
                  void save(IgniteCache<String, String> cache) {
                      cache.put("k", "v");
                      cache.clear();
                  }
              }
              """
          )
        );
    }
}

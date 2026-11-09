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

    @Test
    void cacheMethodsWithoutEquivalentGetComments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteCache;
              import org.apache.ignite.client.ClientCache;

              import javax.cache.Cache;
              import javax.cache.expiry.ExpiryPolicy;
              import java.util.Map;
              import java.util.Set;

              class Store {
                  void scan(IgniteCache<Long, String> cache) {
                      for (Cache.Entry<Long, String> e : cache) {
                          System.out.println(e.getValue());
                      }
                      System.out.println(cache.getName());
                  }

                  void expire(ClientCache<Long, String> cache, ExpiryPolicy policy) {
                      cache.withExpirePolicy(policy).put(1L, "a");
                  }

                  Map<Long, String> read(IgniteCache<Long, String> cache, Set<Long> keys) {
                      return cache.getAllOutTx(keys);
                  }
              }
              """,
            """
              import org.apache.ignite.IgniteCache;
              import org.apache.ignite.client.ClientCache;

              import javax.cache.Cache;
              import javax.cache.expiry.ExpiryPolicy;
              import java.util.Map;
              import java.util.Set;

              class Store {
                  void scan(IgniteCache<Long, String> cache) {
                      // TODO Ignite 3: a KeyValueView cannot be iterated; read its entries with query(tx, criteria), which returns a Cursor of Map.Entry, or with SELECT through ignite.sql().
                      for (Cache.Entry<Long, String> e : cache) {
                          System.out.println(e.getValue());
                      }
                      // TODO Ignite 3: KeyValueView has no equivalent of this cache operation.
                      System.out.println(cache.getName());
                  }

                  void expire(ClientCache<Long, String> cache, ExpiryPolicy policy) {
                      // TODO Ignite 3: KeyValueView has no equivalent of this cache operation.
                      cache.withExpirePolicy(policy).put(1L, "a");
                  }

                  Map<Long, String> read(IgniteCache<Long, String> cache, Set<Long> keys) {
                      return cache.getAllOutTx(keys);
                  }
              }
              """
          )
        );
    }

    @Test
    void transactionApisGetComments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;
              import org.apache.ignite.transactions.TransactionIsolation;
              import org.apache.ignite.transactions.TransactionOptimisticException;

              import static org.apache.ignite.transactions.TransactionConcurrency.OPTIMISTIC;

              class Transfer {
                  static final TransactionIsolation ISOLATION = TransactionIsolation.SERIALIZABLE;

                  void transfer(Ignite ignite) {
                      try (Transaction tx = ignite.transactions().txStart(OPTIMISTIC, ISOLATION)) {
                          tx.setRollbackOnly();
                          tx.commit();
                      } catch (TransactionOptimisticException e) {
                          System.out.println(ignite.transactions().tx());
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;
              import org.apache.ignite.transactions.TransactionIsolation;
              import org.apache.ignite.transactions.TransactionOptimisticException;

              import static org.apache.ignite.transactions.TransactionConcurrency.OPTIMISTIC;

              class Transfer {
                  // TODO Ignite 3: transactions have no concurrency or isolation modes; TransactionOptions sets only a timeout and whether the transaction is read-only.
                  static final TransactionIsolation ISOLATION = TransactionIsolation.SERIALIZABLE;

                  void transfer(Ignite ignite) {
                      // TODO Ignite 3: transactions throw org.apache.ignite.tx.TransactionException, with no subclasses for optimistic conflicts, timeouts, rollbacks, heuristic outcomes or deadlocks.
                      try (Transaction tx = ignite.transactions().txStart(OPTIMISTIC, ISOLATION)) {
                          // TODO Ignite 3: a Transaction only commits or rolls back, with commit(), rollback(), and commitAsync() and rollbackAsync(), which return a CompletableFuture; it has no state, label, timeout or rollback-only flag, and cannot be suspended.
                          tx.setRollbackOnly();
                          tx.commit();
                      } catch (TransactionOptimisticException e) {
                          // TODO Ignite 3: IgniteTransactions only begins transactions, with begin(TransactionOptions), and runs code in one with runInTransaction(...); there is no current transaction of the thread, and no labels, tracing or metrics.
                          System.out.println(ignite.transactions().tx());
                      }
                  }
              }
              """
          )
        );
    }
}

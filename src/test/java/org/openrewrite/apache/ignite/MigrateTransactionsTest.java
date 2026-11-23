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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class MigrateTransactionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTransactions())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2")
            //language=java
            .dependsOn(
              """
                package com.acme;
                public class Ledger {
                    public void record(Object tx, String entry) {}
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void tryWithResourcesBecomesTryFinally() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          ledger.record(tx, "transfer");
                          tx.commit();
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      Transaction tx = ignite.transactions().begin();
                      try {
                          ledger.record(tx, "transfer");
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void transactionNotPassedOnGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              import static org.apache.ignite.transactions.TransactionConcurrency.PESSIMISTIC;
              import static org.apache.ignite.transactions.TransactionIsolation.REPEATABLE_READ;

              class Transfer {
                  void transfer(Ignite ignite, Runnable work) {
                      try (Transaction tx = ignite.transactions().txStart(PESSIMISTIC, REPEATABLE_READ)) {
                          work.run();
                          tx.commit();
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Runnable work) {
                      // TODO Ignite 3: transactions are no longer bound to the thread. Pass tx as the first argument of each table and SQL operation that should run in it, including those in methods called from here.
                      Transaction tx = ignite.transactions().begin();
                      try {
                          work.run();
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void timeoutBecomesTransactionOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.IgniteTransactions;
              import org.apache.ignite.transactions.Transaction;
              import org.apache.ignite.transactions.TransactionConcurrency;
              import org.apache.ignite.transactions.TransactionIsolation;

              class Transfer {
                  void transfer(IgniteTransactions txs, Ledger ledger) {
                      Transaction tx = txs.txStart(TransactionConcurrency.OPTIMISTIC, TransactionIsolation.SERIALIZABLE, 5000, 0);
                      try {
                          ledger.record(tx, "transfer");
                          tx.commit();
                      } finally {
                          tx.close();
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.tx.IgniteTransactions;
              import org.apache.ignite.tx.Transaction;
              import org.apache.ignite.tx.TransactionOptions;

              class Transfer {
                  void transfer(IgniteTransactions txs, Ledger ledger) {
                      Transaction tx = txs.begin(new TransactionOptions().timeoutMillis(5000));
                      try {
                          ledger.record(tx, "transfer");
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void catchClausesKeepTheirOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.client.ClientTransaction;
              import org.apache.ignite.client.IgniteClient;

              class Transfer {
                  void transfer(IgniteClient client, Ledger ledger) {
                      try (ClientTransaction tx = client.transactions().txStart()) {
                          ledger.record(tx, "transfer");
                          tx.commit();
                      } catch (RuntimeException e) {
                          ledger.record(null, "failed");
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(IgniteClient client, Ledger ledger) {
                      try {
                          Transaction tx = client.transactions().begin();
                          try {
                              ledger.record(tx, "transfer");
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      } catch (RuntimeException e) {
                          ledger.record(null, "failed");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameNameInBlockGetsBlocksOfTheirOwn() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          ledger.record(tx, "first");
                          tx.commit();
                      }
                      try (Transaction tx = ignite.transactions().txStart()) {
                          ledger.record(tx, "second");
                          tx.commit();
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      {
                          Transaction tx = ignite.transactions().begin();
                          try {
                              ledger.record(tx, "first");
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      }
                      {
                          Transaction tx = ignite.transactions().begin();
                          try {
                              ledger.record(tx, "second");
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameNameInLaterLoopGetsBlockOfItsOwn() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              import java.util.List;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, List<String> items) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          ledger.record(tx, "all");
                          tx.commit();
                      }
                      for (String item : items) {
                          try (Transaction tx = ignite.transactions().txStart()) {
                              ledger.record(tx, item);
                              tx.commit();
                          }
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              import java.util.List;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, List<String> items) {
                      {
                          Transaction tx = ignite.transactions().begin();
                          try {
                              ledger.record(tx, "all");
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      }
                      for (String item : items) {
                          Transaction tx = ignite.transactions().begin();
                          try {
                              ledger.record(tx, item);
                              tx.commit();
                          } finally {
                              tx.rollback();
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void methodCalledInTransactionGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          ledger.record(tx, "debit");
                          credit(ledger);
                          tx.commit();
                      }
                  }

                  void credit(Ledger ledger) {
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      // TODO Ignite 3: transactions are no longer bound to the thread. Pass tx as the first argument of each table and SQL operation that should run in it, including those in methods called from here.
                      Transaction tx = ignite.transactions().begin();
                      try {
                          ledger.record(tx, "debit");
                          credit(ledger);
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }

                  void credit(Ledger ledger) {
                  }
              }
              """
          )
        );
    }

    @Test
    void jdkAndLoggingCallsNeedNoComment() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              import java.util.List;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, List<String> entries) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          entries.forEach(entry -> ledger.record(tx, entry.trim()));
                          System.out.println("recorded " + entries.size());
                          tx.commit();
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              import java.util.List;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, List<String> entries) {
                      Transaction tx = ignite.transactions().begin();
                      try {
                          entries.forEach(entry -> ledger.record(tx, entry.trim()));
                          System.out.println("recorded " + entries.size());
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void transactionInLocalVariableGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Runnable work) {
                      Transaction tx = ignite.transactions().txStart();
                      try {
                          work.run();
                          tx.commit();
                      } finally {
                          tx.close();
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Runnable work) {
                      // TODO Ignite 3: transactions are no longer bound to the thread. Pass tx as the first argument of each table and SQL operation that should run in it, including those in methods called from here.
                      Transaction tx = ignite.transactions().begin();
                      try {
                          work.run();
                          tx.commit();
                      } finally {
                          tx.rollback();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void transactionAmongSeveralResourcesGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              import java.io.StringReader;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      try (Transaction tx = ignite.transactions().txStart(); StringReader reader = new StringReader("x")) {
                          ledger.record(tx, "read");
                          tx.commit();
                      }
                  }

                  void again(Transaction tx, Ledger ledger) {
                      try (tx) {
                          ledger.record(tx, "again");
                          tx.commit();
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              import java.io.StringReader;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger) {
                      // TODO Ignite 3: Transaction is not AutoCloseable. Begin it before the try block and roll it back in its finally block.
                      try (Transaction tx = ignite.transactions().begin(); StringReader reader = new StringReader("x")) {
                          ledger.record(tx, "read");
                          tx.commit();
                      }
                  }

                  void again(Transaction tx, Ledger ledger) {
                      // TODO Ignite 3: Transaction is not AutoCloseable. Begin it before the try block and roll it back in its finally block.
                      try (tx) {
                          ledger.record(tx, "again");
                          tx.commit();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryWithResourcesOutsideBlockGetsComment() {
        rewriteRun(
          //language=java
          java(
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, int kind) {
                      switch (kind) {
                          case 1:
                              try (Transaction tx = ignite.transactions().txStart()) {
                                  ledger.record(tx, "one");
                                  tx.commit();
                              }
                              break;
                          default:
                              break;
                      }
                  }
              }
              """,
            """
              import com.acme.Ledger;
              import org.apache.ignite.Ignite;
              import org.apache.ignite.tx.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, Ledger ledger, int kind) {
                      switch (kind) {
                          case 1:
                              // TODO Ignite 3: Transaction is not AutoCloseable. Begin it before the try block and roll it back in its finally block.
                              try (Transaction tx = ignite.transactions().begin()) {
                                  ledger.record(tx, "one");
                                  tx.commit();
                              }
                              break;
                          default:
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinIsLeftAlone() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2")),
          //language=kotlin
          kotlin(
            """
              import org.apache.ignite.Ignite

              fun transfer(ignite: Ignite) {
                  ignite.transactions().txStart().use { tx ->
                      tx.commit()
                  }
              }
              """
          )
        );
    }
}

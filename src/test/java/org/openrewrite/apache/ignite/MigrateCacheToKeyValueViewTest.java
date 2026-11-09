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

class MigrateCacheToKeyValueViewTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateCacheToKeyValueView())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2"));
    }

    @DocumentExample
    @Test
    void cacheBecomesKeyValueView() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteCache;

              import java.util.Set;

              class PersonStore {
                  private final IgniteCache<Long, String> cache;

                  PersonStore(Ignite ignite) {
                      cache = ignite.cache("Person");
                  }

                  String find(Long id) {
                      return cache.get(id);
                  }

                  void save(Long id, String name) {
                      cache.put(id, name);
                  }

                  boolean exists(Long id) {
                      return cache.containsKey(id);
                  }

                  void delete(Long id) {
                      cache.remove(id);
                  }

                  void deleteSome(Set<Long> ids) {
                      cache.removeAll(ids);
                  }

                  void deleteAll() {
                      cache.clear();
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.lang.util.IgniteNameUtils;
              import org.apache.ignite.table.KeyValueView;

              import java.util.Set;

              class PersonStore {
                  private final KeyValueView<Long, String> cache;

                  PersonStore(Ignite ignite) {
                      cache = ignite.tables().table(IgniteNameUtils.quoteIfNeeded("Person")).keyValueView(Long.class, String.class);
                  }

                  String find(Long id) {
                      return cache.get(null, id);
                  }

                  void save(Long id, String name) {
                      cache.put(null, id, name);
                  }

                  boolean exists(Long id) {
                      return cache.contains(null, id);
                  }

                  void delete(Long id) {
                      cache.remove(null, id);
                  }

                  void deleteSome(Set<Long> ids) {
                      cache.removeAll(null, ids);
                  }

                  void deleteAll() {
                      cache.removeAll(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void valueClassOfTheApplication() {
        rewriteRun(
          //language=java
          java(
            """
              package com.acme;

              public class Person {
                  String name;
              }
              """
          ),
          //language=java
          java(
            """
              package com.acme;

              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteCache;

              class PersonStore {
                  private final IgniteCache<Long, Person> cache;

                  PersonStore(Ignite ignite) {
                      this.cache = ignite.getOrCreateCache("PERSON");
                  }

                  Person getAndReplace(Long id, Person person) {
                      return cache.getAndReplace(id, person);
                  }
              }
              """,
            """
              package com.acme;

              import org.apache.ignite.Ignite;
              import org.apache.ignite.table.KeyValueView;

              class PersonStore {
                  private final KeyValueView<Long, Person> cache;

                  PersonStore(Ignite ignite) {
                      this.cache = ignite.tables().table("PERSON").keyValueView(Long.class, Person.class);
                  }

                  Person getAndReplace(Long id, Person person) {
                      return cache.getAndReplace(null, id, person);
                  }
              }
              """
          )
        );
    }

    @Test
    void cacheNameFromConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.client.ClientCache;
              import org.apache.ignite.client.IgniteClient;

              class Accounts {
                  static final String CACHE = "accounts";

                  ClientCache<String, Double> balances(IgniteClient client) {
                      return client.cache(CACHE);
                  }
              }
              """,
            """
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.lang.util.IgniteNameUtils;
              import org.apache.ignite.table.KeyValueView;

              class Accounts {
                  static final String CACHE = "accounts";

                  KeyValueView<String, Double> balances(IgniteClient client) {
                      return client.tables().table(IgniteNameUtils.quoteIfNeeded(CACHE)).keyValueView(String.class, Double.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void operationsInTransactionTakeIt() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteCache;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, IgniteCache<String, Double> cache) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          Double from = cache.get("from");
                          cache.put("to", from);
                          tx.commit();
                          cache.put("audit", from);
                      }
                      cache.put("done", 1.0);
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.table.KeyValueView;
              import org.apache.ignite.transactions.Transaction;

              class Transfer {
                  void transfer(Ignite ignite, KeyValueView<String, Double> cache) {
                      try (Transaction tx = ignite.transactions().txStart()) {
                          Double from = cache.get(tx, "from");
                          cache.put(tx, "to", from);
                          tx.commit();
                          cache.put(null, "audit", from);
                      }
                      cache.put(null, "done", 1.0);
                  }
              }
              """
          )
        );
    }

    @Test
    void operationsAfterTransactionDeclaredInBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.client.ClientCache;
              import org.apache.ignite.client.ClientTransaction;
              import org.apache.ignite.client.IgniteClient;

              import java.util.Map;

              class Loader {
                  void load(IgniteClient client, ClientCache<Integer, String> cache, Map<Integer, String> rows) {
                      ClientTransaction tx = client.transactions().txStart();
                      try {
                          cache.putAll(rows);
                          rows.forEach((k, v) -> cache.putIfAbsent(k, v));
                          tx.commit();
                      } finally {
                          tx.close();
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.client.ClientTransaction;
              import org.apache.ignite.client.IgniteClient;
              import org.apache.ignite.table.KeyValueView;

              import java.util.Map;

              class Loader {
                  void load(IgniteClient client, KeyValueView<Integer, String> cache, Map<Integer, String> rows) {
                      ClientTransaction tx = client.transactions().txStart();
                      try {
                          cache.putAll(tx, rows);
                          rows.forEach((k, v) -> cache.putIfAbsent(tx, k, v));
                          tx.commit();
                      } finally {
                          tx.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void lookupWithoutKnownTypesIsLeftWithComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.IgniteCache;

              class Raw {
                  void raw(Ignite ignite) {
                      IgniteCache cache = ignite.cache("raw");
                      cache.put("k", "v");
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;
              import org.apache.ignite.table.KeyValueView;

              class Raw {
                  void raw(Ignite ignite) {
                      // TODO Ignite 3: read the table with ignite.tables().table(name).keyValueView(KeyClass.class, ValueClass.class); the key and value types could not be determined here.
                      KeyValueView cache = ignite.cache("raw");
                      cache.put(null, "k", "v");
                  }
              }
              """
          )
        );
    }

    @Test
    void operationsWithoutEquivalentAreLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteCache;

              class Stats {
                  int count(IgniteCache<String, String> cache) {
                      cache.getAndPutIfAbsent("k", "v");
                      return cache.size();
                  }
              }
              """,
            """
              import org.apache.ignite.table.KeyValueView;

              class Stats {
                  int count(KeyValueView<String, String> cache) {
                      cache.getAndPutIfAbsent("k", "v");
                      return cache.size();
                  }
              }
              """
          )
        );
    }

    @Test
    void objectKeysAndValuesGetComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteCache;

              class Adapter {
                  private IgniteCache<Object, Object> cache;

                  void putObject(Object key, Object value) {
                      cache.put(key, value);
                  }
              }
              """,
            """
              import org.apache.ignite.table.KeyValueView;

              class Adapter {
                  // TODO Ignite 3: a KeyValueView maps keys and values to table columns, so it cannot hold java.lang.Object; use table.keyValueView() for a KeyValueView<Tuple, Tuple>, or classes whose fields match the columns.
                  private KeyValueView<Object, Object> cache;

                  void putObject(Object key, Object value) {
                      cache.put(null, key, value);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedLookupMigratesItsOperation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.Ignite;

              class Chained {
                  void put(Ignite ignite) {
                      ignite.cache("people").put(1L, "Ada");
                  }

                  int size(Ignite ignite) {
                      return ignite.cache("people").size();
                  }
              }
              """,
            """
              import org.apache.ignite.Ignite;

              class Chained {
                  void put(Ignite ignite) {
                      // TODO Ignite 3: read the table with ignite.tables().table(name).keyValueView(KeyClass.class, ValueClass.class); the key and value types could not be determined here.
                      ignite.cache("people").put(null, 1L, "Ada");
                  }

                  int size(Ignite ignite) {
                      return ignite.cache("people").size();
                  }
              }
              """
          )
        );
    }

    @Test
    void otherJCacheProvidersAreUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.cache.Cache;

              class Sessions {
                  void store(Cache<String, String> cache) {
                      cache.put("k", "v");
                      cache.clear();
                  }
              }
              """
          )
        );
    }
}

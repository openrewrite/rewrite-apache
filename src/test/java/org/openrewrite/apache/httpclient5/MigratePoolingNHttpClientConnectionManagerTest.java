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
package org.openrewrite.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigratePoolingNHttpClientConnectionManagerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpasyncclient-4", "httpcore-nio-4",
            "httpclient5", "httpcore5"))
          .recipe(new MigratePoolingNHttpClientConnectionManager());
    }

    @DocumentExample
    @Test
    void migratesPoolingNHttpClientConnectionManagerToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method() throws Exception {
                      ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                      PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method() throws Exception {
                      ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesPoolingNHttpClientConnectionManagerToBuilderWhenUsingFqn() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method() throws Exception {
                      ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                      org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager cm = new org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager(ioReactor);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method() throws Exception {
                      ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesWhenPassedAsArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;

              class A {
                  PoolingNHttpClientConnectionManager passthrough(PoolingNHttpClientConnectionManager cm) {
                      return cm;
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;

              class A {
                  PoolingAsyncClientConnectionManager passthrough(PoolingAsyncClientConnectionManager cm) {
                      return cm;
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesSimpleConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;

              class A {
                  PoolingNHttpClientConnectionManager createConnectionManager() throws Exception {
                      return new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;

              class A {
                  PoolingAsyncClientConnectionManager createConnectionManager() throws Exception {
                      return PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }
}

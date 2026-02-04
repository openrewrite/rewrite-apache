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

class MigrateApacheHttpAsyncClientTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpasyncclient-4", "httpcore-nio-4",
            "httpclient5", "httpcore5"))
          .recipeFromResources(
            "org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5_AsyncClientClassMapping",
            "org.openrewrite.apache.httpclient5.UpgradeApacheHttpCore_5_NioClassMapping"
          );
    }

    @DocumentExample
    @Test
    void migratesPoolingNHttpClientConnectionManagerToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method(ConnectingIOReactor ioReactor) {
                      PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.hc.core5.reactor.ConnectionInitiator;

              class A {
                  void method(ConnectionInitiator ioReactor) {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesAdditionalHttpClientClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.nio.client.HttpAsyncClient;
              import org.apache.http.nio.conn.NHttpClientConnectionManager;

              class A {
                  void method(HttpAsyncClient client, NHttpClientConnectionManager connectionManager) {}
              }
              """,
            """
              import org.apache.hc.client5.http.async.HttpAsyncClient;
              import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;

              class A {
                  void method(HttpAsyncClient client, AsyncClientConnectionManager connectionManager) {}
              }
              """
          )
        );
    }

    @Test
    void migratesCloseIdleConnectionsToCloseIdle() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;

              import java.util.concurrent.TimeUnit;

              class A {
                  void method(PoolingNHttpClientConnectionManager cm) {
                      cm.closeIdleConnections(30, TimeUnit.SECONDS);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.core5.util.TimeValue;

              import java.util.concurrent.TimeUnit;

              class A {
                  void method(PoolingAsyncClientConnectionManager cm) {
                      cm.closeIdle(TimeValue.of(30, TimeUnit.SECONDS));
                  }
              }
              """
          )
        );
    }

    @Test
    void foldsBuilderMethodCallsIntoChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method(ConnectingIOReactor ioReactor) {
                      PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
                      cm.setMaxTotal(100);
                      cm.setDefaultMaxPerRoute(10);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.hc.core5.reactor.ConnectionInitiator;

              class A {
                  void method(ConnectionInitiator ioReactor) {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                              .setMaxConnTotal(100)
                              .setMaxConnPerRoute(10)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentForConstructorWithConnectionFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.nio.conn.ManagedNHttpClientConnection;
              import org.apache.http.nio.conn.NHttpConnectionFactory;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method(ConnectingIOReactor ioReactor, NHttpConnectionFactory<ManagedNHttpClientConnection> factory) {
                      PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor, factory);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.hc.core5.reactor.ConnectionInitiator;
              import org.apache.http.nio.conn.ManagedNHttpClientConnection;
              import org.apache.http.nio.conn.NHttpConnectionFactory;

              class A {
                  void method(ConnectionInitiator ioReactor, NHttpConnectionFactory<ManagedNHttpClientConnection> factory) {
                      PoolingAsyncClientConnectionManager cm = /* TODO: `PoolingNHttpClientConnectionManager` with `NHttpConnectionFactory` - the connection factory configuration is lost in migration. Configure via `PoolingAsyncClientConnectionManagerBuilder` if needed. */ PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentForConstructorWithRegistry() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.config.Registry;
              import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
              import org.apache.http.nio.conn.SchemeIOSessionStrategy;
              import org.apache.http.nio.reactor.ConnectingIOReactor;

              class A {
                  void method(ConnectingIOReactor ioReactor, Registry<SchemeIOSessionStrategy> registry) {
                      PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor, registry);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
              import org.apache.hc.core5.reactor.ConnectionInitiator;
              import org.apache.http.config.Registry;
              import org.apache.http.nio.conn.SchemeIOSessionStrategy;

              class A {
                  void method(ConnectionInitiator ioReactor, Registry<SchemeIOSessionStrategy> registry) {
                      PoolingAsyncClientConnectionManager cm = /* TODO: `PoolingNHttpClientConnectionManager` with `Registry` - the scheme registry configuration is lost in migration. Configure TLS via `PoolingAsyncClientConnectionManagerBuilder.setTlsStrategy(..)` if needed. */ PoolingAsyncClientConnectionManagerBuilder.create().build();
                  }
              }
              """
          )
        );
    }
}

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

class MigratePoolingHttpClientConnectionManagerCloseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void closeExpiredAndIdleConnections() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

              import java.util.List;
              import java.util.concurrent.TimeUnit;

              class A {
                  private static final int CLOSE_IDLE_TIME_S = 30;

                  void evict(List<PoolingHttpClientConnectionManager> connectionManagers) {
                      for (PoolingHttpClientConnectionManager connectionManager : connectionManagers) {
                          connectionManager.closeExpiredConnections();
                          connectionManager.closeIdleConnections(CLOSE_IDLE_TIME_S, TimeUnit.SECONDS);
                      }
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.apache.hc.core5.util.TimeValue;

              import java.util.List;
              import java.util.concurrent.TimeUnit;

              class A {
                  private static final int CLOSE_IDLE_TIME_S = 30;

                  void evict(List<PoolingHttpClientConnectionManager> connectionManagers) {
                      for (PoolingHttpClientConnectionManager connectionManager : connectionManagers) {
                          connectionManager.closeExpired();
                          connectionManager.closeIdle(TimeValue.of(CLOSE_IDLE_TIME_S, TimeUnit.SECONDS));
                      }
                  }
              }
              """
          )
        );
    }
}

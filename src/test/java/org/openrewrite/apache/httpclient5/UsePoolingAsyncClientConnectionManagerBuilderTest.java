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

class UsePoolingAsyncClientConnectionManagerBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient5", "httpcore5"))
          .recipe(new UsePoolingAsyncClientConnectionManagerBuilder());
    }

    @DocumentExample
    @Test
    void foldsSetMaxTotalIntoBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                      cm.setMaxTotal(100);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().setMaxConnTotal(100).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void foldsMultipleMethodCallsIntoBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                      cm.setMaxTotal(100);
                      cm.setDefaultMaxPerRoute(10);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().setMaxConnTotal(100).setMaxConnPerRoute(10).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void stopsAtNonBuilderMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                      cm.setMaxTotal(100);
                      cm.close();
                      cm.setDefaultMaxPerRoute(10);
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().setMaxConnTotal(100).build();
                      cm.close();
                      cm.setDefaultMaxPerRoute(10);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyWhenNoBuilderMethodsFollowDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
              import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;

              class A {
                  void method() {
                      PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
                      System.out.println(cm);
                  }
              }
              """
          )
        );
    }
}

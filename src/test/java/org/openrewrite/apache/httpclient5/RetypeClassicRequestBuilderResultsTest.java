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

class RetypeClassicRequestBuilderResultsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RetypeClassicRequestBuilderResults())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpclient5", "httpcore5"));
    }

    @DocumentExample
    @Test
    void declaresBuiltRequestAsClassicHttpRequest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
              import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

              class A {
                  void method() {
                      HttpUriRequest request = ClassicRequestBuilder.get("https://example.com").build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ClassicHttpRequest;
              import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

              class A {
                  void method() {
                      ClassicHttpRequest request = ClassicRequestBuilder.get("https://example.com").build();
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesRequestsThatTheBuilderDidNotBuild() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.classic.methods.HttpGet;
              import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
              import org.apache.hc.core5.http.ClassicHttpRequest;
              import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

              class A {
                  void method() {
                      HttpUriRequest request = new HttpGet("https://example.com");
                      ClassicHttpRequest built = ClassicRequestBuilder.get("https://example.com").build();
                  }
              }
              """
          )
        );
    }
}

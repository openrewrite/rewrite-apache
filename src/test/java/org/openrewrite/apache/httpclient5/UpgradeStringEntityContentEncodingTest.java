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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeStringEntityContentEncodingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "httpclient-4",
            "httpcore-4",
            "httpmime-4",
            "httpclient5",
            "httpcore5"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-apache/issues/54")
    void movesAdjacentContentEncodingLiteralToStringEntityConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON);
                      se.setContentEncoding("utf-8");
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {
                  void foo() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON, "utf-8", false);
                  }
              }
              """
          )
        );
    }
}

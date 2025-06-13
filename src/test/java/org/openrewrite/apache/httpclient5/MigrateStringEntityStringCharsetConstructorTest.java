/*
 * Copyright 2025 the original author or authors.
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

class MigrateStringEntityStringCharsetConstructorTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpcore"))
          .recipeFromResources("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5");
    }

    @DocumentExample
    @Test
    void migratesWithin4xIfNotAlreadyMigratedTo5x() {
        rewriteRun(
          spec -> spec.recipe(new MigrateStringEntityStringCharsetConstructor()),
          //language=java
          java(
            """
              import org.apache.http.entity.StringEntity;

              class A {
                  void method() {
                      StringEntity utf8Entity = new StringEntity("a", "utf-8");
                      StringEntity utf16Entity = new StringEntity("b", "utf-16");
                      StringEntity anotherEntity = new StringEntity("utf-8", "utf-8");
                  }
              }
              """,
            """
              import org.apache.http.entity.StringEntity;

              import java.nio.charset.StandardCharsets;

              class A {
                  void method() {
                      StringEntity utf8Entity = new StringEntity("a", StandardCharsets.UTF_8);
                      StringEntity utf16Entity = new StringEntity("b", StandardCharsets.UTF_16);
                      StringEntity anotherEntity = new StringEntity("utf-8", StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesCharsetLiteralInConstructorToCharset() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.StringEntity;

              class A {
                  void method() {
                      StringEntity utf8Entity = new StringEntity("a", "utf-8");
                      StringEntity utf16Entity = new StringEntity("b", "utf-16");
                      StringEntity anotherEntity = new StringEntity("utf-8", "utf-8");
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.io.entity.StringEntity;

              import java.nio.charset.StandardCharsets;

              class A {
                  void method() {
                      StringEntity utf8Entity = new StringEntity("a", StandardCharsets.UTF_8);
                      StringEntity utf16Entity = new StringEntity("b", StandardCharsets.UTF_16);
                      StringEntity anotherEntity = new StringEntity("utf-8", StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }
}

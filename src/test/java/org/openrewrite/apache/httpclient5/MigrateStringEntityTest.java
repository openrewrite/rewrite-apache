/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateStringEntityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore", "httpclient5", "httpcore5"))
          .afterTypeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5")
          );
    }

    @DocumentExample
    @Test
    void setContentEncodingToConstructorArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;

              class A {

                  private void a() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON);
                      se.setContentEncoding("utf-8");
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {

                  private void a() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON, "utf-8", false);
                  }
              }
              """
          )
        );
    }

    // @Test
    void setContentEncodingToConstructorArgWithBasicHeader() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.entity.ContentType;
              import org.apache.http.entity.StringEntity;
              import org.apache.http.message.BasicHeader;

              class A {

                  private void a() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON);
                      se.setContentEncoding(new BasicHeader("Content-Encoding", "utf-8"));
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.ContentType;
              import org.apache.hc.core5.http.io.entity.StringEntity;

              class A {

                  private void a() {
                      StringEntity se = new StringEntity("", ContentType.APPLICATION_JSON, "utf-8", false);
                  }
              }
              """
          )
        );
    }
}

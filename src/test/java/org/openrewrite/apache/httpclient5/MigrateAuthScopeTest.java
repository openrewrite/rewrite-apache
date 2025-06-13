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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateAuthScopeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "httpclient-4", "httpcore-4", "httpclient5", "httpcore5"))
          .afterTypeValidationOptions(TypeValidation.none())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.apache.httpclient5.UpgradeApacheHttpClient_5")
          );
    }

    @DocumentExample
    @Test
    void authScopeAnyTest() {
        rewriteRun(
          //language=java
          java(
            """
            import org.apache.http.auth.AuthScope;

            class A {
                void method() {
                    AuthScope any = AuthScope.ANY;
                }
            }
            """,
          """
            import org.apache.hc.client5.http.auth.AuthScope;

            class A {
                void method() {
                    AuthScope any = new AuthScope(null, -1);
                }
            }
            """
          )
        );
    }
}

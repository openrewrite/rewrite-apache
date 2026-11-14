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

class RemoveRedundantCatchAlternativesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.apache.ignite.ChangeIgnite2ExceptionTypes")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cache-api", "ignite-core-2"));
    }

    @DocumentExample
    @Test
    void mergedExceptionsAreCaughtOnce() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.ignite.IgniteException;
              import org.apache.ignite.client.ClientConnectionException;
              import org.apache.ignite.client.ClientException;

              class Clients {
                  void call(Runnable work) {
                      try {
                          work.run();
                      } catch (ClientException | IgniteException e) {
                          System.out.println("failed");
                      }
                      try {
                          work.run();
                      } catch (ClientConnectionException | IgniteException | IllegalStateException e) {
                          System.out.println("failed");
                      }
                  }
              }
              """,
            """
              import org.apache.ignite.lang.IgniteException;

              class Clients {
                  void call(Runnable work) {
                      try {
                          work.run();
                      } catch (IgniteException e) {
                          System.out.println("failed");
                      }
                      try {
                          work.run();
                      } catch (IgniteException | IllegalStateException e) {
                          System.out.println("failed");
                      }
                  }
              }
              """
          )
        );
    }
}

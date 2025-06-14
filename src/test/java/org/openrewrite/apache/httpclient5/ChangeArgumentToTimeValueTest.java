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

import java.util.concurrent.TimeUnit;

import static org.openrewrite.java.Assertions.java;

class ChangeArgumentToTimeValueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "httpcore5")
          //language=java
          .dependsOn(
            """
            import org.apache.hc.core5.util.TimeValue;

            class A {
                private TimeValue storedValue;

                void method(long value) {
                    storedValue = TimeValue.ofMilliseconds(value);
                }

                void method(TimeValue value) {
                    storedValue = value;
                }
            }
            """
          ));
    }

    @DocumentExample
    @Test
    void changeArgumentToTimeValueDefaultMilliseconds() {
        rewriteRun(
          spec -> spec.recipe(new ChangeArgumentToTimeValue("A method(long)", null)),
          //language=java
          java(
            """
              class B {
                  void test() {
                      A a = new A();
                      a.method(100);
                  }
              }
              """,
            """
              import org.apache.hc.core5.util.TimeValue;

              import java.util.concurrent.TimeUnit;

              class B {
                  void test() {
                      A a = new A();
                      a.method(TimeValue.of(100, TimeUnit.MILLISECONDS));
                  }
              }
              """
          )
        );
    }

    @Test
    void changeArgumentToTimeValueWithTimeUnitProvided() {
        rewriteRun(
          spec -> spec.recipe(new ChangeArgumentToTimeValue("A method(long)", TimeUnit.SECONDS)),
          //language=java
          java(
            """
              class B {
                  void test() {
                      A a = new A();
                      a.method(100);
                  }
              }
              """,
            """
              import org.apache.hc.core5.util.TimeValue;

              import java.util.concurrent.TimeUnit;

              class B {
                  void test() {
                      A a = new A();
                      a.method(TimeValue.of(100, TimeUnit.SECONDS));
                  }
              }
              """
          )
        );
    }
}

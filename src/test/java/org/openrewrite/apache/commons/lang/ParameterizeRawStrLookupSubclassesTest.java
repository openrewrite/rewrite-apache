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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ParameterizeRawStrLookupSubclassesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("commons-text"))
          .recipe(new ParameterizeRawStrLookupSubclasses());
    }

    @DocumentExample
    @Test
    void addStringTypeArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.text.StrLookup;

              class EnvironmentLookup extends StrLookup {
                  @Override
                  public String lookup(String key) {
                      return System.getenv(key);
                  }
              }
              """,
            """
              import org.apache.commons.text.StrLookup;

              class EnvironmentLookup extends StrLookup<String> {
                  @Override
                  public String lookup(String key) {
                      return System.getenv(key);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainAlreadyParameterizedSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.text.StrLookup;

              class ConstantLookup extends StrLookup<Object> {
                  @Override
                  public String lookup(String key) {
                      return key;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainUnrelatedSuperclass() {
        rewriteRun(
          //language=java
          java(
            """
              class A extends RuntimeException {
              }
              """
          )
        );
    }

    @Test
    void addStringTypeArgumentToAnonymousSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.text.StrLookup;

              class A {
                  StrLookup lookup() {
                      return new StrLookup() {
                          @Override
                          public String lookup(String key) {
                              return key;
                          }
                      };
                  }
              }
              """,
            """
              import org.apache.commons.text.StrLookup;

              class A {
                  StrLookup lookup() {
                      return new StrLookup<String>() {
                          @Override
                          public String lookup(String key) {
                              return key;
                          }
                      };
                  }
              }
              """
          )
        );
    }
}

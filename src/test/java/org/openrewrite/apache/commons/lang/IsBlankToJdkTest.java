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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class IsBlankToJdkTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "commons-lang3", "plexus-utils", "maven-shared-utils"))
                .recipe(new IsBlankToJdk())
                .allSources(s -> s.markers(javaVersion(21)));
    }

    @DocumentExample
    @Test
    void exampleUse() {
        rewriteRun(
          // language=java
          java(
            """
            import org.apache.commons.lang3.StringUtils;

            class A {
                boolean test(String first) {
                    return StringUtils.isBlank(first);
                }
            }
            """,
            """
            class A {
                boolean test(String first) {
                    return first == null || first.isBlank();
                }
            }
            """
          ));
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.isBlank(first) # first == null || first.isBlank()
      org.apache.commons.lang3.StringUtils # StringUtils.isBlank(field) # field == null || field.isBlank()
      org.apache.commons.lang3.StringUtils # StringUtils.isBlank(this.field) # this.field == null || this.field.isBlank()
      org.apache.commons.lang3.StringUtils # StringUtils.isNotBlank(first) # first != null && !first.isBlank()
      org.apache.maven.shared.utils.StringUtils # StringUtils.isBlank(first) # first == null || first.isBlank()
      org.apache.maven.shared.utils.StringUtils # StringUtils.isNotBlank(first) # first != null && !first.isBlank()
      org.codehaus.plexus.util.StringUtils # StringUtils.isBlank(first) # first == null || first.isBlank()
      org.codehaus.plexus.util.StringUtils # StringUtils.isNotBlank(first) # first != null && !first.isBlank()
      """)
    @ParameterizedTest
    void replaceDirectUse(String classname, String beforeLine, String afterLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  String field = "foo";
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(classname, beforeLine),
            """
              class A {
                  String field = "foo";
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(afterLine)));
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # !StringUtils.isBlank(first) # !(first == null || first.isBlank())
      org.apache.commons.lang3.StringUtils # !StringUtils.isNotBlank(first) # !(first != null && !first.isBlank())
      org.apache.commons.lang3.StringUtils # !(StringUtils.isBlank(first)) # !(first == null || first.isBlank())
      org.apache.commons.lang3.StringUtils # !(StringUtils.isNotBlank(first)) # !(first != null && !first.isBlank())
      org.apache.maven.shared.utils.StringUtils # !StringUtils.isBlank(first) # !(first == null || first.isBlank())
      org.apache.maven.shared.utils.StringUtils # !StringUtils.isNotBlank(first) # !(first != null && !first.isBlank())
      org.codehaus.plexus.util.StringUtils # !StringUtils.isBlank(first) # !(first == null || first.isBlank())
      org.codehaus.plexus.util.StringUtils # !StringUtils.isNotBlank(first) # !(first != null && !first.isBlank())
      """)
    @ParameterizedTest
    void replaceNegated(String classname, String beforeLine, String afterLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(classname, beforeLine),
            """
              class A {
                  boolean test(String first) {
                      return %s;
                  }
              }
              """.formatted(afterLine)));
    }

    @Test
    void convertSimpleGetters() {
        // language=java
        rewriteRun(
          java(
            """
              class B {
                  String field;
                  String getField() {
                      return field;
                  }
              }
              """
          ),
          java(
            """
              import org.apache.commons.lang3.StringUtils;

              class A {
                  boolean test(B b) {
                      return StringUtils.isBlank(b.getField());
                  }
              }
              """,
            """
              class A {
                  boolean test(B b) {
                      return b.getField() == null || b.getField().isBlank();
                  }
              }
              """
          )
        );
    }

    @Test
    void realWorldDockerWorkflowExample() {
        rewriteRun(
          // language=java
          java(
            """
            import org.apache.commons.lang3.StringUtils;

            class DeclarativeDockerUtils {
                String getOverride() { return ""; }
                String getLabel() { return ""; }

                void test(String override, String label) {
                    if (!StringUtils.isBlank(override)) {
                        if (!StringUtils.isBlank(label)) {
                            System.out.println("both set");
                        }
                    }
                }
            }
            """,
            """
            class DeclarativeDockerUtils {
                String getOverride() { return ""; }
                String getLabel() { return ""; }

                void test(String override, String label) {
                    if (!(override == null || override.isBlank())) {
                        if (!(label == null || label.isBlank())) {
                            System.out.println("both set");
                        }
                    }
                }
            }
            """
          ));
    }

    @CsvSource(delimiter = '#', textBlock = """
      org.apache.commons.lang3.StringUtils # StringUtils.isBlank(foo())
      org.apache.commons.lang3.StringUtils # StringUtils.isBlank(first + second)
      org.apache.commons.lang3.StringUtils # StringUtils.isNotBlank(foo())
      org.apache.commons.lang3.StringUtils # StringUtils.isNotBlank(first + second)
      org.apache.maven.shared.utils.StringUtils # StringUtils.isBlank(foo())
      org.codehaus.plexus.util.StringUtils # StringUtils.isBlank(foo())
      """)
    @ParameterizedTest
    void retainComplexUse(String classname, String beforeLine) {
        // language=java
        rewriteRun(
          java(
            """
              import %s;

              class A {
                  boolean test(String first, String second) {
                      return %s;
                  }
                  private String foo() {
                      return "foo";
                  }
              }
              """.formatted(classname, beforeLine)));
    }
}
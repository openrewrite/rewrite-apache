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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;


class UpgradeApacheCommonsLang_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3", "commons-text"))
          .recipeFromResources("org.openrewrite.apache.commons.lang.UpgradeApacheCommonsLang_2_3");
    }

    @DocumentExample
    @Test
    void apacheCommonsLang() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.RandomStringUtils;
              import org.apache.commons.lang.StringUtils;

              import java.util.Map;

              class Test {
                  static void helloApacheLang() {
                     String aaa = StringUtils.repeat("a", 20);
                     String randomString = RandomStringUtils.random(10);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomStringUtils;
              import org.apache.commons.lang3.StringUtils;

              import java.util.Map;

              class Test {
                  static void helloApacheLang() {
                     String aaa = StringUtils.repeat("a", 20);
                     String randomString = RandomStringUtils.random(10);
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionUtilsGetFullStackTraceToGetStackTrace() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.exception.ExceptionUtils;

              class A {
                  String doSomething(Throwable t) {
                     return ExceptionUtils.getFullStackTrace(t);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.exception.ExceptionUtils;

              class A {
                  String doSomething(Throwable t) {
                     return ExceptionUtils.getStackTrace(t);
                  }
              }
              """
          )
        );
    }

    @Test
    void nullArgumentExceptionChangesToNullPointerException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.NullArgumentException;

              class A {
                  boolean doSomething(Throwable t) {
                     return t instanceof NullArgumentException;
                  }
              }
              """,
            """
              class A {
                  boolean doSomething(Throwable t) {
                     return t instanceof NullPointerException;
                  }
              }
              """
          )
        );
    }

    @Test
    void nestableExceptionChangesToException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.exception.NestableException;

              class ConfigurationException extends NestableException {
                  ConfigurationException(String message, Throwable cause) {
                      super(message, cause);
                  }
              }
              """,
            """
              class ConfigurationException extends Exception {
                  ConfigurationException(String message, Throwable cause) {
                      super(message, cause);
                  }
              }
              """
          )
        );
    }

    @Test
    void nestableRuntimeExceptionChangesToRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.exception.NestableRuntimeException;

              class ConfigurationRuntimeException extends NestableRuntimeException {
                  ConfigurationRuntimeException(String message) {
                      super(message);
                  }
              }
              """,
            """
              class ConfigurationRuntimeException extends RuntimeException {
                  ConfigurationRuntimeException(String message) {
                      super(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void deprecatedNumberUtilsMovesToMathPackage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.NumberUtils;

              class A {
                  int parse(String s) {
                      return NumberUtils.stringToInt(s);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.math.NumberUtils;

              class A {
                  int parse(String s) {
                      return NumberUtils.toInt(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void randomUtilsMovesOutOfMathPackage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.math.RandomUtils;

              class A {
                  boolean flip() {
                      return RandomUtils.nextBoolean();
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomUtils;

              class A {
                  boolean flip() {
                      return RandomUtils.nextBoolean();
                  }
              }
              """
          )
        );
    }

    @Test
    void lang2TextPackageMigratesThroughToCommonsText() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>commons-lang</groupId>
                            <artifactId>commons-lang</artifactId>
                            <version>2.6</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>commons-lang3</artifactId>")
                .contains("<artifactId>commons-text</artifactId>")
                .actual())
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.lang.text.StrLookup;
                  import org.apache.commons.lang.text.StrSubstitutor;

                  class A {
                      String interpolate(String s) {
                          return new StrSubstitutor(StrLookup.noneLookup()).replace(s);
                      }
                  }
                  """,
                """
                  import org.apache.commons.text.StrLookup;
                  import org.apache.commons.text.StrSubstitutor;

                  class A {
                      String interpolate(String s) {
                          return new StrSubstitutor(StrLookup.noneLookup()).replace(s);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void toBooleanObjectOnPrimitiveHasNoLang3Equivalent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.BooleanUtils;

              class A {
                  Boolean box(boolean b) {
                      return BooleanUtils.toBooleanObject(b);
                  }
              }
              """,
            """
              class A {
                  Boolean box(boolean b) {
                      return Boolean.valueOf(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void objectUtilsToStringWithDefaultBecomesAmbiguousInLang3() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.ObjectUtils;

              class A {
                  String text(Object o) {
                      return ObjectUtils.toString(o, null);
                  }
              }
              """,
            """
              import java.util.Objects;

              class A {
                  String text(Object o) {
                      return Objects.toString(o, null);
                  }
              }
              """
          )
        );
    }

    @Test
    void isJavaVersionAtLeastTakesAJavaVersion() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.SystemUtils;

              class A {
                  boolean modern() {
                      return SystemUtils.isJavaVersionAtLeast(1.8f);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.JavaVersion;
              import org.apache.commons.lang3.SystemUtils;

              class A {
                  boolean modern() {
                      return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void rawStrLookupSubclassGainsTypeArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.text.StrLookup;

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
    void stringEscapeUtilsHtmlAndJavaScriptWereRenamed() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.StringEscapeUtils;

              class A {
                  String html(String s) {
                      return StringEscapeUtils.escapeHtml(StringEscapeUtils.unescapeHtml(s));
                  }

                  String script(String s) {
                      return StringEscapeUtils.escapeJavaScript(StringEscapeUtils.unescapeJavaScript(s));
                  }
              }
              """,
            """
              import org.apache.commons.lang3.StringEscapeUtils;

              class A {
                  String html(String s) {
                      return StringEscapeUtils.escapeHtml4(StringEscapeUtils.unescapeHtml4(s));
                  }

                  String script(String s) {
                      return StringEscapeUtils.escapeEcmaScript(StringEscapeUtils.unescapeEcmaScript(s));
                  }
              }
              """
          )
        );
    }

    @Test
    void isAssignableKeepsAutoboxingDisabled() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.ClassUtils;

              class A {
                  boolean single(Class<?> from, Class<?> to) {
                      return ClassUtils.isAssignable(from, to);
                  }

                  boolean many(Class<?>[] from, Class<?>[] to) {
                      return ClassUtils.isAssignable(from, to);
                  }

                  boolean explicit(Class<?> from, Class<?> to) {
                      return ClassUtils.isAssignable(from, to, true);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.ClassUtils;

              class A {
                  boolean single(Class<?> from, Class<?> to) {
                      return ClassUtils.isAssignable(from, to, false);
                  }

                  boolean many(Class<?>[] from, Class<?>[] to) {
                      return ClassUtils.isAssignable(from, to, false);
                  }

                  boolean explicit(Class<?> from, Class<?> to) {
                      return ClassUtils.isAssignable(from, to, true);
                  }
              }
              """
          )
        );
    }
}

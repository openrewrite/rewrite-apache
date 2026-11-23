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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;


class UpgradeApacheCommonsLang_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "commons-lang-2",
            "commons-lang3",
            "commons-text"))
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
          // ChangePackage keeps Commons Lang 2's StrLookup, which is raw
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
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
    void transitiveCommonsLang2GetsDirectCommonsLang3Dependency() {
        // Commons Lang 2 arrives through commons-configuration, so there is no commons-lang dependency to change
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
                            <groupId>commons-configuration</groupId>
                            <artifactId>commons-configuration</artifactId>
                            <version>1.10</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>commons-lang3</artifactId>")
                .actual())
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.commons.lang.StringUtils;

                  class A {
                      String f(String s) {
                          return StringUtils.trim(s);
                      }
                  }
                  """,
                """
                  import org.apache.commons.lang3.StringUtils;

                  class A {
                      String f(String s) {
                          return StringUtils.trim(s);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void randomUtilsNextIntGainsLowerBound() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.math.RandomUtils;

              class A {
                  void f() {
                      int i = RandomUtils.nextInt(500);
                      int unbounded = RandomUtils.nextInt();
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomUtils;

              class A {
                  void f() {
                      int i = RandomUtils.nextInt(0, 500);
                      int unbounded = RandomUtils.nextInt();
                  }
              }
              """
          )
        );
    }

    @Test
    void fullyQualifiedUsagesAreMigrated() {
        rewriteRun(
          // ChangePackage keeps the Lang 2 parameter types of the method
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(false).build()),
          //language=java
          java(
            """
              class A {
                  boolean f(String s) {
                      return org.apache.commons.lang.StringUtils.containsIgnoreCase(s, "GET");
                  }
              }
              """,
            """
              class A {
                  boolean f(String s) {
                      return org.apache.commons.lang3.StringUtils.containsIgnoreCase(s, "GET");
                  }
              }
              """
          )
        );
    }

    @Test
    void escapeSqlWasRemovedSoDoubleTheQuotes() {
        rewriteRun(
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
          //language=java
          java(
            """
              import org.apache.commons.lang.StringEscapeUtils;

              class A {
                  String f(String s) {
                      return StringEscapeUtils.escapeSql(s);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.StringUtils;

              class A {
                  String f(String s) {
                      return StringUtils.replace(s, "'", "''");
                  }
              }
              """
          )
        );
    }

    @Test
    void stringEscapeUtilsWriterMethodsMoveToCommonsText() {
        rewriteRun(
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(false).build()),
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
                  import org.apache.commons.lang.StringEscapeUtils;

                  import java.io.IOException;
                  import java.io.Writer;

                  class A {
                      void f(Writer out, String url) throws IOException {
                          StringEscapeUtils.escapeXml(out, url);
                      }
                  }
                  """,
                """
                  import org.apache.commons.text.StringEscapeUtils;

                  import java.io.IOException;
                  import java.io.Writer;

                  class A {
                      void f(Writer out, String url) throws IOException {
                          StringEscapeUtils.ESCAPE_XML10.translate(url, out);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void enumsPackageHasNoCommonsLang3Counterpart() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.StringUtils;
              import org.apache.commons.lang.enums.Enum;

              class Color extends Enum {
                  Color(String name) {
                      super(StringUtils.trim(name));
                  }
              }
              """,
            """
              import org.apache.commons.lang3.StringUtils;
              import org.apache.commons.lang.enums.Enum;

              class Color extends /*~~(Commons Lang 3 has no `enums` package; replace with a Java `enum`)~~>*/Enum {
                  Color(String name) {
                      super(StringUtils.trim(name));
                  }
              }
              """
          )
        );
    }

    @Test
    void gradleVersionVariableIsUpgradedToo() {
        // Changing only the coordinates would leave `commons-lang3:2.6`, which doesn't exist
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              ext {
                  apacheCommonsLangVersion = '2.6'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation "commons-lang:commons-lang:$apacheCommonsLangVersion"
              }
              """,
            spec -> spec.after(gradle -> assertThat(gradle)
              .contains("org.apache.commons:commons-lang3:$apacheCommonsLangVersion")
              .doesNotContain("'2.6'")
              .containsPattern("apacheCommonsLangVersion = '3\\.\\d+\\.\\d+'")
              .actual())
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
          // ChangeMethodName keeps Commons Lang 2's methods, which aren't final as Commons Lang 3's are
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
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
          // ChangePackage keeps Commons Lang 2's isAssignable, whose Class parameters are raw
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
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

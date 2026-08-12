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
package org.openrewrite.apache.commons;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.maven.Assertions.pomXml;

class ApacheCommonsBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "commons-io", "commons-lang3"))
          .recipeFromResources("org.openrewrite.apache.commons.ApacheCommonsBestPractices");
    }

    @DocumentExample
    @Test
    void useJdkEquivalentsAndExplicitCharset() {
        rewriteRun(
          java(
            """
              import org.apache.commons.io.IOUtils;
              import org.apache.commons.lang3.SystemUtils;

              import java.io.InputStream;

              class Test {
                  String lineSeparator = SystemUtils.LINE_SEPARATOR;

                  String read(InputStream in) throws Exception {
                      return IOUtils.toString(in);
                  }
              }
              """,
            """
              import org.apache.commons.io.IOUtils;

              import java.io.InputStream;
              import java.nio.charset.StandardCharsets;

              class Test {
                  String lineSeparator = System.lineSeparator();

                  String read(InputStream in) throws Exception {
                      return IOUtils.toString(in, StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateApacheCommonsCharsetsToStandardCharsets() {
        rewriteRun(
          java(
            """
              import org.apache.commons.io.Charsets;

              import java.nio.charset.Charset;

              class Test {
                  Charset charset = Charsets.UTF_8;
              }
              """,
            """
              import java.nio.charset.Charset;
              import java.nio.charset.StandardCharsets;

              class Test {
                  Charset charset = StandardCharsets.UTF_8;
              }
              """
          )
        );
    }

    @Test
    void relocateApacheCommonsIo() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.commons</groupId>
                          <artifactId>commons-io</artifactId>
                          <version>1.3.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>commons-io</groupId>
                          <artifactId>commons-io</artifactId>
                          <version>1.3.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void upgradeApacheCommonsLang() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3")),
          //language=java
          java(
            """
              import org.apache.commons.lang.RandomStringUtils;
              import org.apache.commons.lang.exception.ExceptionUtils;

              class Test {
                  String stackTrace(Throwable t) {
                      String random = RandomStringUtils.random(10);
                      return ExceptionUtils.getFullStackTrace(t);
                  }
              }
              """,
            """
              import org.apache.commons.lang3.RandomStringUtils;
              import org.apache.commons.lang3.exception.ExceptionUtils;

              class Test {
                  String stackTrace(Throwable t) {
                      String random = RandomStringUtils.random(10);
                      return ExceptionUtils.getStackTrace(t);
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeApacheCommonsLangAndThenInlineToJdk() {
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-lang3"))
            .allSources(s -> s.markers(javaVersion(21))),
          //language=java
          java(
            """
              import org.apache.commons.lang.StringUtils;

              class Test {
                  boolean blank(String s) {
                      return StringUtils.isBlank(s);
                  }
              }
              """,
            """
              class Test {
                  boolean blank(String s) {
                      return s == null || s.isBlank();
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeApacheCommonsCollections() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("commons-collections", "commons-collections4")),
          //language=java
          java(
            """
              import org.apache.commons.collections.CollectionUtils;

              class Test {
                  static void reverse(Object[] input) {
                      CollectionUtils.reverseArray(input);
                  }
              }
              """,
            """
              import org.apache.commons.collections4.CollectionUtils;

              class Test {
                  static void reverse(Object[] input) {
                      CollectionUtils.reverseArray(input);
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradeApacheCommonsMath() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("commons-math", "commons-math3")),
          //language=java
          java(
            """
              import org.apache.commons.math.stat.StatUtils;

              class Test {
                  static double max(double[] data) {
                      return StatUtils.max(data);
                  }
              }
              """,
            """
              import org.apache.commons.math3.stat.StatUtils;

              class Test {
                  static double max(double[] data) {
                      return StatUtils.max(data);
                  }
              }
              """
          )
        );
    }
}

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
package org.openrewrite.apache.commons.lang;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class WordUtilsToCommonsTextTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("commons-lang", "commons-text"))
          .recipeFromResource(
            "/META-INF/rewrite/apache-commons-lang-2-3.yml",
            "org.openrewrite.apache.commons.lang.WordUtilsToCommonsText");
    }

    @DocumentExample
    @Test
    void migrateWordUtilsToCommonsText() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang.WordUtils;

              class Lang2 {
                  String capitalize(String str) {
                      return WordUtils.capitalize(str);
                  }

                  String wrap(String str, int width) {
                      return WordUtils.wrap(str, width);
                  }
              }
              """,
            """
              import org.apache.commons.text.WordUtils;

              class Lang2 {
                  String capitalize(String str) {
                      return WordUtils.capitalize(str);
                  }

                  String wrap(String str, int width) {
                      return WordUtils.wrap(str, width);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateLang3WordUtilsToCommonsText() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.WordUtils;

              class Lang3 {
                  String capitalize(String str) {
                      return WordUtils.capitalize(str);
                  }

                  String wrap(String str, int width) {
                      return WordUtils.wrap(str, width);
                  }
              }
              """,
            """
              import org.apache.commons.text.WordUtils;

              class Lang3 {
                  String capitalize(String str) {
                      return WordUtils.capitalize(str);
                  }

                  String wrap(String str, int width) {
                      return WordUtils.wrap(str, width);
                  }
              }
              """
          )
        );
    }

    @Test
    void addCommonsTextDependencyWhenWordUtilsIsUsed() {
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
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-text</artifactId>
                            <version>1.14.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              java(
                """
                  import org.apache.commons.lang.WordUtils;

                  class A {
                      String capitalize(String str) {
                          return WordUtils.capitalize(str);
                      }
                  }
                  """,
                """
                  import org.apache.commons.text.WordUtils;

                  class A {
                      String capitalize(String str) {
                          return WordUtils.capitalize(str);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doesNotMigrateAlreadyMigratedCode() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.text.WordUtils;

              class A {
                  String capitalize(String str) {
                      return WordUtils.capitalize(str);
                  }
              }
              """
          )
        );
    }
}

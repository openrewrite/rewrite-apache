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

class MigrateSystemUtilsDeprecationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("commons-lang3"))
          .recipeFromResources("org.openrewrite.apache.commons.lang3.MigrateSystemUtilsDeprecations");
    }

    @DocumentExample
    @Test
    void fileSeparator() {
        rewriteRun(
          java(
            """
              import org.apache.commons.lang3.SystemUtils;

              class Test {
                  String f = SystemUtils.FILE_SEPARATOR;
                  String p = SystemUtils.PATH_SEPARATOR;
                  String l = SystemUtils.LINE_SEPARATOR;
              }
              """,
            """
              import java.io.File;

              class Test {
                  String f = File.separator;
                  String p = File.pathSeparator;
                  String l = System.lineSeparator();
              }
              """
          )
        );
    }

    @Test
    void isJava19() {
        rewriteRun(
          java(
            """
              import org.apache.commons.lang3.SystemUtils;

              class Test {
                  boolean b = SystemUtils.IS_JAVA_1_9;
              }
              """,
            """
              import org.apache.commons.lang3.SystemUtils;

              class Test {
                  boolean b = SystemUtils.IS_JAVA_9;
              }
              """
          )
        );
    }

    @Test
    void replaceKeysWithSystemProperties() {
        rewriteRun(
          java(
            """
              import org.apache.commons.lang3.SystemUtils;

              class Test {
                  String k1 = SystemUtils.USER_HOME_KEY;
                  //String k2 = SystemUtils.USER_NAME_KEY;
                  String k3 = SystemUtils.USER_DIR_KEY;
                  String k4 = SystemUtils.JAVA_IO_TMPDIR_KEY;
                  String k5 = SystemUtils.JAVA_HOME_KEY;
              }
              """,
            """
              import org.apache.commons.lang3.SystemProperties;

              class Test {
                  String k1 = SystemProperties.USER_HOME;
                  //String k2 = SystemUtils.USER_NAME_KEY;
                  String k3 = SystemProperties.USER_DIR;
                  String k4 = SystemProperties.JAVA_IO_TMPDIR;
                  String k5 = SystemProperties.JAVA_HOME;
              }
              """
          )
        );
    }
}

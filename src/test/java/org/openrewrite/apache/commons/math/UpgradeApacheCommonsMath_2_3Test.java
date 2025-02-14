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
package org.openrewrite.apache.commons.math;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeApacheCommonsMath_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("commons-math", "commons-math3"))
          .recipeFromResources("org.openrewrite.apache.commons.math.UpgradeApacheCommonsMath_2_3");
    }

    @DocumentExample
    @Test
    void apacheCommonsMath() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.math.Field;
              import org.apache.commons.math.stat.StatUtils;

              class Test {
                  static void helloApacheMath() {
                     Field field = null;
                     double[] data = new double[] { 25.1d, 35.2d };
                     double max = StatUtils.max(data);
                  }
              }
              """,
            """
              import org.apache.commons.math3.Field;
              import org.apache.commons.math3.stat.StatUtils;

              class Test {
                  static void helloApacheMath() {
                     Field field = null;
                     double[] data = new double[] { 25.1d, 35.2d };
                     double max = StatUtils.max(data);
                  }
              }
              """
          )
        );
    }
}

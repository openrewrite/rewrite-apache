/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.poi;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReplaceSetBoldweightWithSetBoldTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceSetBoldweightWithSetBoldRecipes())
          .parser(JavaParser.fromJavaVersion().classpath(
            "poi"));
    }
    @Test
    void replaceSetBoldweightWithSetBoldShort() {
        //language=java
        rewriteRun(
          java(
            """
            import org.apache.poi.ss.usermodel.Font;
            
            class Test {
                void method(Font font) {
                    font.setBoldweight((short) 700);
                }
            }
            """,
            """
            import org.apache.poi.ss.usermodel.Font;
            
            class Test {
                void method(Font font) {
                    font.setBold(true);
                }
            }
            """
          )
        );
    }

    @Test
    void replaceSetBoldweightWithSetBoldEnum() {
        //language=java
        rewriteRun(
          java(
            """
            import org.apache.poi.ss.usermodel.Font;
            
            class Test {
                void method(Font font) {
                    font.setBoldweight(font.BOLDWEIGHT_BOLD);
                }
            }
            """,
            """
            import org.apache.poi.ss.usermodel.Font;
            
            class Test {
                void method(Font font) {
                    font.setBold(true);
                }
            }
            """
          )
        );
    }
}

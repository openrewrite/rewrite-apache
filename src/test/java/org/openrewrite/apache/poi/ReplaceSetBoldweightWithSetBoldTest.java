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

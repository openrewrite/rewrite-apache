package org.openrewrite.apache.poi;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReplaceSetCellTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceSetCellTypeRecipes())
          .parser(JavaParser.fromJavaVersion().classpath(
            "poi"));
    }

    @Test
    void replaceSetCellTypeInt() {
        //language=java
        rewriteRun(
          java(
            """
            import org.apache.poi.ss.usermodel.Cell;
            
            class Test {
                void method(Cell cell) {
                    cell.setCellType(0);
                }
            }
            """,
            """
            import org.apache.poi.ss.usermodel.Cell;
            import org.apache.poi.ss.usermodel.CellType;
            
            class Test {
                void method(Cell cell) {
                    cell.setCellType(CellType.NUMERIC);
                }
            }
            """
          )
        );
    }

    @Test
    void replaceSetCellTypeEnum() {
        //language=java
        rewriteRun(
          java(
            """
            import org.apache.poi.ss.usermodel.Cell;
            
            class Test {
                void method(Cell cell) {
                    cell.setCellType(Cell.CELL_TYPE_NUMERIC);
                }
            }
            """,
            """
            import org.apache.poi.ss.usermodel.Cell;
            import org.apache.poi.ss.usermodel.CellType;
            
            class Test {
                void method(Cell cell) {
                    cell.setCellType(CellType.NUMERIC);
                }
            }
            """
          )
        );
    }
}

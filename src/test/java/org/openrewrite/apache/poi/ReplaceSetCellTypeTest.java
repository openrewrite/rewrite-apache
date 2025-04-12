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
package org.openrewrite.apache.poi;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"deprecation", "AccessStaticViaInstance", "RedundantSuppression"})
class ReplaceSetCellTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceSetCellType())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi"));
    }

    @DocumentExample
    @Test
    void numeric() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(0);
                      cell.setCellType(Cell.CELL_TYPE_NUMERIC);
                      cell.setCellType(cell.CELL_TYPE_NUMERIC);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.NUMERIC);
                      cell.setCellType(CellType.NUMERIC);
                      cell.setCellType(CellType.NUMERIC);
                  }
              }
              """
          )
        );
    }

    @Test
    void string() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(1);
                      cell.setCellType(Cell.CELL_TYPE_STRING);
                      cell.setCellType(cell.CELL_TYPE_STRING);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.STRING);
                      cell.setCellType(CellType.STRING);
                      cell.setCellType(CellType.STRING);
                  }
              }
              """
          )
        );
    }

    @Test
    void formula() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(2);
                      cell.setCellType(Cell.CELL_TYPE_FORMULA);
                      cell.setCellType(cell.CELL_TYPE_FORMULA);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.FORMULA);
                      cell.setCellType(CellType.FORMULA);
                      cell.setCellType(CellType.FORMULA);
                  }
              }
              """
          )
        );
    }

    @Test
    void blank() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(3);
                      cell.setCellType(Cell.CELL_TYPE_BLANK);
                      cell.setCellType(cell.CELL_TYPE_BLANK);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.BLANK);
                      cell.setCellType(CellType.BLANK);
                      cell.setCellType(CellType.BLANK);
                  }
              }
              """
          )
        );
    }

    @Test
    void bool() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(4);
                      cell.setCellType(Cell.CELL_TYPE_BOOLEAN);
                      cell.setCellType(cell.CELL_TYPE_BOOLEAN);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.BOOLEAN);
                      cell.setCellType(CellType.BOOLEAN);
                      cell.setCellType(CellType.BOOLEAN);
                  }
              }
              """
          )
        );
    }

    @Test
    void error() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(5);
                      cell.setCellType(Cell.CELL_TYPE_ERROR);
                      cell.setCellType(cell.CELL_TYPE_ERROR);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      cell.setCellType(CellType.ERROR);
                      cell.setCellType(CellType.ERROR);
                      cell.setCellType(CellType.ERROR);
                  }
              }
              """
          )
        );
    }
}

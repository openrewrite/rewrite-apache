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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceSetCellTypeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceSetCellTypeRecipes())
          .parser(JavaParser.fromJavaVersion().classpath("poi"));
    }

    @DocumentExample
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
                      cell.setCellType(Cell.CELL_TYPE_NUMERIC);
                      cell.setCellType(cell.CELL_TYPE_ERROR);
                      cell.setCellType(Cell.CELL_TYPE_ERROR);
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
                      cell.setCellType(CellType.ERROR);
                      cell.setCellType(CellType.ERROR);
                  }
              }
              """
          )
        );
    }
}

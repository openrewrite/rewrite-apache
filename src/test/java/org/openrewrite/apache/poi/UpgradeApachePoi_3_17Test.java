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
package org.openrewrite.apache.poi;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeApachePoi_3_17Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.apache.poi.UpgradeApachePoi_3_17")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3.15"));
    }

    @DocumentExample
    @Test
    void enumAccessorsAreTheOnlyOnesThatReturnTheEnumIn3_17() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CreationHelper;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Cell cell, Row row, Hyperlink link, CreationHelper helper) {
                      int type = cell.getCellType();
                      switch (type) {
                          case Cell.CELL_TYPE_STRING:
                              row.createCell(0, type);
                              break;
                          case Cell.CELL_TYPE_BLANK:
                              row.createCell(1, 1);
                              break;
                      }
                      if (link.getType() == Hyperlink.LINK_URL) {
                          helper.createHyperlink(link.getType());
                      }
                      cell.setCellType(Cell.CELL_TYPE_NUMERIC);
                  }
              }
              """,
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.CreationHelper;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Cell cell, Row row, Hyperlink link, CreationHelper helper) {
                      CellType type = cell.getCellTypeEnum();
                      switch (type) {
                          case STRING:
                              row.createCell(0, type);
                              break;
                          case BLANK:
                              row.createCell(1, CellType.STRING);
                              break;
                      }
                      if (link.getTypeEnum() == HyperlinkType.URL) {
                          helper.createHyperlink(link.getTypeEnum());
                      }
                      cell.setCellType(CellType.NUMERIC);
                  }
              }
              """
          )
        );
    }

    @Test
    void enumAccessorsStayOnTheVersionThatNeedsThem() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell, Hyperlink link) {
                      return cell.getCellTypeEnum() == CellType.STRING || link.getTypeEnum() == HyperlinkType.URL;
                  }
              }
              """
          )
        );
    }

    @Test
    void styleConstantsStillReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.CellStyle;

              class Test {
                  void method(CellStyle style) {
                      style.setAlignment(CellStyle.ALIGN_CENTER);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.CellStyle;
              import org.apache.poi.ss.usermodel.HorizontalAlignment;

              class Test {
                  void method(CellStyle style) {
                      style.setAlignment(HorizontalAlignment.CENTER);
                  }
              }
              """
          )
        );
    }
}

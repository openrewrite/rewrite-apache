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
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApachePoi_4_1Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.apache.poi.UpgradeApachePoi_4_1")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3"));
    }

    @DocumentExample
    @Test
    void npoifsToPoifs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;

              import java.io.File;
              import java.io.IOException;

              class Test {
                  void method(File file) throws IOException {
                      NPOIFSFileSystem fs = new NPOIFSFileSystem(file);
                  }
              }
              """,
            """
              import org.apache.poi.poifs.filesystem.POIFSFileSystem;

              import java.io.File;
              import java.io.IOException;

              class Test {
                  void method(File file) throws IOException {
                      POIFSFileSystem fs = new POIFSFileSystem(file);
                  }
              }
              """
          )
        );
    }

    @Test
    void opoifsToPoifs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.poifs.filesystem.OPOIFSFileSystem;

              import java.io.InputStream;
              import java.io.IOException;

              class Test {
                  void method(InputStream is) throws IOException {
                      OPOIFSFileSystem fs = new OPOIFSFileSystem(is);
                  }
              }
              """,
            """
              import java.io.InputStream;

              import java.io.IOException;

              import org.apache.poi.poifs.filesystem.POIFSFileSystem;

              class Test {
                  void method(InputStream is) throws IOException {
                      POIFSFileSystem fs = new POIFSFileSystem(is);
                  }
              }
              """
          )
        );
    }

    @Test
    void intCodesBecomeThePlainAccessors() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3.15")),
          //language=java
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  String read(Cell cell, HSSFCell hssfCell, Hyperlink link, Row row, int code) {
                      int type = cell.getCellType();
                      row.createCell(0, type);
                      row.createCell(1, code);
                      if (link.getType() == Hyperlink.LINK_URL) {
                          return "link";
                      }
                      System.out.println(hssfCell.getCellType() + cell.getCachedFormulaResultType());
                      switch (cell.getCachedFormulaResultType()) {
                          case Cell.CELL_TYPE_STRING:
                              return cell.getStringCellValue();
                          default:
                              return null;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  String read(Cell cell, HSSFCell hssfCell, Hyperlink link, Row row, int code) {
                      CellType type = cell.getCellType();
                      row.createCell(0, type);
                      row.createCell(1, CellType.forInt(code));
                      if (link.getType() == HyperlinkType.URL) {
                          return "link";
                      }
                      System.out.println(hssfCell.getCellType().getCode() + cell.getCachedFormulaResultType().getCode());
                      switch (cell.getCachedFormulaResultType()) {
                          case STRING:
                              return cell.getStringCellValue();
                          default:
                              return null;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void intCodesOfFormulaEvaluationBecomeThePlainAccessors() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3.15")),
          //language=java
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellValue;
              import org.apache.poi.ss.usermodel.FormulaEvaluator;

              class Test {
                  boolean method(Cell cell, CellValue value, FormulaEvaluator evaluator) {
                      evaluator.evaluateFormulaCell(cell);
                      return evaluator.evaluateFormulaCell(cell) == 0 || value.getCellType() == 1;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.CellValue;
              import org.apache.poi.ss.usermodel.FormulaEvaluator;

              class Test {
                  boolean method(Cell cell, CellValue value, FormulaEvaluator evaluator) {
                      evaluator.evaluateFormulaCell(cell);
                      return evaluator.evaluateFormulaCell(cell) == CellType.NUMERIC || value.getCellType() == CellType.STRING;
                  }
              }
              """
          )
        );
    }

    @Test
    void enumAccessorsDeprecatedIn4BecomeThePlainOnes() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3.15")),
          //language=java
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(HSSFCell cell, Hyperlink link) {
                      return cell.getCellTypeEnum() == CellType.STRING || link.getTypeEnum() == HyperlinkType.URL;
                  }
              }
              """,
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(HSSFCell cell, Hyperlink link) {
                      return cell.getCellType() == CellType.STRING || link.getType() == HyperlinkType.URL;
                  }
              }
              """
          )
        );
    }

    @Test
    void upgradePoiVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi</artifactId>
                          <version>3.17</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void upgradeOoxmlSchemasVersion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml-schemas</artifactId>
                          <version>3.17</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.poi</groupId>
                          <artifactId>poi-ooxml-schemas</artifactId>
                          <version>4.1.2</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}

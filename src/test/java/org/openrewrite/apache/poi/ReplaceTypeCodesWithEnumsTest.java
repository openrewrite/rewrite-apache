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

@SuppressWarnings({"deprecation", "ConstantValue", "UnusedAssignment", "SwitchStatementWithTooFewBranches", "RedundantSuppression"})
class ReplaceTypeCodesWithEnumsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceTypeCodesWithEnums())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "poi-3.15"));
    }

    @DocumentExample
    @Test
    void switchLabelsAreUnqualified() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  String method(Cell cell) {
                      switch (cell.getCellType()) {
                          case Cell.CELL_TYPE_STRING:
                              return cell.getStringCellValue();
                          case Cell.CELL_TYPE_FORMULA:
                              return cell.getCellFormula();
                          default:
                              return null;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  String method(Cell cell) {
                      switch (cell.getCellTypeEnum()) {
                          case STRING:
                              return cell.getStringCellValue();
                          case FORMULA:
                              return cell.getCellFormula();
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
    void switchLabelsFromInheritedConstantsAndLiterals() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;

              class Test {
                  int method(HSSFCell cell) {
                      switch (cell.getCellType()) {
                          case HSSFCell.CELL_TYPE_BLANK:
                              return 1;
                          case 4:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;

              class Test {
                  int method(HSSFCell cell) {
                      switch (cell.getCellTypeEnum()) {
                          case BLANK:
                              return 1;
                          case BOOLEAN:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void switchExpressionWithArrows() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      return switch (cell.getCachedFormulaResultType()) {
                          case Cell.CELL_TYPE_STRING -> 1;
                          case Cell.CELL_TYPE_NUMERIC, Cell.CELL_TYPE_BOOLEAN -> 2;
                          default -> 3;
                      };
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      return switch (cell.getCachedFormulaResultTypeEnum()) {
                          case STRING -> 1;
                          case NUMERIC, BOOLEAN -> 2;
                          default -> 3;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void switchWithLabelNoEnumConstantHasStaysOnTheCode() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      switch (cell.getCellType()) {
                          case Cell.CELL_TYPE_STRING:
                              return 1;
                          case 9:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      switch (cell.getCellTypeEnum().getCode()) {
                          case 1:
                              return 1;
                          case 9:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void comparisons() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  boolean method(Cell cell, Cell other) {
                      return cell.getCellType() == Cell.CELL_TYPE_STRING ||
                             Cell.CELL_TYPE_BLANK != cell.getCellType() ||
                             cell.getCellType() == 0 ||
                             1 == cell.getCellType() ||
                             cell.getCellType() == other.getCellType();
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell, Cell other) {
                      return cell.getCellTypeEnum() == CellType.STRING ||
                             CellType.BLANK != cell.getCellTypeEnum() ||
                             cell.getCellTypeEnum() == CellType.NUMERIC ||
                             CellType.STRING == cell.getCellTypeEnum() ||
                             cell.getCellTypeEnum() == other.getCellTypeEnum();
                  }
              }
              """
          )
        );
    }

    @Test
    void comparisonsThatStayOnTheCode() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  boolean method(Cell cell, int limit) {
                      return cell.getCellType() > 1 || cell.getCellType() == 9 || cell.getCellType() == limit;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  boolean method(Cell cell, int limit) {
                      return cell.getCellTypeEnum().getCode() > 1 || cell.getCellTypeEnum().getCode() == 9 || cell.getCellTypeEnum().getCode() == limit;
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Cell cell, Row row) {
                      final int type = cell.getCellType();
                      if (type == Cell.CELL_TYPE_FORMULA) {
                          row.createCell(0, type);
                      }
                      switch (type) {
                          case Cell.CELL_TYPE_ERROR:
                              cell.setCellType(type);
                              break;
                          default:
                              System.out.println("type " + type);
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Cell cell, Row row) {
                      final CellType type = cell.getCellTypeEnum();
                      if (type == CellType.FORMULA) {
                          row.createCell(0, type);
                      }
                      switch (type) {
                          case ERROR:
                              cell.setCellType(type);
                              break;
                          default:
                              System.out.println("type " + type);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariableAssignedLaterAndFromAnother() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  boolean method(Cell cell) {
                      int type = -1;
                      int copy;
                      type = cell.getCellType();
                      copy = type;
                      return copy == 0;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell) {
                      CellType type = CellType._NONE;
                      CellType copy;
                      type = cell.getCellTypeEnum();
                      copy = type;
                      return copy == CellType.NUMERIC;
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariableUsedAsNumberStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      int type = cell.getCellType();
                      int blank = Cell.CELL_TYPE_BLANK;
                      int first = cell.getCellType(), second = 2;
                      return type + blank + first + second;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  int method(Cell cell) {
                      int type = cell.getCellTypeEnum().getCode();
                      int blank = CellType.BLANK.getCode();
                      int first = cell.getCellTypeEnum().getCode(), second = 2;
                      return type + blank + first + second;
                  }
              }
              """
          )
        );
    }

    @Test
    void variableRejectedLateStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      int first = 0;
                      int read = first + 1;
                      first = cell.getCellType();
                      int second = cell.getCellType();
                      return read + second;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      int first = 0;
                      int read = first + 1;
                      first = cell.getCellTypeEnum().getCode();
                      int second = cell.getCellTypeEnum().getCode();
                      return read + second;
                  }
              }
              """
          )
        );
    }

    @Test
    void sameNameInSiblingBlocks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell, boolean flag) {
                      if (flag) {
                          int value = cell.getCellType();
                          cell.setCellType(value);
                      } else {
                          long value = 5;
                          System.out.println(value == 3);
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell, boolean flag) {
                      if (flag) {
                          int value = cell.getCellTypeEnum().getCode();
                          cell.setCellType(CellType.forInt(value));
                      } else {
                          long value = 5;
                          System.out.println(value == 3);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loopVariableWithTheSameName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      {
                          int i = cell.getCellType();
                          cell.setCellType(i);
                      }
                      for (int i = 0; i == 1; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      {
                          int i = cell.getCellTypeEnum().getCode();
                          cell.setCellType(CellType.forInt(i));
                      }
                      for (int i = 0; i == 1; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocReferences() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  /**
                   * Compares with {@link Cell#CELL_TYPE_STRING}, see {@link Cell#getCellType()}.
                   *
                   * @see Cell#CELL_TYPE_BLANK
                   */
                  boolean method(Cell cell) {
                      return cell.getCellType() == Cell.CELL_TYPE_STRING;
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  /**
                   * Compares with {@link Cell#CELL_TYPE_STRING}, see {@link Cell#getCellType()}.
                   *
                   * @see Cell#CELL_TYPE_BLANK
                   */
                  boolean method(Cell cell) {
                      return cell.getCellTypeEnum() == CellType.STRING;
                  }
              }
              """
          )
        );
    }

    @Test
    void hyperlinkCodesThatStayInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.ss.usermodel.CreationHelper;

              class Test {
                  int method(Hyperlink link, CreationHelper helper, int code) {
                      helper.createHyperlink(code);
                      switch (code) {
                          case Hyperlink.LINK_URL:
                              return 1;
                          default:
                              return link.getType() + Hyperlink.LINK_FILE;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.ss.usermodel.CreationHelper;

              class Test {
                  int method(Hyperlink link, CreationHelper helper, int code) {
                      helper.createHyperlink(HyperlinkType.values()[code]);
                      switch (HyperlinkType.values()[code]) {
                          case URL:
                              return 1;
                          default:
                              return link.getTypeEnum().ordinal() + HyperlinkType.FILE.ordinal();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ternary() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell, Cell other, boolean flag) {
                      int type = flag ? cell.getCellType() : Cell.CELL_TYPE_BLANK;
                      other.setCellType(type);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell, Cell other, boolean flag) {
                      CellType type = flag ? cell.getCellTypeEnum() : CellType.BLANK;
                      other.setCellType(type);
                  }
              }
              """
          )
        );
    }

    @Test
    void ternaryOfLiterals() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  boolean method(Cell cell, boolean flag) {
                      cell.setCellType(flag ? 1 : 0);
                      cell.setCellType(flag ? 1 : 9);
                      return cell.getCellType() == (flag ? 1 : 0);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell, boolean flag) {
                      cell.setCellType(flag ? CellType.STRING : CellType.NUMERIC);
                      cell.setCellType(CellType.forInt(flag ? 1 : 9));
                      return cell.getCellTypeEnum() == (flag ? CellType.STRING : CellType.NUMERIC);
                  }
              }
              """
          )
        );
    }

    @Test
    void assignmentUsedAsValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      int type;
                      if ((type = cell.getCellType()) == Cell.CELL_TYPE_STRING) {
                          cell.setCellType(type);
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell) {
                      CellType type;
                      if ((type = cell.getCellTypeEnum()) == CellType.STRING) {
                          cell.setCellType(type);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void assignmentUsedAsNumberStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      int type;
                      return Math.abs(type = cell.getCellType());
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      int type;
                      return Math.abs(type = cell.getCellTypeEnum().getCode());
                  }
              }
              """
          )
        );
    }

    @Test
    void finalConstantStaysConstantExpression() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  private static final int TEXT = Cell.CELL_TYPE_STRING;

                  int method(int code) {
                      switch (code) {
                          case TEXT:
                              return 1;
                          default:
                              return 2;
                      }
                  }
              }
              """,
            """
              class Test {
                  private static final int TEXT = 1;

                  int method(int code) {
                      switch (code) {
                          case TEXT:
                              return 1;
                          default:
                              return 2;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void annotationValueStaysConstantExpression() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  @interface Column {
                      int type();
                  }

                  @Column(type = Cell.CELL_TYPE_STRING)
                  String name;
              }
              """,
            """
              class Test {
                  @interface Column {
                      int type();
                  }

                  @Column(type = 1)
                  String name;
              }
              """
          )
        );
    }

    @Test
    void arrowCaseBodiesAreValuesNotLabels() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell, int code) {
                      return switch (code) {
                          case 1 -> Cell.CELL_TYPE_BLANK;
                          case 2 -> cell.getCellType();
                          default -> 0;
                      };
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  int method(Cell cell, int code) {
                      return switch (code) {
                          case 1 -> CellType.BLANK.getCode();
                          case 2 -> cell.getCellTypeEnum().getCode();
                          default -> 0;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void createCell() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.hssf.usermodel.HSSFRow;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Row row, HSSFRow hssfRow, Cell cell) {
                      row.createCell(0, 1);
                      row.createCell(1, Cell.CELL_TYPE_STRING);
                      row.createCell(2, cell.getCellType());
                      hssfRow.createCell(3, HSSFCell.CELL_TYPE_NUMERIC);
                      hssfRow.createCell(4, 3);
                  }
              }
              """,
            """
              import org.apache.poi.hssf.usermodel.HSSFRow;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  void method(Row row, HSSFRow hssfRow, Cell cell) {
                      row.createCell(0, CellType.STRING);
                      row.createCell(1, CellType.STRING);
                      row.createCell(2, cell.getCellTypeEnum());
                      hssfRow.createCell(3, CellType.NUMERIC);
                      hssfRow.createCell(4, CellType.BLANK);
                  }
              }
              """
          )
        );
    }

    @Test
    void setCellTypeOnImplementation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;

              class Test {
                  void method(HSSFCell cell, HSSFCell other) {
                      cell.setCellType(1);
                      cell.setCellType(other.getCellType());
                  }
              }
              """,
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(HSSFCell cell, HSSFCell other) {
                      cell.setCellType(CellType.STRING);
                      cell.setCellType(other.getCellTypeEnum());
                  }
              }
              """
          )
        );
    }

    @Test
    void hyperlinks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.ss.usermodel.CreationHelper;

              class Test {
                  void method(Hyperlink link, CreationHelper helper) {
                      int type = link.getType();
                      helper.createHyperlink(type);
                      helper.createHyperlink(Hyperlink.LINK_FILE);
                      helper.createHyperlink(1);
                      if (link.getType() == Hyperlink.LINK_URL) {
                          helper.createHyperlink(link.getType());
                      }
                      switch (type) {
                          case Hyperlink.LINK_EMAIL:
                              break;
                          case 2:
                              break;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.ss.usermodel.CreationHelper;

              class Test {
                  void method(Hyperlink link, CreationHelper helper) {
                      HyperlinkType type = link.getTypeEnum();
                      helper.createHyperlink(type);
                      helper.createHyperlink(HyperlinkType.FILE);
                      helper.createHyperlink(HyperlinkType.URL);
                      if (link.getTypeEnum() == HyperlinkType.URL) {
                          helper.createHyperlink(link.getTypeEnum());
                      }
                      switch (type) {
                          case EMAIL:
                              break;
                          case DOCUMENT:
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void formulaEvaluatorAndCellValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellValue;
              import org.apache.poi.ss.usermodel.FormulaEvaluator;

              class Test {
                  void method(FormulaEvaluator evaluator, Cell cell) {
                      evaluator.evaluateFormulaCell(cell);
                      if (evaluator.evaluateFormulaCell(cell) == Cell.CELL_TYPE_STRING) {
                          CellValue value = evaluator.evaluate(cell);
                          switch (value.getCellType()) {
                              case Cell.CELL_TYPE_BOOLEAN:
                                  break;
                          }
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.CellValue;
              import org.apache.poi.ss.usermodel.FormulaEvaluator;

              class Test {
                  void method(FormulaEvaluator evaluator, Cell cell) {
                      evaluator.evaluateFormulaCell(cell);
                      if (evaluator.evaluateFormulaCellEnum(cell) == CellType.STRING) {
                          CellValue value = evaluator.evaluate(cell);
                          switch (value.getCellTypeEnum()) {
                              case BOOLEAN:
                                  break;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void overloadedPrivateMethodStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  String value(Cell cell) {
                      return value(cell, cell.getCellType());
                  }

                  private String value(Cell cell, int type) {
                      switch (type) {
                          case Cell.CELL_TYPE_STRING:
                              return cell.getStringCellValue();
                          case Cell.CELL_TYPE_FORMULA:
                              return value(cell, cell.getCachedFormulaResultType());
                          default:
                              return null;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  String value(Cell cell) {
                      return value(cell, cell.getCellTypeEnum().getCode());
                  }

                  private String value(Cell cell, int type) {
                      switch (CellType.forInt(type)) {
                          case STRING:
                              return cell.getStringCellValue();
                          case FORMULA:
                              return value(cell, cell.getCachedFormulaResultTypeEnum().getCode());
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
    void privateMethodParameterBecomesTheEnum() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  String value(Cell cell) {
                      return read(cell, cell.getCellType());
                  }

                  private String read(Cell cell, int type) {
                      if (type == Cell.CELL_TYPE_FORMULA) {
                          return read(cell, cell.getCachedFormulaResultType());
                      }
                      return type == 1 ? cell.getStringCellValue() : null;
                  }

                  private void blank(Cell cell) {
                      read(cell, 3);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  String value(Cell cell) {
                      return read(cell, cell.getCellTypeEnum());
                  }

                  private String read(Cell cell, CellType type) {
                      if (type == CellType.FORMULA) {
                          return read(cell, cell.getCachedFormulaResultTypeEnum());
                      }
                      return type == CellType.STRING ? cell.getStringCellValue() : null;
                  }

                  private void blank(Cell cell) {
                      read(cell, CellType.BLANK);
                  }
              }
              """
          )
        );
    }

    @Test
    void privateMethodCalledWithUnknownIntStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell, int unknown) {
                      read(cell.getCellType());
                      read(unknown);
                  }

                  private void read(int type) {
                      System.out.println(type == Cell.CELL_TYPE_STRING);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  void method(Cell cell, int unknown) {
                      read(cell.getCellTypeEnum().getCode());
                      read(unknown);
                  }

                  private void read(int type) {
                      System.out.println(type == CellType.STRING.getCode());
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterOfProtectedMethodStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  protected Cell createCell(Row row, int col, int type) {
                      Cell cell = row.createCell(col, type);
                      cell.setCellType(type);
                      return cell;
                  }

                  void method(Row row) {
                      createCell(row, 0, Cell.CELL_TYPE_NUMERIC);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  protected Cell createCell(Row row, int col, int type) {
                      Cell cell = row.createCell(col, CellType.forInt(type));
                      cell.setCellType(CellType.forInt(type));
                      return cell;
                  }

                  void method(Row row) {
                      createCell(row, 0, CellType.NUMERIC.getCode());
                  }
              }
              """
          )
        );
    }

    @Test
    void switchOnIntParameterOfPublicMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  public int method(int type) {
                      switch (type) {
                          case Cell.CELL_TYPE_STRING:
                              return 1;
                          case 0:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  public int method(int type) {
                      switch (CellType.forInt(type)) {
                          case STRING:
                              return 1;
                          case NUMERIC:
                              return 2;
                          default:
                              return 3;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void boxedCodesFromCollections() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              import java.util.Map;

              class Test {
                  void method(Cell cell, Map<Integer, Integer> forced) {
                      Integer type = forced.get(cell.getColumnIndex());
                      cell.setCellType(type);
                      forced.put(0, cell.getCellType());
                      forced.put(1, Cell.CELL_TYPE_STRING);
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              import java.util.Map;

              class Test {
                  void method(Cell cell, Map<Integer, Integer> forced) {
                      Integer type = forced.get(cell.getColumnIndex());
                      cell.setCellType(CellType.forInt(type));
                      forced.put(0, cell.getCellTypeEnum().getCode());
                      forced.put(1, CellType.STRING.getCode());
                  }
              }
              """
          )
        );
    }

    @Test
    void returnedFromIntMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.hssf.usermodel.HSSFCell;
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  int method(Cell cell) {
                      if (cell == null) {
                          return HSSFCell.CELL_TYPE_BLANK;
                      }
                      return cell.getCellType();
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  int method(Cell cell) {
                      if (cell == null) {
                          return CellType.BLANK.getCode();
                      }
                      return cell.getCellTypeEnum().getCode();
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldStaysInt() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  private int type = Cell.CELL_TYPE_BLANK;

                  void method(Cell cell) {
                      type = cell.getCellType();
                      if (type == Cell.CELL_TYPE_STRING) {
                          cell.setCellType(type);
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  private int type = CellType.BLANK.getCode();

                  void method(Cell cell) {
                      type = cell.getCellTypeEnum().getCode();
                      if (type == CellType.STRING.getCode()) {
                          cell.setCellType(CellType.forInt(type));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void assertions() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "poi-3.15")
            .dependsOn(
              //language=java
              """
                package org.junit.jupiter.api;
                public class Assertions {
                    public static void assertEquals(int expected, int actual) {}
                    public static void assertEquals(Object expected, Object actual) {}
                    public static void assertEquals(int expected, int actual, String message) {}
                    public static void assertEquals(Object expected, Object actual, String message) {}
                }
                """
            )),
          //language=java
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class Test {
                  void method(Cell cell, Cell other, int expected) {
                      assertEquals(1, cell.getCellType());
                      assertEquals(Cell.CELL_TYPE_STRING, cell.getCellType(), "message");
                      assertEquals(cell.getCellType(), other.getCellType());
                      assertEquals(expected, cell.getCellType());
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class Test {
                  void method(Cell cell, Cell other, int expected) {
                      assertEquals(CellType.STRING, cell.getCellTypeEnum());
                      assertEquals(CellType.STRING, cell.getCellTypeEnum(), "message");
                      assertEquals(cell.getCellTypeEnum(), other.getCellTypeEnum());
                      assertEquals(expected, cell.getCellTypeEnum().getCode());
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImportOfConstant() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BLANK;
              import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING;

              class Test {
                  boolean method(Cell cell) {
                      switch (cell.getCellType()) {
                          case CELL_TYPE_STRING:
                              return true;
                          default:
                              return cell.getCellType() == CELL_TYPE_BLANK;
                      }
                  }
              }
              """,
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell) {
                      switch (cell.getCellTypeEnum()) {
                          case STRING:
                              return true;
                          default:
                              return cell.getCellTypeEnum() == CellType.BLANK;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enumNameTakenByAnotherType() {
        //language=java
        rewriteRun(
          java(
            """
              package com.acme;

              public class CellType {
                  public static final int CELL_TYPE_FORMULA = 7;
              }
              """
          ),
          java(
            """
              package com.acme;

              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      int own = CellType.CELL_TYPE_FORMULA;
                      int type = cell.getCellType();
                      if (type == Cell.CELL_TYPE_STRING || own == cell.getCellType()) {
                          cell.setCellType(own);
                      }
                  }
              }
              """,
            """
              package com.acme;

              import org.apache.poi.ss.usermodel.Cell;

              class Test {
                  void method(Cell cell) {
                      int own = CellType.CELL_TYPE_FORMULA;
                      org.apache.poi.ss.usermodel.CellType type = cell.getCellTypeEnum();
                      if (type == org.apache.poi.ss.usermodel.CellType.STRING || own == cell.getCellTypeEnum().getCode()) {
                          cell.setCellType(org.apache.poi.ss.usermodel.CellType.forInt(own));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ownConstantsWithTheSameNames() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;

              interface Grid {
                  int CELL_TYPE_STRING = 1;
                  int getType();
              }

              class Test {
                  static final int CELL_TYPE_ERROR = 5;

                  boolean method(Grid grid, Cell cell) {
                      return grid.getType() == Grid.CELL_TYPE_STRING || grid.getType() == CELL_TYPE_ERROR || cell == null;
                  }
              }
              """
          )
        );
    }

    @Test
    void enumAccessorsStay() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.common.usermodel.Hyperlink;
              import org.apache.poi.common.usermodel.HyperlinkType;
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  boolean method(Cell cell, Hyperlink link, Row row) {
                      row.createCell(0, CellType.STRING);
                      cell.setCellType(CellType.BLANK);
                      switch (cell.getCellTypeEnum()) {
                          case STRING:
                              return true;
                          default:
                              return link.getTypeEnum() == HyperlinkType.URL;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void codeAlreadyOnPoi4() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.apache.poi.ss.usermodel;
              public enum CellType { NUMERIC, STRING }
              """,
            //language=java
            """
              package org.apache.poi.ss.usermodel;
              public interface Cell {
                  CellType getCellType();
                  void setCellType(CellType type);
              }
              """
          )),
          //language=java
          java(
            """
              import org.apache.poi.ss.usermodel.Cell;
              import org.apache.poi.ss.usermodel.CellType;

              class Test {
                  boolean method(Cell cell) {
                      CellType type = cell.getCellType();
                      switch (type) {
                          case STRING:
                              cell.setCellType(cell.getCellType());
                              return true;
                          default:
                              return cell.getCellType() == CellType.NUMERIC;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.poi.ss.usermodel.Row;

              class Test {
                  interface Cell {
                      int getCellType();
                  }

                  int method(Cell cell, Row row) {
                      switch (cell.getCellType()) {
                          case 1:
                              return row.getRowNum();
                          default:
                              return cell.getCellType() == 0 ? 1 : 2;
                      }
                  }
              }
              """
          )
        );
    }
}

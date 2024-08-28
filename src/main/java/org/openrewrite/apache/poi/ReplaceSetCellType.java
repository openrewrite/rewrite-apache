package org.openrewrite.apache.poi;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.openrewrite.java.template.RecipeDescriptor;

public class ReplaceSetCellType {
    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeNumeric {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(0);
        }
        
        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_NUMERIC);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.NUMERIC);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeString {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(1);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_STRING);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.STRING);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeFormula {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(2);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_FORMULA);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.FORMULA);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeBlank {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(3);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_BLANK);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.BLANK);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeBoolean {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(4);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_BOOLEAN);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.BOOLEAN);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`",
            description = "Replace `Cell.setCellType(int)` with `Cell.setCellType(CellType)`")
    static class ReplaceSetCellTypeError {
        @BeforeTemplate
        void beforeInt(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(5);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(Cell.CELL_TYPE_ERROR);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Cell cell) {
            cell.setCellType(CellType.ERROR);
        }
    }
}

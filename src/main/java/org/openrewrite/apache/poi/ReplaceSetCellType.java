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

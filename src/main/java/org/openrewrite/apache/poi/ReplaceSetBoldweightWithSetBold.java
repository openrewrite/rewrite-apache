package org.openrewrite.apache.poi;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;


public class ReplaceSetBoldweightWithSetBold {
    @RecipeDescriptor(
            name = "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)`",
            description = "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)`")
    static class ReplaceSetBoldweightNormalWithSetBoldFalse {
        @BeforeTemplate
        void beforeShort(org.apache.poi.ss.usermodel.Font font) {
            font.setBoldweight((short) 400);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Font font) {
            font.setBoldweight(font.BOLDWEIGHT_NORMAL);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Font font) {
            font.setBold(false);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)`",
            description = "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)`")
    static class ReplaceSetBoldweightBoldWithSetBoldTrue {
        @BeforeTemplate
        void beforeShort(org.apache.poi.ss.usermodel.Font font) {
            font.setBoldweight((short) 700);
        }

        @BeforeTemplate
        void beforeEnum(org.apache.poi.ss.usermodel.Font font) {
            font.setBoldweight(font.BOLDWEIGHT_BOLD);
        }

        @AfterTemplate
        void after(org.apache.poi.ss.usermodel.Font font) {
            font.setBold(true);
        }
    }
}

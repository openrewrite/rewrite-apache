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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.poi.ss.usermodel.Font;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)`",
        description = "Replace `Font.setBoldweight(short)` or equivalent with `Font.setBold(boolean)`.")
@SuppressWarnings({"AccessStaticViaInstance", "deprecation"})
public class ReplaceSetBoldweightWithSetBold {

    @RecipeDescriptor(
            name = "Replace `Font.setBoldweight(Font.BOLDWEIGHT_NORMAL)` with `Font.setBold(false)`",
            description = "Replace `Font.setBoldweight(Font.BOLDWEIGHT_NORMAL)` or equivalent with `Font.setBold(false)`.")
    static class ReplaceSetBoldweightNormalWithSetBoldFalse {
        @BeforeTemplate
        void beforeShort(Font font) {
            font.setBoldweight((short) 400);
        }

        @BeforeTemplate
        void beforeField(Font font) {
            font.setBoldweight(font.BOLDWEIGHT_NORMAL);
        }

        @BeforeTemplate
        void beforeStaticField(Font font) {
            font.setBoldweight(Font.BOLDWEIGHT_NORMAL);
        }

        @AfterTemplate
        void after(Font font) {
            font.setBold(false);
        }
    }

    @RecipeDescriptor(
            name = "Replace `Font.setBoldweight(Font.BOLDWEIGHT_BOLD)` with `Font.setBold(true)`",
            description = "Replace `Font.setBoldweight(Font.BOLDWEIGHT_BOLD)` or equivalent with `Font.setBold(true)`.")
    static class ReplaceSetBoldweightBoldWithSetBoldTrue {
        @BeforeTemplate
        void beforeShort(Font font) {
            font.setBoldweight((short) 700);
        }

        @BeforeTemplate
        void beforeField(Font font) {
            font.setBoldweight(font.BOLDWEIGHT_BOLD);
        }

        @BeforeTemplate
        void beforeStaticField(Font font) {
            font.setBoldweight(Font.BOLDWEIGHT_BOLD);
        }

        @AfterTemplate
        void after(Font font) {
            font.setBold(true);
        }
    }
}

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

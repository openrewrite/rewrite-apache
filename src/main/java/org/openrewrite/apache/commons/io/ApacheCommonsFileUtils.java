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
package org.openrewrite.apache.commons.io;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.commons.io.FileUtils;
import org.openrewrite.java.template.RecipeDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ApacheCommonsFileUtils {
    @RecipeDescriptor(
            name = "Replace `FileUtils.getFile(String...)` with JDK provided API",
            description = "Replace Apache Commons `FileUtils.getFile(String... name)` with JDK provided API.")
    public static class GetFile {
        @BeforeTemplate
        File before(String name) {
            return FileUtils.getFile(name);
        }

        @AfterTemplate
        File after(String name) {
            return new File(name);
        }
    }

// NOTE: java: reference to compile is ambiguous; methods P3 & F3 match
//    public static class Write {
//        @BeforeTemplate
//        void before(File file, CharSequence data, Charset cs) throws Exception {
//            FileUtils.write(file, data, cs);
//        }
//
//        @AfterTemplate
//        void after(File file, CharSequence data, Charset cs) throws Exception {
//            Files.write(file.toPath(), Arrays.asList(data), cs);
//        }
//    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.writeStringToFile(File, String)` with JDK provided API",
            description = "Replace Apache Commons `FileUtils.writeStringToFile(File file, String data)` with JDK provided API.")
    @SuppressWarnings("deprecation")
    public static class WriteStringToFile {
        @BeforeTemplate
        void before(File a, String s) throws Exception {
            FileUtils.writeStringToFile(a, s);
        }

        @AfterTemplate
        void after(File a, String s) throws Exception {
            Files.write(a.toPath(), s.getBytes());
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.readFileToString(File)` with `FileUtils.readFileToString(File, StandardCharsets.UTF_8)`",
            description = "Replace deprecated `FileUtils.readFileToString(File)` with `FileUtils.readFileToString(File, StandardCharsets.UTF_8)`.")
    @SuppressWarnings("deprecation")
    public static class ReadFileToStringWithCharset {
        @BeforeTemplate
        String before(File file) throws IOException {
            return FileUtils.readFileToString(file);
        }

        @AfterTemplate
        String after(File file) throws IOException {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.readLines(File)` with `FileUtils.readLines(File, StandardCharsets.UTF_8)`",
            description = "Replace deprecated `FileUtils.readLines(File)` with `FileUtils.readLines(File, StandardCharsets.UTF_8)`.")
    @SuppressWarnings("deprecation")
    public static class ReadLinesWithCharset {
        @BeforeTemplate
        List<String> before(File file) throws IOException {
            return FileUtils.readLines(file);
        }

        @AfterTemplate
        List<String> after(File file) throws IOException {
            return FileUtils.readLines(file, StandardCharsets.UTF_8);
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.write(File, CharSequence)` with `FileUtils.write(File, CharSequence, StandardCharsets.UTF_8, false)`",
            description = "Replace deprecated `FileUtils.write(File, CharSequence)` with `FileUtils.write(File, CharSequence, StandardCharsets.UTF_8, false)`.")
    @SuppressWarnings("deprecation")
    public static class WriteWithCharset {
        @BeforeTemplate
        void before(File file, CharSequence data) throws IOException {
            FileUtils.write(file, data);
        }

        @AfterTemplate
        void after(File file, CharSequence data) throws IOException {
            FileUtils.write(file, data, StandardCharsets.UTF_8, false);
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.write(File, CharSequence, boolean)` with `FileUtils.write(File, CharSequence, StandardCharsets.UTF_8, boolean)`",
            description = "Replace deprecated `FileUtils.write(File, CharSequence, boolean)` with `FileUtils.write(File, CharSequence, StandardCharsets.UTF_8, boolean)`.")
    @SuppressWarnings("deprecation")
    public static class WriteAppendWithCharset {
        @BeforeTemplate
        void before(File file, CharSequence data, boolean append) throws IOException {
            FileUtils.write(file, data, append);
        }

        @AfterTemplate
        void after(File file, CharSequence data, boolean append) throws IOException {
            FileUtils.write(file, data, StandardCharsets.UTF_8, append);
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.writeStringToFile(File, String, boolean)` with `FileUtils.writeStringToFile(File, String, StandardCharsets.UTF_8, boolean)`",
            description = "Replace deprecated `FileUtils.writeStringToFile(File, String, boolean)` with `FileUtils.writeStringToFile(File, String, StandardCharsets.UTF_8, boolean)`.")
    @SuppressWarnings("deprecation")
    public static class WriteStringToFileAppendWithCharset {
        @BeforeTemplate
        void before(File file, String data, boolean append) throws IOException {
            FileUtils.writeStringToFile(file, data, append);
        }

        @AfterTemplate
        void after(File file, String data, boolean append) throws IOException {
            FileUtils.writeStringToFile(file, data, StandardCharsets.UTF_8, append);
        }
    }
}

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
package org.openrewrite.codehaus.plexus;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.codehaus.plexus.util.FileUtils;
import org.openrewrite.java.template.RecipeDescriptor;

import java.io.File;

class PlexusFileUtils {

    // https://github.com/codehaus-plexus/plexus-utils/blob/master/src/main/java/org/codehaus/plexus/util/StringUtils.java

    @RecipeDescriptor(
            name = "Replace `FileUtils.deleteDirectory(File)` with JDK provided API",
            description = "Replace Plexus `FileUtils.deleteDirectory(File directory)` with JDK provided API.")
    static class DeleteDirectoryFile {
        @BeforeTemplate
        void before(File dir) throws Exception {
            FileUtils.deleteDirectory(dir);
        }

        @AfterTemplate
        void after(File dir) throws Exception {
            org.apache.commons.io.FileUtils.deleteDirectory(dir);
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.deleteDirectory(String)` with JDK provided API",
            description = "Replace Plexus `FileUtils.deleteDirectory(String directory)` with JDK provided API.")
    static class DeleteDirectoryString {
        @BeforeTemplate
        void before(String dir) throws Exception {
            FileUtils.deleteDirectory(dir);
        }

        @AfterTemplate
        void after(String dir) throws Exception {
            org.apache.commons.io.FileUtils.deleteDirectory(new File(dir));
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.fileExists(String)` with JDK provided API",
            description = "Replace Plexus `FileUtils.fileExists(String fileName)` with JDK provided API.")
    static class FileExistsString {
        @BeforeTemplate
        boolean before(String fileName) {
            return FileUtils.fileExists(fileName);
        }

        @AfterTemplate
        boolean after(String fileName) {
            return new File(fileName).exists();
        }
    }

    @RecipeDescriptor(
            name = "Replace `FileUtils.getFile(String)` with JDK provided API",
            description = "Replace Plexus `FileUtils.getFile(String fileName)` with JDK provided API.")
    static class GetFile {
        @BeforeTemplate
        File before(String fileName) {
            return FileUtils.getFile(fileName);
        }

        @AfterTemplate
        File after(String fileName) {
            return new File(fileName);
        }
    }
}

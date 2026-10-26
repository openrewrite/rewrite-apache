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
package org.openrewrite.apache.commons.collections;

import org.apache.commons.collections4.CollectionUtils;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

final class CardinalityRuntimeSupport {
    private CardinalityRuntimeSupport() {
    }

    static void assertEquivalent(String before, String after) {
        try {
            Path commons41 = Paths.get(System.getProperty("commonsCollections41Jar"));
            Path commons44 = Paths.get(CollectionUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path jar : Arrays.asList(commons41, commons44)) {
                Path root = Paths.get("build", "cardinality-runtime-proof", jar.getFileName().toString());
                String baseline = compileAndRun(root.resolve("baseline"), before, jar);
                String candidate = compileAndRun(root.resolve("candidate"), after, jar);
                assertThat(baseline).isEqualTo("2|1|NullPointerException|NH|ArrayIndexOutOfBoundsException|qualifier");
                assertThat(candidate).isEqualTo(baseline);
                String receipt = "jar=" + jar + "\njarSHA256=" + sha256(Files.readAllBytes(jar)) +
                        "\njavaVersion=" + System.getProperty("java.version") + "\nrelease=8\nbaseline=" + baseline +
                        "\ncandidate=" + candidate + "\nbeforeSHA256=" + sha256(before.getBytes(StandardCharsets.UTF_8)) +
                        "\nafterSHA256=" + sha256(after.getBytes(StandardCharsets.UTF_8)) +
                        "\nbaselineClassSHA256=" + sha256(Files.readAllBytes(root.resolve("baseline/RuntimeExample.class"))) +
                        "\ncandidateClassSHA256=" + sha256(Files.readAllBytes(root.resolve("candidate/RuntimeExample.class"))) + "\n";
                Files.write(root.resolve("result.txt"), receipt.getBytes(StandardCharsets.UTF_8));
                System.out.println(receipt);
            }
        } catch (Exception e) {
            throw new AssertionError("Actual recipe output must compile and preserve behavior against the real Commons jars", e);
        }
    }

    private static String compileAndRun(Path directory, String source, Path jar) throws Exception {
        Files.createDirectories(directory);
        Path sourceFile = directory.resolve("RuntimeExample.java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean compiled = compiler.getTask(null, files, diagnostics,
                    Arrays.asList("--release", "8", "-classpath", jar.toString(), "-d", directory.toString()), null,
                    files.getJavaFileObjects(sourceFile.toFile())).call();
            assertThat(compiled).as("Actual output compiler diagnostics: %s", diagnostics.getDiagnostics()).isTrue();
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL(), jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            return (String) loader.loadClass("RuntimeExample").getMethod("exercise").invoke(null);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}

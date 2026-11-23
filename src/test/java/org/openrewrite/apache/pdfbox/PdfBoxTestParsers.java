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
package org.openrewrite.apache.pdfbox;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses the code a test migrates with PDFBox 2 and the code it expects with PDFBox 3, whose classes have the same
 * names, so that both are type attributed and the expected code is compiled against the API it is written for.
 * <p>
 * The code a recipe leaves alone keeps its PDFBox 2 types, such as {@code PDPageContentStream.beginText()} where
 * PDFBox 3 declares {@code beginText()} on {@code PDAbstractContentStream}, so the types of a changed file can't match
 * what a parser gives the expected code. The expected code still has to compile against PDFBox 3.
 */
final class PdfBoxTestParsers {

    private PdfBoxTestParsers() {
    }

    /**
     * @param otherArtifacts Artifacts of the type table, such as {@code commons-io}, to parse with.
     */
    static RecipeSpec pdfBox2To3(RecipeSpec spec, String... otherArtifacts) {
        return parsers(spec, new String[0], otherArtifacts);
    }

    /**
     * @param testLibraries Artifacts on the test runtime classpath, such as {@code junit-jupiter-api}, to parse with.
     */
    static RecipeSpec pdfBox2To3WithTestLibraries(RecipeSpec spec, String... testLibraries) {
        return parsers(spec, testLibraries);
    }

    private static RecipeSpec parsers(RecipeSpec spec, String[] testLibraries, String... otherArtifacts) {
        return spec
          .parser(parser(testLibraries, otherArtifacts, "pdfbox-2", "fontbox-2", "xmpbox-2"))
          .afterParser(parser(testLibraries, otherArtifacts, "pdfbox-3", "pdfbox-io-3", "fontbox-3", "xmpbox-3"))
          .recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(ParserTypes.Off).build());
    }

    private static JavaParser.Builder<?, ?> parser(String[] testLibraries, String[] otherArtifacts, String... pdfBoxArtifacts) {
        List<String> artifacts = new ArrayList<>(Arrays.asList(pdfBoxArtifacts));
        artifacts.addAll(Arrays.asList(otherArtifacts));
        JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), artifacts.toArray(new String[0]));
        if (testLibraries.length > 0) {
            JavaParser.dependenciesFromClasspath(testLibraries).forEach(parser::addClasspathEntry);
        }
        return parser;
    }
}

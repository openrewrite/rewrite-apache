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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class EmbedNewPdfBoxDependencies extends Recipe {

    /**
     * The dependencies that PDFBox 3.0 artifacts have and their 2.x versions didn't, each with the artifacts that have it.
     */
    private static final Map<String, List<String>> NEEDED_BY = new LinkedHashMap<>();

    static {
        NEEDED_BY.put("pdfbox-io", asList("pdfbox", "fontbox"));
        NEEDED_BY.put("picocli", asList("pdfbox-tools", "pdfbox-debugger"));
        NEEDED_BY.put("commons-io", singletonList("pdfbox-tools"));
    }

    String displayName = "Embed the dependencies PDFBox 3 added in OSGi bundles that embed PDFBox";

    String description = "PDFBox 3.0 moved its I/O classes, such as `RandomAccessRead`, from `pdfbox` and `fontbox` to the new " +
            "`pdfbox-io` artifact, and its command line tools now depend on picocli and Commons IO. A bundle that the Maven " +
            "Bundle Plugin builds with these PDFBox artifacts among the artifact IDs of its `Embed-Dependency` instruction then " +
            "imports packages it doesn't embed, and can't be resolved. This adds each dependency the instruction lacks after " +
            "the first artifact that needs it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"Embed-Dependency".equals(t.getName()) || !t.getValue().isPresent()) {
                    return t;
                }
                String value = t.getValue().get();
                String embedded = value;
                for (Map.Entry<String, List<String>> dependency : NEEDED_BY.entrySet()) {
                    if (artifact(dependency.getKey()).matcher(embedded).find()) {
                        continue;
                    }
                    for (String needing : dependency.getValue()) {
                        String added = embedAfter(embedded, needing, dependency.getKey());
                        if (added != null) {
                            embedded = added;
                            break;
                        }
                    }
                }
                return embedded.equals(value) ? t : t.withValue(embedded);
            }
        };
    }

    /**
     * An artifact ID of an {@code Embed-Dependency} instruction: after the {@code artifactId=} of a clause or a {@code |}
     * that separates its alternatives, or as a clause of its own, which names an artifact ID. The groups are the separator
     * before it, the white space around it, and the separator after it, with the white space after that.
     */
    private static Pattern artifact(String artifactId) {
        return Pattern.compile("(^|[=|,])(\\s*)" + Pattern.quote(artifactId) + "(\\s*)([|;,]|$)(\\s*)");
    }

    /**
     * The instruction with the dependency added as the artifact ID or clause after the given one, spaced as the one
     * after that, or else as the given one is, when the instruction names the given one.
     */
    private static @Nullable String embedAfter(String instruction, String artifactId, String dependency) {
        Matcher m = artifact(artifactId).matcher(instruction);
        if (!m.find()) {
            return null;
        }
        boolean clause = !"=".equals(m.group(1)) && !"|".equals(m.group(1));
        if (clause && ";".equals(m.group(4))) {
            // The attributes after it are the clause's, and adding a clause would take them from it
            return null;
        }
        String space = m.group(4).isEmpty() || ";".equals(m.group(4)) ? m.group(2) : m.group(5);
        return instruction.substring(0, m.end(3)) + (clause ? "," : "|") + space + dependency +
               instruction.substring(m.end(3));
    }
}

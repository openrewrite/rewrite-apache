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
package org.openrewrite.apache.commons.lang;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.openrewrite.java.ShortenFullyQualifiedTypeReferences.modifyOnly;

public class StringEscapeUtilsWriterMethodsToCommonsText extends Recipe {

    private static final String COMMONS_TEXT = "org.apache.commons.text.StringEscapeUtils";

    /**
     * Commons Lang's {@code StringEscapeUtils} methods that write to a {@code Writer},
     * and the Commons Text translator constant that does the same.
     */
    private static final Map<String, String> TRANSLATORS = new LinkedHashMap<>();

    static {
        TRANSLATORS.put("escapeJava", "ESCAPE_JAVA");
        TRANSLATORS.put("escapeJavaScript", "ESCAPE_ECMASCRIPT");
        TRANSLATORS.put("escapeHtml", "ESCAPE_HTML4");
        TRANSLATORS.put("escapeXml", "ESCAPE_XML10");
        TRANSLATORS.put("escapeCsv", "ESCAPE_CSV");
        TRANSLATORS.put("unescapeJava", "UNESCAPE_JAVA");
        TRANSLATORS.put("unescapeJavaScript", "UNESCAPE_ECMASCRIPT");
        TRANSLATORS.put("unescapeHtml", "UNESCAPE_HTML4");
        TRANSLATORS.put("unescapeXml", "UNESCAPE_XML");
        TRANSLATORS.put("unescapeCsv", "UNESCAPE_CSV");
    }

    @Getter
    final String displayName = "Migrate `StringEscapeUtils` methods that write to a `Writer` to Commons Text";

    @Getter
    final String description = "Commons Lang 3 dropped the `StringEscapeUtils` methods that take a `Writer`. " +
                               "Replace `escapeXml(writer, str)` and its siblings with the matching Commons Text " +
                               "translator, as in `StringEscapeUtils.ESCAPE_XML10.translate(str, writer)`.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Map<MethodMatcher, String> matchers = new LinkedHashMap<>();
        List<TreeVisitor<?, ExecutionContext>> preconditions = new ArrayList<>();
        for (Map.Entry<String, String> e : TRANSLATORS.entrySet()) {
            String pattern = "org.apache.commons.lang*.StringEscapeUtils " + e.getKey() + "(java.io.Writer, java.lang.String)";
            matchers.put(new MethodMatcher(pattern), e.getValue());
            preconditions.add(new UsesMethod<>(pattern));
        }
        //noinspection unchecked
        return Preconditions.check(Preconditions.or(preconditions.toArray(new TreeVisitor[0])), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                for (Map.Entry<MethodMatcher, String> e : matchers.entrySet()) {
                    if (e.getKey().matches(m) && m.getArguments().size() == 2) {
                        J replaced = JavaTemplate.builder(COMMONS_TEXT + "." + e.getValue() + ".translate(#{any(java.lang.String)}, #{any(java.io.Writer)})")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-text"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(1), m.getArguments().get(0));
                        doAfterVisit(modifyOnly(replaced));
                        maybeRemoveImport("org.apache.commons.lang.StringEscapeUtils");
                        maybeRemoveImport("org.apache.commons.lang3.StringEscapeUtils");
                        maybeAddImport(COMMONS_TEXT);
                        return replaced.withPrefix(m.getPrefix());
                    }
                }
                return m;
            }
        });
    }
}

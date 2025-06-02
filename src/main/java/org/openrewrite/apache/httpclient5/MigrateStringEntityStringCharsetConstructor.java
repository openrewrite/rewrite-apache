/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.apache.httpclient5;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.nio.charset.Charset;
import java.util.Collections;

public class MigrateStringEntityStringCharsetConstructor extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace `new StringEntity(String, String)` with `new StringEntity(String, Charset)`";
    }

    @Override
    public String getDescription() {
        return "Replace `new StringEntity(String, String)` with `new StringEntity(String, Charset)` to eliminate literal usage for charset parameters.";
    }

    private static final MethodMatcher MATCHER_FOR_4x = new MethodMatcher("org.apache.http.entity.StringEntity <constructor>(String, String)");
    private static final MethodMatcher MATCHER_FOR_5x = new MethodMatcher("org.apache.hc.core5.http.io.entity.StringEntity <constructor>(String, String)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(MATCHER_FOR_4x), new UsesMethod<>(MATCHER_FOR_5x)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                boolean is4x = MATCHER_FOR_4x.matches(nc);
                boolean is5x = MATCHER_FOR_5x.matches(nc);
                if (!is4x && !is5x) {
                    return nc;
                }
                nc = nc.withArguments(ListUtils.mapLast(nc.getArguments(), arg -> {
                    if (arg instanceof J.Literal) {
                        J.Literal argLiteral = (J.Literal) arg;
                        String argValue = (String) argLiteral.getValue();
                        Charset argCharset = Charset.defaultCharset();
                        if (argValue != null && Charset.isSupported(argValue)) {
                            argCharset = Charset.forName(argValue);
                        }
                        return writeAsFieldAccess(argCharset.name(), argLiteral.getPrefix());
                    }
                    return arg;
                }));
                maybeAddImport("java.nio.charset.StandardCharsets");
                nc = JavaTemplate.builder("new StringEntity(#{any(java.lang.String)}, #{any(java.nio.charset.Charset)})")
                        .javaParser(JavaParser.fromJavaVersion().classpath(is4x ? "httpcore" : "httpcore5"))
                        .imports(is4x ? "org.apache.http.entity.StringEntity" : "org.apache.hc.core5.http.io.entity.StringEntity")
                        .build()
                        .apply(getCursor(), nc.getCoordinates().replace(), nc.getArguments().get(0), nc.getArguments().get(1));
                return nc;
            }

            private J.FieldAccess writeAsFieldAccess(String charsetName, Space prefix) {
                J.Identifier newCharsetIdentifier = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), charsetName.replace("-", "_"), JavaType.buildType("java.nio.charset.Charset"), null);
                J.Identifier standardCharsetsIdentifier = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "StandardCharsets", JavaType.buildType("java.nio.charset.StandardCharsets"), null);
                return new J.FieldAccess(Tree.randomId(), prefix, Markers.EMPTY, standardCharsetsIdentifier, new JLeftPadded<>(Space.EMPTY, newCharsetIdentifier, Markers.EMPTY), JavaType.buildType("java.nio.charset.Charset"));
            }
        });
    }
}

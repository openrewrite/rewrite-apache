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
package org.openrewrite.apache.httpclient5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateUrlEncodedFormEntityStringCharset extends Recipe {

    private static final MethodMatcher MATCHER_FOR_4X = new MethodMatcher(
            "org.apache.http.client.entity.UrlEncodedFormEntity <constructor>(java.util.List, java.lang.String)");
    private static final MethodMatcher MATCHER_FOR_5X = new MethodMatcher(
            "org.apache.hc.client5.http.entity.UrlEncodedFormEntity <constructor>(java.util.List, java.lang.String)");
    private static final Set<String> STANDARD_CHARSETS = new HashSet<>(Arrays.asList(
            "US-ASCII", "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-16"));

    String displayName = "Replace `new UrlEncodedFormEntity(List, String)` with `new UrlEncodedFormEntity(Iterable, Charset)`";

    String description = "HttpClient 5's `UrlEncodedFormEntity` takes the charset as a `Charset` only. Replace a supported " +
            "charset name literal with the `StandardCharsets` constant, or with `Charset.forName(..)`, and a `static final` " +
            "`String` constant declared in the same file with `Charset.forName(CONSTANT)`. Other arguments, which may be " +
            "`null`, are left for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(MATCHER_FOR_4X), new UsesMethod<>(MATCHER_FOR_5X)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        if (!MATCHER_FOR_4X.matches(nc) && !MATCHER_FOR_5X.matches(nc)) {
                            return nc;
                        }
                        return nc.withArguments(ListUtils.mapLast(nc.getArguments(), arg -> {
                            String charsetName = literalCharsetName(arg);
                            if (charsetName != null) {
                                String canonical = Charset.forName(charsetName).name();
                                if (STANDARD_CHARSETS.contains(canonical)) {
                                    maybeAddImport("java.nio.charset.StandardCharsets");
                                    return JavaTemplate.builder("StandardCharsets." + canonical.replace("-", "_"))
                                            .imports("java.nio.charset.StandardCharsets")
                                            .build()
                                            .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace());
                                }
                                return charsetForName(arg);
                            }
                            if (isNonNullConstantInThisFile(arg)) {
                                return charsetForName(arg);
                            }
                            return arg;
                        }));
                    }

                    private Expression charsetForName(Expression arg) {
                        maybeAddImport("java.nio.charset.Charset");
                        return JavaTemplate.builder("Charset.forName(#{any(java.lang.String)})")
                                .imports("java.nio.charset.Charset")
                                .build()
                                .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(), arg);
                    }

                    private @Nullable String literalCharsetName(Expression arg) {
                        if (arg instanceof J.Literal && ((J.Literal) arg).getValue() instanceof String) {
                            String name = (String) ((J.Literal) arg).getValue();
                            try {
                                return Charset.isSupported(name) ? name : null;
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
                        }
                        return null;
                    }

                    private boolean isNonNullConstantInThisFile(Expression arg) {
                        if (!(arg instanceof J.Identifier)) {
                            return false;
                        }
                        JavaType.Variable constant = ((J.Identifier) arg).getFieldType();
                        if (constant == null || !constant.hasFlags(Flag.Static, Flag.Final) ||
                                !TypeUtils.isOfClassType(constant.getType(), "java.lang.String")) {
                            return false;
                        }
                        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                        if (cu == null) {
                            return false;
                        }
                        AtomicReference<Boolean> found = new AtomicReference<>(false);
                        new JavaIsoVisitor<AtomicReference<Boolean>>() {
                            @Override
                            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, AtomicReference<Boolean> f) {
                                if (TypeUtils.isOfType(variable.getVariableType(), constant) &&
                                        variable.getInitializer() instanceof J.Literal &&
                                        ((J.Literal) variable.getInitializer()).getValue() instanceof String) {
                                    f.set(true);
                                }
                                return variable;
                            }
                        }.visit(cu, found);
                        return found.get();
                    }
                });
    }
}

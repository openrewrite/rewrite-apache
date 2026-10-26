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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

public class CollectionUtilsCardinalityToFrequency extends Recipe {
    private static final String COLLECTION_UTILS = "org.apache.commons.collections4.CollectionUtils";
    private static final String ITERABLE_UTILS = "org.apache.commons.collections4.IterableUtils";
    private static final String METHOD_PATTERN = COLLECTION_UTILS + " cardinality(..)";

    @Getter
    final String displayName = "Replace `CollectionUtils.cardinality` with `IterableUtils.frequency`";

    @Getter
    final String description = "Replace attributed Java invocations of Commons Collections 4 `cardinality` when `IterableUtils` " +
            "is present on the source set classpath (Commons Collections 4.1 or later). Preserve null iterable rejection " +
            "with `Objects.requireNonNull`, requiring Java 7 or later. Only local variables, parameters and literals " +
            "with a class qualifier or static import are migrated. Other argument expressions, expression qualifiers, " +
            "explicit type arguments and method references are retained to preserve evaluation and typing.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new UsesJavaVersion<>(7), new UsesMethod<>(METHOD_PATTERN)),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final MethodMatcher matcher = new MethodMatcher(METHOD_PATTERN);
                    private @Nullable JavaTemplate template;

                    @Override
                    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                        return sourceFile instanceof J.CompilationUnit;
                    }

                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        // A missing marker is not evidence that the replacement exists. In particular, 4.0
                        // has cardinality but no IterableUtils. Do not change the project's dependencies.
                        JavaSourceSet sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
                        if (sourceSet == null || sourceSet.getClasspath().stream()
                                .noneMatch(type -> ITERABLE_UTILS.equals(type.getFullyQualifiedName()))) {
                            return cu;
                        }
                        // Qualification also requires an unshadowed package root. Treat declared/imported
                        // types named java or org conservatively rather than emitting an ambiguous expression.
                        boolean[] shadowedRoot = {false};
                        new JavaIsoVisitor<boolean[]>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] shadowed) {
                                if (("java".equals(identifier.getSimpleName()) || "org".equals(identifier.getSimpleName())) &&
                                    identifier.getFieldType() == null &&
                                    (TypeUtils.asFullyQualified(identifier.getType()) != null ||
                                     identifier.getType() instanceof JavaType.GenericTypeVariable)) {
                                    shadowed[0] = true;
                                }
                                return identifier;
                            }
                        }.visit(cu, shadowedRoot);
                        if (shadowedRoot[0]) {
                            return cu;
                        }
                        return super.visitCompilationUnit(cu, ctx);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (!matcher.matches(m) || m.getArguments().size() != 2 ||
                            m.getMethodType() == null || m.getMethodType().getParameterTypes().size() != 2 ||
                            !hasOriginalDeclaration(m.getMethodType()) ||
                            !TypeUtils.isOfClassType(m.getMethodType().getParameterTypes().get(1), "java.lang.Iterable") ||
                            m.getTypeParameters() != null && !m.getTypeParameters().isEmpty() ||
                            !isClassQualifier(m.getSelect()) ||
                            !isStable(m.getArguments().get(0)) || !isStable(m.getArguments().get(1))) {
                            return m;
                        }
                        Set<String> namesInScope = VariableNameUtils.findNamesInScope(getCursor());
                        // A qualified expression can itself bind to a variable instead of a package.
                        if (namesInScope.contains("org") || namesInScope.contains("java")) {
                            return m;
                        }
                        if (template == null) {
                            template = JavaTemplate.builder("org.apache.commons.collections4.IterableUtils.frequency(java.util.Objects.requireNonNull(#{any(java.lang.Iterable)}), #{any(java.lang.Object)})")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-collections4-4.4"))
                                    .build();
                        }
                        maybeRemoveImport(COLLECTION_UTILS);
                        J.MethodInvocation replacement = template.apply(updateCursor(m), m.getCoordinates().replace(),
                                m.getArguments().get(1), m.getArguments().get(0));
                        // Standard shortening handles type/import conflicts; variables share the expression
                        // namespace, so retain qualification when a local, parameter or field has that name.
                        doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(replacement,
                                fqn -> namesInScope.contains(fqn.substring(fqn.lastIndexOf('.') + 1))));
                        return replacement;
                    }

                    private boolean isClassQualifier(@Nullable Expression select) {
                        if (select == null) {
                            return true;
                        }
                        if (!TypeUtils.isOfClassType(select.getType(), COLLECTION_UTILS)) {
                            return false;
                        }
                        if (select instanceof J.Identifier) {
                            return ((J.Identifier) select).getFieldType() == null;
                        }
                        return select instanceof J.FieldAccess && ((J.FieldAccess) select).getName().getFieldType() == null;
                    }

                    private boolean isStable(Expression expression) {
                        if (expression instanceof J.Parentheses) {
                            return isStable((Expression) ((J.Parentheses<?>) expression).getTree());
                        }
                        if (expression instanceof J.Literal) {
                            return true;
                        }
                        if (expression instanceof J.Identifier) {
                            JavaType.Variable variable = ((J.Identifier) expression).getFieldType();
                            // Fields can be volatile or accessed through a receiver; getters, arrays and casts
                            // can throw even without reported side effects. Only local/parameter reads commute.
                            return variable != null && variable.getOwner() instanceof JavaType.Method;
                        }
                        return false;
                    }

                    private boolean hasOriginalDeclaration(JavaType.Method method) {
                        JavaType.Method declaration = null;
                        for (JavaType.Method candidate : method.getDeclaringType().getMethods()) {
                            if ("cardinality".equals(candidate.getName())) {
                                if (declaration != null) {
                                    return false;
                                }
                                declaration = candidate;
                            }
                        }
                        if (declaration == null || !declaration.hasFlags(Flag.Static) ||
                            declaration.getReturnType() != JavaType.Primitive.Int || declaration.getParameterTypes().size() != 2 ||
                            !TypeUtils.isOfClassType(declaration.getParameterTypes().get(1), "java.lang.Iterable")) {
                            return false;
                        }
                        // Applied method types substitute O with the actual argument type. Check the declaration
                        // instead; missing or ambiguous declaration metadata is not evidence of this exact API.
                        JavaType first = declaration.getParameterTypes().get(0);
                        return TypeUtils.isOfClassType(first, "java.lang.Object") ||
                                first instanceof JavaType.GenericTypeVariable &&
                                ((JavaType.GenericTypeVariable) first).getBounds().stream()
                                        .allMatch(bound -> TypeUtils.isOfClassType(bound, "java.lang.Object"));
                    }
                });
    }
}

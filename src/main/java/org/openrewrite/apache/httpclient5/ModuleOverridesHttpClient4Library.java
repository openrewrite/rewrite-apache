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
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ModuleOverridesHttpClient4Library extends ScanningRecipe<ModuleOverridesHttpClient4Library.Accumulator> {

    private static final String HTTPCLIENT_4_PACKAGE = "org.apache.http.";

    @Getter
    String displayName = "Find modules overriding a library method that uses HttpClient 4";

    @Getter
    String description = "Marks the source files of modules that override a method whose declaration in a library, " +
                         "a type that is not declared in the sources, takes or returns an HttpClient 4 type. " +
                         "The override has to keep that signature as long as the library is on HttpClient 4, " +
                         "so retyping it to HttpClient 5 stops it from compiling. " +
                         "This recipe is intended to be used as a precondition for the HttpClient 5 migration.";

    @Option(displayName = "Invert marking",
            description = "If `true`, marks the files of modules that do **not** override such a method instead. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean invertMarking;

    public static class Accumulator {
        final Set<String> declaredTypes = new HashSet<>();
        final Map<JavaProject, Set<String>> overriddenLibraryTypes = new HashMap<>();
        @Nullable
        Set<JavaProject> tied;

        Set<JavaProject> tied() {
            if (tied == null) {
                tied = new HashSet<>();
                overriddenLibraryTypes.forEach((project, declaringTypes) -> {
                    if (!declaredTypes.containsAll(declaringTypes)) {
                        tied.add(project);
                    }
                });
            }
            return tied;
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (classDecl.getType() != null) {
                    acc.declaredTypes.add(classDecl.getType().getFullyQualifiedName());
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && mayOverrideHttpClient4Method(methodType)) {
                    getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getMarkers().findFirst(JavaProject.class)
                            .ifPresent(project -> TypeUtils.findOverriddenMethod(methodType)
                                    .filter(overridden -> usesHttpClient4(overridden) &&
                                                          !overridden.getDeclaringType().getFullyQualifiedName().startsWith(HTTPCLIENT_4_PACKAGE))
                                    .ifPresent(overridden -> acc.overriddenLibraryTypes
                                            .computeIfAbsent(project, p -> new HashSet<>())
                                            .add(overridden.getDeclaringType().getFullyQualifiedName())));
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private boolean mayOverrideHttpClient4Method(JavaType.Method method) {
                if (method.isConstructor() || method.hasFlags(Flag.Static) || method.hasFlags(Flag.Private)) {
                    return false;
                }
                for (JavaType parameterType : method.getParameterTypes()) {
                    if (isHttpClient4(parameterType)) {
                        return true;
                    }
                }
                // An override may narrow the return type to a subtype declared outside HttpClient 4
                JavaType returnType = method.getReturnType();
                return isOrExtendsHttpClient4(TypeUtils.asFullyQualified(
                        returnType instanceof JavaType.Array ? ((JavaType.Array) returnType).getElemType() : returnType));
            }

            private boolean isOrExtendsHttpClient4(JavaType.@Nullable FullyQualified type) {
                if (type == null) {
                    return false;
                }
                if (isHttpClient4(type) || isOrExtendsHttpClient4(type.getSupertype())) {
                    return true;
                }
                for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                    if (isOrExtendsHttpClient4(anInterface)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean usesHttpClient4(JavaType.Method overridden) {
                if (isHttpClient4(overridden.getReturnType())) {
                    return true;
                }
                for (JavaType parameterType : overridden.getParameterTypes()) {
                    if (isHttpClient4(parameterType)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isHttpClient4(@Nullable JavaType type) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type instanceof JavaType.Array ? ((JavaType.Array) type).getElemType() : type);
                return fq != null && fq.getFullyQualifiedName().startsWith(HTTPCLIENT_4_PACKAGE);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        boolean invert = Boolean.TRUE.equals(invertMarking);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                boolean tied = project != null && acc.tied().contains(project);
                return tied != invert ?
                        SearchResult.found(tree, tied ? "Module overrides a library method that uses HttpClient 4" : "Module does not override a library method that uses HttpClient 4") :
                        tree;
            }
        };
    }
}

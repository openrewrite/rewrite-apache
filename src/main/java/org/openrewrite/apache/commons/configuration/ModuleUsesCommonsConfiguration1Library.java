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
package org.openrewrite.apache.commons.configuration;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Collections.emptySet;

@Value
@EqualsAndHashCode(callSuper = false)
public class ModuleUsesCommonsConfiguration1Library extends ScanningRecipe<ModuleUsesCommonsConfiguration1Library.Accumulator> {

    private static final String CONFIGURATION_1_PACKAGE = "org.apache.commons.configuration.";

    @Getter
    String displayName = "Find modules that exchange Commons Configuration 1.x types with a library";

    @Getter
    String description = "Marks the source files of modules that call, override or extend a method or class of a " +
                         "library, a type that is not declared in the sources, whose declaration takes, returns, throws or " +
                         "extends a Commons Configuration 1.x type, such as TinkerPop 3.4's `GraphFactory.open(Configuration)`, " +
                         "Archaius 1's `ConfigurationManager.getConfigInstance()` or Hadoop's `MetricsSink.init(SubsetConfiguration)`. " +
                         "Such code has to keep using the types the library declares as long as the library is on 1.x, " +
                         "so moving it to Configuration 2 stops it from compiling. " +
                         "This recipe is intended to be used as a precondition for the Configuration 2 migration.";

    @Option(displayName = "Invert marking",
            description = "If `true`, marks the files of modules that do **not** use such a library instead. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean invertMarking;

    public static class Accumulator {
        /**
         * The types declared in the sources, by the name of each, with the module that declares it.
         */
        final Map<String, @Nullable JavaProject> declaredTypes = new HashMap<>();

        /**
         * The types each module exchanges 1.x types with: types that are or extend one, or whose methods it calls,
         * overrides or extends take, return or throw one.
         */
        final Map<JavaProject, Set<String>> libraryTypes = new HashMap<>();

        @Nullable
        Set<JavaProject> tied;

        Set<JavaProject> tied() {
            if (tied == null) {
                tied = new HashSet<>();
                libraryTypes.forEach((project, types) -> {
                    if (!declaredTypes.keySet().containsAll(types)) {
                        tied.add(project);
                    }
                });
                // A module that exchanges 1.x types with another module of the repository that stays on 1.x has to
                // stay on it too, whichever of the two uses the other's types
                for (boolean changed = true; changed; ) {
                    changed = false;
                    for (Map.Entry<JavaProject, Set<String>> exchanges : libraryTypes.entrySet()) {
                        for (String type : exchanges.getValue()) {
                            JavaProject declaring = declaredTypes.get(type);
                            if (declaring != null && tied.contains(exchanges.getKey()) != tied.contains(declaring)) {
                                tied.add(exchanges.getKey());
                                tied.add(declaring);
                                changed = true;
                            }
                        }
                    }
                }
            }
            return tied;
        }

        /**
         * The types a module exchanges 1.x types with that the sources don't declare, or, for a module that stays on
         * 1.x for another module of the repository, the types of the other modules.
         */
        String exchangedWith(JavaProject project) {
            Set<String> types = libraryTypes.getOrDefault(project, emptySet());
            Set<String> undeclared = new TreeSet<>();
            for (String type : types) {
                if (!declaredTypes.containsKey(type)) {
                    undeclared.add(type);
                }
            }
            return String.join(", ", undeclared.isEmpty() ? new TreeSet<>(types) : undeclared);
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        // A module can get 1.x types from a library without naming them, such as by calling a method on what it returns
        JavaIsoVisitor<ExecutionContext> scanner = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    // Using a type that is a 1.x configuration, such as one of another module, exchanges it
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    JavaProject project = sourceFile.getMarkers().findFirst(JavaProject.class).orElse(null);
                    for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                        if (project != null && isLibrarySubtypeOfConfiguration1(fq)) {
                            acc.libraryTypes.computeIfAbsent(project, p -> new HashSet<>()).add(fq.getFullyQualifiedName());
                        }
                    }
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                JavaType.FullyQualified type = classDecl.getType();
                if (type != null) {
                    acc.declaredTypes.put(type.getFullyQualifiedName(), getCursor().firstEnclosingOrThrow(JavaSourceFile.class)
                            .getMarkers().findFirst(JavaProject.class).orElse(null));
                    // Extending a library class that is itself a 1.x configuration, such as Archaius' ConcurrentMapConfiguration
                    tieIf(isLibrarySubtypeOfConfiguration1(type.getSupertype()), type.getSupertype());
                    for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                        tieIf(isLibrarySubtypeOfConfiguration1(anInterface), anInterface);
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && !methodType.isConstructor() && !methodType.hasFlags(Flag.Static) &&
                    !methodType.hasFlags(Flag.Private) && usesConfiguration1(methodType)) {
                    TypeUtils.findOverriddenMethod(methodType)
                            .filter(overridden -> !isConfiguration1(overridden.getDeclaringType()) &&
                                                  usesConfiguration1(declaration(overridden)))
                            .ifPresent(overridden -> tieIf(true, overridden.getDeclaringType()));
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                tieIfLibraryCall(method.getMethodType());
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                JavaType.Method constructor = newClass.getConstructorType();
                if (newClass.getBody() != null && constructor != null) {
                    // An anonymous class is declared in the sources, though no class declaration names it, and its
                    // constructor passes what it takes on to the constructor of its supertype
                    JavaType.FullyQualified anonymous = constructor.getDeclaringType();
                    acc.declaredTypes.put(anonymous.getFullyQualifiedName(), getCursor().firstEnclosingOrThrow(JavaSourceFile.class)
                            .getMarkers().findFirst(JavaProject.class).orElse(null));
                    JavaType.FullyQualified supertype = anonymous.getSupertype();
                    constructor = supertype == null ? null : constructor.withDeclaringType(supertype);
                }
                tieIfLibraryCall(constructor);
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                tieIfLibraryCall(memberRef.getMethodType());
                return super.visitMemberReference(memberRef, ctx);
            }

            private void tieIfLibraryCall(JavaType.@Nullable Method methodType) {
                if (methodType != null && !isConfiguration1(methodType.getDeclaringType()) &&
                    usesConfiguration1(declaration(methodType))) {
                    tieIf(true, methodType.getDeclaringType());
                }
            }

            private void tieIf(boolean tied, JavaType.@Nullable FullyQualified libraryType) {
                if (tied && libraryType != null) {
                    getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getMarkers().findFirst(JavaProject.class)
                            .ifPresent(project -> acc.libraryTypes
                                    .computeIfAbsent(project, p -> new HashSet<>())
                                    .add(libraryType.getFullyQualifiedName()));
                }
            }
        };
        // Other sources name 1.x types too, such as the class of a bean in Spring XML, which UsesType finds
        return Preconditions.check(new UsesType<>(CONFIGURATION_1_PACKAGE + ".*", true), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof JavaSourceFile ? scanner.visit(tree, ctx) : tree;
            }
        });
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
                if (project != null && acc.tied().contains(project)) {
                    return invert ? tree : SearchResult.found(tree,
                            "Module exchanges Commons Configuration 1.x types with " + acc.exchangedWith(project));
                }
                return invert ? SearchResult.found(tree, "Module does not exchange Commons Configuration 1.x types with a library") : tree;
            }
        };
    }

    /**
     * The method as its type declares it, with the type variables that a call fills in left as they are, so that a
     * {@code List<Configuration>} doesn't make {@code List.add(E)} take a 1.x type.
     */
    private static JavaType.Method declaration(JavaType.Method method) {
        for (JavaType.Method declared : method.getDeclaringType().getMethods()) {
            if (declared.getName().equals(method.getName()) &&
                declared.getParameterTypes().size() == method.getParameterTypes().size()) {
                return declared;
            }
        }
        return method;
    }

    private static boolean usesConfiguration1(JavaType.Method method) {
        if (isConfiguration1(method.getReturnType())) {
            return true;
        }
        for (JavaType parameterType : method.getParameterTypes()) {
            if (isConfiguration1(parameterType)) {
                return true;
            }
        }
        // A caller catches the 1.x ConfigurationException that the library throws
        for (JavaType thrown : method.getThrownExceptions()) {
            if (isConfiguration1(thrown)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLibrarySubtypeOfConfiguration1(JavaType.@Nullable FullyQualified type) {
        return type != null && !isConfiguration1(type) && isOrExtendsConfiguration1(type);
    }

    private static boolean isOrExtendsConfiguration1(JavaType.@Nullable FullyQualified type) {
        if (type == null) {
            return false;
        }
        if (isConfiguration1(type) || isOrExtendsConfiguration1(type.getSupertype())) {
            return true;
        }
        for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
            if (isOrExtendsConfiguration1(anInterface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfiguration1(@Nullable JavaType type) {
        if (type instanceof JavaType.Array) {
            return isConfiguration1(((JavaType.Array) type).getElemType());
        }
        if (type instanceof JavaType.Parameterized) {
            for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                if (isConfiguration1(typeParameter)) {
                    return true;
                }
            }
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && fq.getFullyQualifiedName().startsWith(CONFIGURATION_1_PACKAGE);
    }
}

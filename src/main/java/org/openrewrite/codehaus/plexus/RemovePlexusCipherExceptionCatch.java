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
package org.openrewrite.codehaus.plexus;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemovePlexusCipherExceptionCatch extends Recipe {

    private static final String PLEXUS_CIPHER_EXCEPTION = "org.sonatype.plexus.components.cipher.PlexusCipherException";

    /**
     * The constructors that declare {@code throws PlexusCipherException} in plexus-cipher 1.x and no longer do in 2.0.
     */
    private static final List<MethodMatcher> NO_LONGER_THROWING = Arrays.asList(
            new MethodMatcher("org.sonatype.plexus.components.cipher.DefaultPlexusCipher <constructor>()"),
            new MethodMatcher("org.sonatype.plexus.components.cipher.PBECipher <constructor>()"));

    @Getter
    final String displayName = "Remove `catch (PlexusCipherException)` the cipher constructors no longer need";

    @Getter
    final String description = "The constructors of `DefaultPlexusCipher` and `PBECipher` no longer throw " +
            "`PlexusCipherException` in `org.codehaus.plexus:plexus-cipher` 2.0, so catching it around them, with " +
            "nothing else in the `try` that throws it, no longer compiles. This removes such a `catch`, or the " +
            "exception from a multi-catch, and unwraps a `try` that is left without `catch` and `finally`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PLEXUS_CIPHER_EXCEPTION, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // Groovy and Kotlin have no checked exceptions, so their catches still compile
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitTry(J.Try tryable, ExecutionContext ctx) {
                J.Try t = (J.Try) super.visitTry(tryable, ctx);
                if (t.getResources() != null && !t.getResources().isEmpty()) {
                    // The resources' close() may throw anything
                    return t;
                }
                J.Block tryBody = t.getBody();
                List<J.Try.Catch> catches = ListUtils.map(t.getCatches(), c -> removePlexusCipherException(c, tryBody));
                if (catches == t.getCatches()) {
                    return t;
                }
                maybeRemoveImport(PLEXUS_CIPHER_EXCEPTION);
                t = t.withCatches(catches);
                if (t.getCatches().isEmpty() && t.getFinally() == null) {
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                        // Unwrapped by visitBlock, which can replace one statement by several
                        return t;
                    }
                    return t.getBody().withPrefix(t.getPrefix());
                }
                return t;
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                if (b.getStatements().stream().noneMatch(RemovePlexusCipherExceptionCatch::isBareTry)) {
                    return b;
                }
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                List<Statement> statements = b.getStatements();
                List<Statement> unwrapped = new ArrayList<>(statements.size());
                for (int i = 0; i < statements.size(); i++) {
                    Statement s = statements.get(i);
                    if (!isBareTry(s)) {
                        unwrapped.add(s);
                        continue;
                    }
                    J.Try t = (J.Try) s;
                    if (declaresNameUsedLater(t.getBody(), statements.subList(i + 1, statements.size()))) {
                        // Keep the statements in a block of their own, so that their variables stay in its scope
                        unwrapped.add(t.getBody().withPrefix(t.getPrefix()));
                        continue;
                    }
                    List<Statement> body = t.getBody().getStatements();
                    for (int j = 0; j < body.size(); j++) {
                        Statement moved = ShiftFormat.indent(body.get(j), blockCursor, -1);
                        if (j == 0) {
                            moved = moved.withPrefix(t.getPrefix().withComments(ListUtils.concatAll(
                                    t.getPrefix().getComments(), moved.getPrefix().getComments())));
                        }
                        unwrapped.add(moved);
                    }
                }
                return b.withStatements(unwrapped);
            }

            private J.Try.@Nullable Catch removePlexusCipherException(J.Try.Catch aCatch, J.Block tryBody) {
                J.VariableDeclarations parameter = aCatch.getParameter().getTree();
                TypeTree typeExpression = parameter.getTypeExpression();
                if (typeExpression instanceof J.MultiCatch) {
                    J.MultiCatch multiCatch = (J.MultiCatch) typeExpression;
                    List<JRightPadded<NameTree>> padded = multiCatch.getPadding().getAlternatives();
                    List<JRightPadded<NameTree>> kept = ListUtils.map(padded, alternative ->
                            isPlexusCipherException(alternative.getElement().getType()) &&
                            !mayThrow(tryBody, alternative.getElement().getType()) ? null : alternative);
                    if (kept.size() == padded.size()) {
                        return aCatch;
                    }
                    if (kept.size() > 1) {
                        kept = ListUtils.mapFirst(kept, a -> a.withElement(a.getElement().withPrefix(padded.get(0).getElement().getPrefix())));
                        kept = ListUtils.mapLast(kept, a -> a.withAfter(padded.get(padded.size() - 1).getAfter()));
                        return aCatch.withParameter(aCatch.getParameter().withTree(
                                parameter.withTypeExpression(multiCatch.getPadding().withAlternatives(kept))));
                    }
                    NameTree remaining = kept.get(0).getElement().withPrefix(multiCatch.getPrefix());
                    JavaType remainingType = remaining.getType();
                    return aCatch.withParameter(aCatch.getParameter().withTree(parameter
                            .withTypeExpression((TypeTree) remaining)
                            .withVariables(ListUtils.map(parameter.getVariables(), v -> v
                                    .withType(remainingType)
                                    .withName(v.getName().withType(remainingType).withFieldType(
                                            v.getName().getFieldType() == null ? null :
                                                    v.getName().getFieldType().withType(remainingType)))))));
                }
                if (isPlexusCipherException(typeExpression == null ? null : typeExpression.getType()) &&
                    !mayThrow(tryBody, typeExpression.getType())) {
                    return null;
                }
                return aCatch;
            }
        });
    }

    private static boolean isPlexusCipherException(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, PLEXUS_CIPHER_EXCEPTION);
    }

    private static boolean isBareTry(Statement s) {
        if (!(s instanceof J.Try)) {
            return false;
        }
        J.Try t = (J.Try) s;
        return t.getCatches().isEmpty() && t.getFinally() == null &&
               (t.getResources() == null || t.getResources().isEmpty());
    }

    /**
     * Whether anything in the block, other than the constructors that no longer throw it, may throw the caught
     * exception or a sub- or supertype of it, so that catching it still compiles. Unknown types count as throwing it.
     */
    private static boolean mayThrow(J.Block body, @Nullable JavaType caught) {
        if (!(caught instanceof JavaType.FullyQualified)) {
            return true;
        }
        AtomicBoolean mayThrow = new AtomicBoolean(false);
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                // What the lambda's body throws is thrown when it is called, not here
                return lambda;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                return classDecl;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                if (newClass.getEnclosing() != null) {
                    visit(newClass.getEnclosing(), found);
                }
                visit(newClass.getArguments(), found);
                if (NO_LONGER_THROWING.stream().noneMatch(m -> m.matches(newClass))) {
                    check(newClass.getMethodType(), found);
                }
                return newClass;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                check(method.getMethodType(), found);
                return super.visitMethodInvocation(method, found);
            }

            @Override
            public J.Throw visitThrow(J.Throw thrown, AtomicBoolean found) {
                JavaType type = thrown.getException().getType();
                if (!(type instanceof JavaType.FullyQualified) || related((JavaType.FullyQualified) type)) {
                    found.set(true);
                }
                return super.visitThrow(thrown, found);
            }

            private void check(JavaType.@Nullable Method methodType, AtomicBoolean found) {
                if (methodType == null) {
                    found.set(true);
                    return;
                }
                for (JavaType thrownException : methodType.getThrownExceptions()) {
                    if (!(thrownException instanceof JavaType.FullyQualified) ||
                        related((JavaType.FullyQualified) thrownException)) {
                        found.set(true);
                    }
                }
            }

            private boolean related(JavaType.FullyQualified thrown) {
                return TypeUtils.isAssignableTo(caught, thrown) || TypeUtils.isAssignableTo(thrown, caught);
            }
        }.visit(body, mayThrow);
        return mayThrow.get();
    }

    /**
     * Whether a variable the block declares directly would clash, once it is in the enclosing scope, with a variable
     * declared in the statements after it.
     */
    private static boolean declaresNameUsedLater(J.Block body, List<Statement> later) {
        Set<String> declared = new HashSet<>();
        for (Statement statement : body.getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable v : ((J.VariableDeclarations) statement).getVariables()) {
                    declared.add(v.getSimpleName());
                }
            } else if (statement instanceof J.ClassDeclaration) {
                declared.add(((J.ClassDeclaration) statement).getSimpleName());
            }
        }
        if (declared.isEmpty()) {
            return false;
        }
        AtomicBoolean clash = new AtomicBoolean(false);
        JavaIsoVisitor<AtomicBoolean> visitor = new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, AtomicBoolean found) {
                if (declared.contains(variable.getSimpleName())) {
                    found.set(true);
                }
                return super.visitVariable(variable, found);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                if (declared.contains(classDecl.getSimpleName())) {
                    found.set(true);
                }
                return super.visitClassDeclaration(classDecl, found);
            }
        };
        for (Statement statement : later) {
            visitor.visit(statement, clash);
        }
        return clash.get();
    }
}

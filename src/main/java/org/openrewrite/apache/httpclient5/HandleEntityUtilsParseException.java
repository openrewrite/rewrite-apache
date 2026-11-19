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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class HandleEntityUtilsParseException extends Recipe {

    private static final String PARSE_EXCEPTION = "org.apache.hc.core5.http.ParseException";
    private static final MethodMatcher ENTITY_UTILS_TO_STRING =
            new MethodMatcher("org.apache.hc.core5.http.io.entity.EntityUtils toString(..)");
    private static final MethodMatcher HANDLE_RESPONSE =
            new MethodMatcher("org.apache.hc.core5.http.io.HttpClientResponseHandler handleResponse(..)", true);

    String displayName = "Handle the checked `ParseException` of `EntityUtils.toString(..)`";

    String description = "HttpClient 4's `EntityUtils.toString(..)` threw an unchecked `ParseException`, while HttpClient 5's " +
            "declares the checked `org.apache.hc.core5.http.ParseException`. Where the call sits in a `try` that catches " +
            "`IOException`, add `ParseException` to that `catch`; where it sits in an `HttpClientResponseHandler.handleResponse` " +
            "implementation, which may throw `HttpException`, add it to the method's `throws`. Other calls would need a " +
            "method signature to change, and are left alone.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ENTITY_UTILS_TO_STRING), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                Set<UUID> targets = new HashSet<>();
                new JavaIsoVisitor<Set<UUID>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<UUID> t) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, t);
                        if (ENTITY_UTILS_TO_STRING.matches(m)) {
                            J target = whereToHandle(getCursor());
                            if (target != null) {
                                t.add(target.getId());
                            }
                        }
                        return m;
                    }
                }.visit(cu, targets);
                if (targets.isEmpty()) {
                    return cu;
                }
                J.CompilationUnit c = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Try.Catch visitCatch(J.Try.Catch _catch, ExecutionContext ctx) {
                        J.Try.Catch c = super.visitCatch(_catch, ctx);
                        if (!targets.contains(c.getId())) {
                            return c;
                        }
                        J.VariableDeclarations param = c.getParameter().getTree();
                        TypeTree caught = param.getTypeExpression();
                        if (caught == null) {
                            return c;
                        }
                        List<JRightPadded<NameTree>> alternatives = new ArrayList<>();
                        if (caught instanceof J.MultiCatch) {
                            List<JRightPadded<NameTree>> existing = ((J.MultiCatch) caught).getPadding().getAlternatives();
                            alternatives.addAll(ListUtils.mapLast(existing, a -> a.withAfter(Space.SINGLE_SPACE)));
                        } else {
                            alternatives.add(JRightPadded.<NameTree>build((NameTree) caught.withPrefix(Space.EMPTY)).withAfter(Space.SINGLE_SPACE));
                        }
                        alternatives.add(JRightPadded.build(parseException(Space.SINGLE_SPACE)));
                        J.MultiCatch multiCatch = new J.MultiCatch(Tree.randomId(), caught.getPrefix(), Markers.EMPTY, alternatives);
                        maybeAddImport(PARSE_EXCEPTION);
                        return c.withParameter(c.getParameter().withTree(param.withTypeExpression(multiCatch)));
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        if (!targets.contains(md.getId())) {
                            return md;
                        }
                        maybeAddImport(PARSE_EXCEPTION);
                        JContainer<NameTree> throwz = md.getPadding().getThrows();
                        if (throwz == null) {
                            return md.getPadding().withThrows(JContainer.build(Space.SINGLE_SPACE,
                                    Collections.singletonList(JRightPadded.build(parseException(Space.SINGLE_SPACE))), Markers.EMPTY));
                        }
                        return md.getPadding().withThrows(throwz.getPadding().withElements(ListUtils.concat(
                                throwz.getPadding().getElements(), JRightPadded.build(parseException(Space.SINGLE_SPACE)))));
                    }
                }.visitNonNull(cu, ctx);
                return c;
            }
        });
    }

    /**
     * The {@code catch} or method declaration to add {@code ParseException} to, or {@code null} when the exception is
     * already handled or there is nowhere to add it without changing a method signature.
     */
    private static @Nullable J whereToHandle(Cursor cursor) {
        Object child = cursor.getValue();
        for (Cursor c = cursor.getParentTreeCursor(); c != null && !(c.getValue() instanceof J.CompilationUnit); c = c.getParentTreeCursor()) {
            Object value = c.getValue();
            if (value instanceof J.Lambda || value instanceof J.ClassDeclaration ||
                value instanceof J.NewClass && child == ((J.NewClass) value).getBody()) {
                // A lambda's functional interface decides what it may throw, and HttpClientResponseHandler allows
                // ParseException; an initializer has no method to declare it on
                return null;
            }
            if (value instanceof J.Try) {
                J.Try aTry = (J.Try) value;
                if (child == aTry.getBody() || isResource(aTry, child)) {
                    J.Try.Catch ioCatch = null;
                    for (J.Try.Catch aCatch : aTry.getCatches()) {
                        if (catchesParseException(aCatch)) {
                            return null;
                        }
                        if (ioCatch == null && catchesExactly(aCatch, "java.io.IOException") && usesParameterGenerically(aCatch)) {
                            ioCatch = aCatch;
                        }
                    }
                    if (ioCatch != null) {
                        return ioCatch;
                    }
                }
            }
            if (value instanceof J.MethodDeclaration) {
                J.MethodDeclaration md = (J.MethodDeclaration) value;
                if (md.getThrows() != null) {
                    for (NameTree thrown : md.getThrows()) {
                        if (isSupertypeOfParseException(thrown.getType())) {
                            return null;
                        }
                    }
                }
                return md.getMethodType() != null && HANDLE_RESPONSE.matches(md.getMethodType()) ? md : null;
            }
            child = value;
        }
        return null;
    }

    private static boolean isResource(J.Try aTry, Object child) {
        return aTry.getResources() != null && aTry.getResources().contains(child);
    }

    private static boolean catchesParseException(J.Try.Catch aCatch) {
        TypeTree caught = aCatch.getParameter().getTree().getTypeExpression();
        if (caught instanceof J.MultiCatch) {
            for (NameTree alternative : ((J.MultiCatch) caught).getAlternatives()) {
                if (isSupertypeOfParseException(alternative.getType())) {
                    return true;
                }
            }
            return false;
        }
        return caught != null && isSupertypeOfParseException(caught.getType());
    }

    private static boolean isSupertypeOfParseException(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, PARSE_EXCEPTION) ||
               TypeUtils.isOfClassType(type, "org.apache.hc.core5.http.ProtocolException") ||
               TypeUtils.isOfClassType(type, "org.apache.hc.core5.http.HttpException") ||
               TypeUtils.isOfClassType(type, "java.lang.Exception") ||
               TypeUtils.isOfClassType(type, "java.lang.Throwable");
    }

    private static boolean catchesExactly(J.Try.Catch aCatch, String exception) {
        TypeTree caught = aCatch.getParameter().getTree().getTypeExpression();
        if (caught instanceof J.MultiCatch) {
            for (NameTree alternative : ((J.MultiCatch) caught).getAlternatives()) {
                if (TypeUtils.isOfClassType(alternative.getType(), exception)) {
                    return true;
                }
            }
            return false;
        }
        return caught != null && TypeUtils.isOfClassType(caught.getType(), exception);
    }

    /**
     * In a multi-catch the parameter is an {@code Exception}: it may not be rethrown, which would need the method to
     * declare {@code ParseException}, nor passed where an {@code IOException} is expected.
     */
    private static boolean usesParameterGenerically(J.Try.Catch aCatch) {
        J.VariableDeclarations.NamedVariable variable = aCatch.getParameter().getTree().getVariables().get(0);
        AtomicBoolean specific = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, AtomicBoolean s) {
                if (isParameter(thrown.getException())) {
                    s.set(true);
                }
                return super.visitThrow(thrown, s);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean s) {
                checkArguments(method.getMethodType(), method.getArguments(), s);
                return super.visitMethodInvocation(method, s);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean s) {
                checkArguments(newClass.getConstructorType(), newClass.getArguments(), s);
                return super.visitNewClass(newClass, s);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable v, AtomicBoolean s) {
                if (v.getInitializer() != null && isParameter(v.getInitializer()) &&
                    !TypeUtils.isOfClassType(v.getType(), "java.lang.Exception") &&
                    !TypeUtils.isOfClassType(v.getType(), "java.lang.Throwable") &&
                    !TypeUtils.isOfClassType(v.getType(), "java.lang.Object")) {
                    s.set(true);
                }
                return super.visitVariable(v, s);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean s) {
                if (isParameter(assignment.getAssignment())) {
                    s.set(true);
                }
                return super.visitAssignment(assignment, s);
            }

            @Override
            public J.Return visitReturn(J.Return _return, AtomicBoolean s) {
                if (_return.getExpression() != null && isParameter(_return.getExpression())) {
                    s.set(true);
                }
                return super.visitReturn(_return, s);
            }

            private void checkArguments(JavaType.@Nullable Method type, List<Expression> args, AtomicBoolean s) {
                for (int i = 0; i < args.size(); i++) {
                    if (!isParameter(args.get(i))) {
                        continue;
                    }
                    if (type == null) {
                        s.set(true);
                        continue;
                    }
                    List<JavaType> params = type.getParameterTypes();
                    JavaType param = params.isEmpty() ? null : params.get(Math.min(i, params.size() - 1));
                    if (param instanceof JavaType.Array) {
                        param = ((JavaType.Array) param).getElemType();
                    }
                    if (!TypeUtils.isOfClassType(param, "java.lang.Throwable") &&
                        !TypeUtils.isOfClassType(param, "java.lang.Exception") &&
                        !TypeUtils.isOfClassType(param, "java.lang.Object")) {
                        s.set(true);
                    }
                }
            }

            private boolean isParameter(Expression e) {
                return e instanceof J.Identifier && ((J.Identifier) e).getSimpleName().equals(variable.getSimpleName());
            }
        }.visit(aCatch.getBody(), specific);
        return !specific.get();
    }

    private static J.Identifier parseException(Space prefix) {
        return new J.Identifier(Tree.randomId(), prefix, Markers.EMPTY, Collections.emptyList(), "ParseException",
                JavaType.ShallowClass.build(PARSE_EXCEPTION), null);
    }
}

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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddHttpExceptionToResponseHandlers extends Recipe {

    private static final String FQN_RESPONSE_HANDLER = "org.apache.hc.core5.http.io.HttpClientResponseHandler";
    private static final String FQN_HTTP_EXCEPTION = "org.apache.hc.core5.http.HttpException";
    private static final MethodMatcher ENTITY_TO_STRING = new MethodMatcher("org.apache.hc.core5.http.io.entity.EntityUtils toString(..)");

    // ParseException and the types it extends, any of which a handleResponse may already declare
    private static final List<String> PARSE_EXCEPTION_TYPES = Arrays.asList(
            "org.apache.hc.core5.http.ParseException",
            "org.apache.hc.core5.http.ProtocolException",
            FQN_HTTP_EXCEPTION,
            "java.lang.Exception",
            "java.lang.Throwable");

    String displayName = "Declare `HttpException` on response handlers that read the entity";

    String description = "HttpCore 5's `EntityUtils.toString` throws the checked `ParseException`, which is " +
            "unchecked in HttpCore 4. `HttpClientResponseHandler.handleResponse` may throw it as an " +
            "`HttpException`, so declare that on the implementations that call `EntityUtils.toString`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ENTITY_TO_STRING), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (!"handleResponse".equals(md.getSimpleName()) || !inResponseHandler() ||
                    declaresParseException(md) || !readsEntity(md)) {
                    return md;
                }
                maybeAddImport(FQN_HTTP_EXCEPTION);
                JavaType.FullyQualified httpExceptionType = JavaType.ShallowClass.build(FQN_HTTP_EXCEPTION);
                J.Identifier httpException = new J.Identifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                        emptyList(), "HttpException", httpExceptionType, null);
                if (md.getMethodType() != null) {
                    JavaType.Method methodType = md.getMethodType().withThrownExceptions(
                            ListUtils.concat(md.getMethodType().getThrownExceptions(), httpExceptionType));
                    md = md.withMethodType(methodType).withName(md.getName().withType(methodType));
                }
                if (md.getThrows() == null || md.getThrows().isEmpty()) {
                    return md.getPadding().withThrows(JContainer.build(Space.SINGLE_SPACE,
                            singletonList(JRightPadded.build(httpException)), Markers.EMPTY));
                }
                return md.withThrows(ListUtils.concat(md.getThrows(), httpException));
            }

            private boolean inResponseHandler() {
                Object enclosing = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                if (enclosing instanceof J.ClassDeclaration) {
                    return TypeUtils.isAssignableTo(FQN_RESPONSE_HANDLER, ((J.ClassDeclaration) enclosing).getType());
                }
                return enclosing instanceof J.NewClass && ((J.NewClass) enclosing).getClazz() != null &&
                       TypeUtils.isAssignableTo(FQN_RESPONSE_HANDLER, ((J.NewClass) enclosing).getClazz().getType());
            }
        });
    }

    private static boolean declaresParseException(J.MethodDeclaration md) {
        if (md.getThrows() == null) {
            return false;
        }
        for (NameTree thrown : md.getThrows()) {
            for (String type : PARSE_EXCEPTION_TYPES) {
                if (TypeUtils.isOfClassType(thrown.getType(), type)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Calls in a lambda or class declared in the body throw from that lambda or class, not from handleResponse
    private static boolean readsEntity(J.MethodDeclaration md) {
        AtomicBoolean reads = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                return lambda;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                return classDecl;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                return newClass.getBody() == null ? super.visitNewClass(newClass, found) : newClass;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (ENTITY_TO_STRING.matches(method)) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }
        }.visit(md.getBody(), reads);
        return reads.get();
    }
}

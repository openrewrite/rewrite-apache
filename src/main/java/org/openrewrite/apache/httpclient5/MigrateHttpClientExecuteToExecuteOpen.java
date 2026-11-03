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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateHttpClientExecuteToExecuteOpen extends Recipe {

    private static final String HTTP_CLIENT = "org.apache.hc.client5.http.classic.HttpClient";
    private static final String HTTP_HOST = "org.apache.hc.core5.http.HttpHost";
    private static final String HTTP_CONTEXT = "org.apache.hc.core5.http.protocol.HttpContext";

    /**
     * Only calls declared on the {@code HttpClient} interface itself: {@code CloseableHttpClient}'s own
     * {@code execute} methods return a {@code CloseableHttpResponse} in 5.x as they did in 4.x.
     */
    private static final MethodMatcher EXECUTE = new MethodMatcher(HTTP_CLIENT + " execute(..)", false);

    /**
     * A parameter type of the call may still carry its 4.x name, when the file does not use that type itself.
     */
    private static final String[] HTTP_HOSTS = {HTTP_HOST, "org.apache.http.HttpHost"};
    private static final String[] RESPONSE_HANDLERS = {
            "org.apache.hc.core5.http.io.HttpClientResponseHandler", "org.apache.http.client.ResponseHandler"};

    private static boolean isAny(JavaType type, String... fullyQualifiedNames) {
        for (String fqn : fullyQualifiedNames) {
            if (TypeUtils.isAssignableTo(fqn, type)) {
                return true;
            }
        }
        return false;
    }

    String displayName = "Replace `HttpClient.execute(..)` returning `HttpResponse` with `executeOpen(..)`";

    String description = "HttpClient 4's `HttpClient.execute(HttpUriRequest)`, `execute(HttpUriRequest, HttpContext)` and " +
            "`execute(HttpHost, HttpRequest, HttpContext)` returned a response with an entity. In 5.x these methods of the " +
            "`HttpClient` interface return an `HttpResponse`, which has no entity, so the migrated code that reads the " +
            "response as a `ClassicHttpResponse` does not compile. `executeOpen(HttpHost, ClassicHttpRequest, HttpContext)` " +
            "returns the `ClassicHttpResponse` and, like 4.x's `execute`, leaves it open for the caller to consume; " +
            "a `null` target is determined from the request, as `execute` does.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(EXECUTE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!EXECUTE.matches(m) || m.getSelect() == null || m.getMethodType() == null) {
                    return m;
                }
                List<JavaType> params = m.getMethodType().getParameterTypes();
                List<Expression> args = m.getArguments();
                for (JavaType param : params) {
                    if (isAny(param, RESPONSE_HANDLERS)) {
                        // the overloads that take a response handler return the handler's result
                        return m;
                    }
                }
                Expression host = null;
                Expression request;
                Expression context = null;
                if (params.size() == 1) {
                    request = args.get(0);
                } else if (params.size() == 2 && !isAny(params.get(0), HTTP_HOSTS)) {
                    request = args.get(0);
                    context = args.get(1);
                } else if (params.size() == 3) {
                    host = args.get(0);
                    request = args.get(1);
                    context = args.get(2);
                } else {
                    // execute(HttpHost, ClassicHttpRequest) already returns a ClassicHttpResponse
                    return m;
                }

                List<Object> parameters = new ArrayList<>();
                parameters.add(m.getSelect());
                StringBuilder code = new StringBuilder("#{any(" + HTTP_CLIENT + ")}.executeOpen(");
                if (host == null) {
                    code.append("null, ");
                } else {
                    code.append("#{any(" + HTTP_HOST + ")}, ");
                    parameters.add(host);
                }
                code.append("#{any(org.apache.hc.core5.http.ClassicHttpRequest)}, ");
                parameters.add(request);
                if (context == null) {
                    code.append("null)");
                } else {
                    code.append("#{any(" + HTTP_CONTEXT + ")})");
                    parameters.add(context);
                }
                return JavaTemplate.builder(code.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replace(), parameters.toArray());
            }
        });
    }
}

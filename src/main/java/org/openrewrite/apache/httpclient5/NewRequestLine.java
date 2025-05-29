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

import lombok.EqualsAndHashCode;
import lombok.Value;
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

@Value
@EqualsAndHashCode(callSuper = false)
public class NewRequestLine extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replaces deprecated `HttpRequestBase::getRequestLine()`";
    }

    @Override
    public String getDescription() {
        return "`HttpRequestBase::getStatusLine()` was removed in 5.x when `HttpRequestBase` was migrated to `HttpUriRequestBase`, " +
                "so we replace it with `new RequestLine(HttpRequest)`. " +
                "Ideally we will try to simply method chains for `getMethod`, `getUri` and `getProtocolVersion`, " +
                "but there are some scenarios where `RequestLine` object is assigned or used directly, and we need to " +
                "instantiate the object.";
    }

    private static final MethodMatcher MATCHER = new MethodMatcher("org.apache.hc.client5.http.classic.methods.HttpUriRequestBase getRequestLine()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MATCHER), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (MATCHER.matches(m)) {
                    maybeAddImport("org.apache.hc.core5.http.message.RequestLine");
                    return JavaTemplate.builder("new RequestLine(#{any(org.apache.hc.core5.http.HttpRequest)})")
                            .imports("org.apache.hc.core5.http.message.RequestLine")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect());
                }
                return m;
            }
        });
    }
}

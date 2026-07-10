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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateHttpAsyncMethods extends Recipe {

    private static final String FQN_OLD = "org.apache.http.nio.client.methods.HttpAsyncMethods";
    private static final String FQN_ASYNC_REQUEST_BUILDER = "org.apache.hc.core5.http.nio.support.AsyncRequestBuilder";
    private static final String FQN_ASYNC_ENTITY_PRODUCERS = "org.apache.hc.core5.http.nio.entity.AsyncEntityProducers";
    private static final String FQN_SIMPLE_RESPONSE_CONSUMER = "org.apache.hc.client5.http.async.methods.SimpleResponseConsumer";

    private static final MethodMatcher CREATE_POST = new MethodMatcher(FQN_OLD + " createPost(..)");
    private static final MethodMatcher CREATE_GET = new MethodMatcher(FQN_OLD + " createGet(..)");
    private static final MethodMatcher CREATE_CONSUMER = new MethodMatcher(FQN_OLD + " createConsumer()");

    String displayName = "Migrate `HttpAsyncMethods` factory calls to HttpClient 5.x";

    String description = "Rewrites `HttpAsyncMethods.createPost/createGet/createConsumer` from Apache " +
            "HttpAsyncClient 4.x to their HttpClient 5.x equivalents using `AsyncRequestBuilder`, " +
            "`AsyncEntityProducers`, and `SimpleResponseConsumer`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_OLD, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (CREATE_POST.matches(mi)) {
                    List<Expression> args = mi.getArguments();
                    if (args.size() != 3) {
                        return mi;
                    }
                    maybeRemoveImport(FQN_OLD);
                    maybeAddImport(FQN_ASYNC_REQUEST_BUILDER);
                    maybeAddImport(FQN_ASYNC_ENTITY_PRODUCERS);
                    return JavaTemplate.builder("AsyncRequestBuilder.post(#{any()}).setEntity(AsyncEntityProducers.create(#{any()}, #{any()})).build()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(FQN_ASYNC_REQUEST_BUILDER, FQN_ASYNC_ENTITY_PRODUCERS)
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(1), args.get(2));
                }
                if (CREATE_GET.matches(mi)) {
                    List<Expression> args = mi.getArguments();
                    if (args.size() != 1) {
                        return mi;
                    }
                    maybeRemoveImport(FQN_OLD);
                    maybeAddImport(FQN_ASYNC_REQUEST_BUILDER);
                    return JavaTemplate.builder("AsyncRequestBuilder.get(#{any()}).build()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(FQN_ASYNC_REQUEST_BUILDER)
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0));
                }
                if (CREATE_CONSUMER.matches(mi)) {
                    maybeRemoveImport(FQN_OLD);
                    maybeAddImport(FQN_SIMPLE_RESPONSE_CONSUMER);
                    return JavaTemplate.builder("SimpleResponseConsumer.create()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(FQN_SIMPLE_RESPONSE_CONSUMER)
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace());
                }
                return mi;
            }
        });
    }
}

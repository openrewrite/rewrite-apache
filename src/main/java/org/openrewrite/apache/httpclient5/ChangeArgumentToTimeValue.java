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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeArgumentToTimeValue extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find matching method invocations.",
            example = "org.apache.http.impl.nio.reactor.IOReactorConfig.Builder setSelectInterval(long)")
    String methodPattern;

    @Option(displayName = "Time unit",
            description = "The TimeUnit enum value we want to use to turn the original value into a TimeValue. Defaults to `MILLISECONDS`.",
            example = "MILLISECONDS",
            required = false)
    @Nullable
    TimeUnit timeUnit;

    String displayName = "Changes an argument (or pair of arguments) to a `TimeValue` for matched method invocations";

    String description = "In Apache Http Client 5.x migration, some methods that previously took a single `long` argument, or a pair of arguments " +
            "of type `long` and `TimeUnit` respectively, have changed to take a `TimeValue`. Previously in 4.x, all these single `long` argument " +
            "methods were implicitly having the value expressed in milliseconds. By default this recipe uses `TimeUnit.MILLISECONDS` for the " +
            "`TimeUnit` when creating a `TimeValue`. It is possible to specify this as a option. The `timeUnit` option will be ignored for cases " +
            "matching `*(long, TimeUnit).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final MethodMatcher matcher = new MethodMatcher(methodPattern);
        return Preconditions.check(new UsesMethod<>(matcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m)) {
                    maybeAddImport("org.apache.hc.core5.util.TimeValue");
                    if (m.getArguments().size() == 1) {
                        maybeAddImport("java.util.concurrent.TimeUnit");
                        return JavaTemplate
                                .builder("TimeValue.of(#{any()}, TimeUnit.#{})")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                                .imports("org.apache.hc.core5.util.TimeValue", "java.util.concurrent.TimeUnit")
                                .build()
                                .apply(
                                        updateCursor(m),
                                        m.getCoordinates().replaceArguments(),
                                        m.getArguments().get(0),
                                        timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS
                                );
                    } else if (m.getArguments().size() == 2) {
                        return JavaTemplate
                                .builder("TimeValue.of(#{any()}, #{any()})")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                                .imports("org.apache.hc.core5.util.TimeValue")
                                .build()
                                .apply(
                                        updateCursor(m),
                                        m.getCoordinates().replaceArguments(),
                                        m.getArguments().get(0),
                                        m.getArguments().get(1)
                                );
                    }
                }
                return m;
            }
        });
    }
}

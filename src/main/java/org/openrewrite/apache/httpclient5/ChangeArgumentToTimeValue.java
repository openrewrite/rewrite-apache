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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
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

import java.util.concurrent.TimeUnit;

@Value
@EqualsAndHashCode(callSuper = false)
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

    @Override
    public String getDisplayName() {
        return "Changes an argument to a TimeValue for matched method invocations";
    }

    @Override
    public String getDescription() {
        return "In Apache Http Client 5.x migration, some methods that previously took a single long argument have changed to take a TimeValue. " +
                "Previously in 4.x, all these methods were implicitly having the value expressed in milliseconds. By default this recipe uses " +
                "`TimeUnit.MILLISECONDS` for the TimeUnit when creating a TimeValue. It is possible to specify this as a parameter. Since all " +
                "affected methods of the Apache Http Client 5.x migration only have one long argument, the recipe applies with matched method " +
                "invocations of exactly one parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final MethodMatcher matcher = new MethodMatcher(methodPattern);
        return Preconditions.check(new UsesMethod<>(matcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m) && m.getArguments().size() == 1) {
                    JavaTemplate template = JavaTemplate
                            .builder("TimeValue.of(#{any()}, TimeUnit.#{})")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                            .imports("org.apache.hc.core5.util.TimeValue", "java.util.concurrent.TimeUnit")
                            .build();
                    Expression firstArg = m.getArguments().get(0);
                    m = template.apply(
                            updateCursor(m),
                            m.getCoordinates().replaceArguments(),
                            firstArg,
                            timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS
                    );
                    maybeAddImport("org.apache.hc.core5.util.TimeValue");
                    maybeAddImport("java.util.concurrent.TimeUnit");
                }
                return m;
            }
        });
    }
}

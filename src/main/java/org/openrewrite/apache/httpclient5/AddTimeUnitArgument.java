/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddTimeUnitArgument extends Recipe {

    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find matching method invocations.",
            example = "org.apache.http.client.config.RequestConfig.Builder setConnectionRequestTimeout(int)")
    String methodPattern;

    @Option(displayName = "Time unit",
            description = "The TimeUnit enum value we want to add to the method invocation. Defaults to `MILLISECONDS`.",
            example = "MILLISECONDS",
            required = false)
    @Nullable
    TimeUnit timeUnit;

    String displayName = "Adds a TimeUnit argument to the matched method invocations";

    String description = "In Apache Http Client 5.x migration, an extra TimeUnit argument is required in the timeout and duration methods. " +
                "Previously in 4.x, all these methods were implicitly having the timeout or duration expressed in milliseconds, " +
                "but in 5.x the unit of the timeout or duration is required. So, by default this recipe adds " +
                "`TimeUnit.MILLISECONDS`, it is possible to specify this as a parameter. Since all affected methods of " +
                "the Apache Http Client 5.x migration only have one integer/long argument, the recipe applies with matched method " +
                "invocations of exactly one parameter.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher matcher = new MethodMatcher(methodPattern);

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m)) {
                    J.MethodInvocation templated = JavaTemplate
                            .builder("TimeUnit.#{}")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports("java.util.concurrent.TimeUnit")
                            .build()
                            .apply(
                                    updateCursor(m),
                                    m.getCoordinates().replaceArguments(),
                                    timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS
                            );
                    Expression timeUnitArgument = templated.getArguments().get(0).withPrefix(Space.SINGLE_SPACE);
                    m = m.withArguments(ListUtils.concat(m.getArguments(), timeUnitArgument));

                    JavaType.Method methodType = m.getMethodType();
                    if (methodType != null) {
                        methodType = methodType
                                .withParameterTypes(ListUtils.concat(methodType.getParameterTypes(), timeUnitArgument.getType()))
                                .withParameterNames(ListUtils.concat(methodType.getParameterNames(), "timeUnit"));
                        m = m.withMethodType(methodType).withName(m.getName().withType(methodType));
                    }
                    maybeAddImport("java.util.concurrent.TimeUnit");
                }
                return m;
            }
        };
    }
}

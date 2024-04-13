/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddTimeUnitArgument extends Recipe {

    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find matching method invocations.",
            example = "org.apache.http.client.config.RequestConfig.Builder setConnectionRequestTimeout(int)")
    String methodPattern;

    @Option(displayName = "Time Unit",
            description = "The TimeUnit enum value we want to add to the method invocation. Defaults to `MILLISECONDS`.",
            example = "MILLISECONDS",
            required = false)
    @Nullable
    TimeUnit timeUnit;

    @Override
    public String getDisplayName() {
        return "Adds a TimeUnit argument to the matched method invocations";
    }

    @Override
    public String getDescription() {
        return "In Apache Http Client 5.x migration, an extra TimeUnit argument is required in the timeout and duration methods. " +
                "Previously in 4.x, all these methods were implicitly having the timeout or duration expressed in milliseconds, " +
                "but in 5.x the unit of the timeout or duration is required. So, by default this recipe adds " +
                "`TimeUnit.MILLISECONDS`, it is possible to specify this as a parameter. Since all affected methods of " +
                "the Apache Http Client 5.x migration only have one integer/long argument, the recipe applies with matched method " +
                "invocations of exactly one parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher matcher = new MethodMatcher(methodPattern);

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m)) {
                    JavaTemplate template = JavaTemplate
                            .builder(StringUtils.repeat("#{any()}, ", m.getArguments().size()) + "TimeUnit.#{}")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports("java.util.concurrent.TimeUnit")
                            .build();

                    List<Object> arguments = new ArrayList<>(m.getArguments());
                    arguments.add(timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS);

                    m = template.apply(
                            updateCursor(m),
                            m.getCoordinates().replaceArguments(),
                            arguments.toArray(new Object[0])
                    );
                    maybeAddImport("java.util.concurrent.TimeUnit");
                }
                return m;
            }
        };
    }
}

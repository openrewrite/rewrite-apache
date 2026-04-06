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
package org.openrewrite.apache.commons.lang;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.apache.commons.lang.RepeatableArgumentMatcher.isRepeatableArgument;

public class DefaultIfBlankToJdk extends Recipe {
    private static final String DEFAULT_IF_BLANK_REPLACEMENT =
            "#{any(String)} == null || #{any(String)}.isBlank() ? #{any(String)} : #{any(String)}";

    private static final MethodMatcher defaultIfBlankMatcher = new MethodMatcher("*..StringUtils defaultIfBlank(..)");

    @Getter
    final String displayName = "Replace StringUtils#defaultIfBlank(String, String) with JDK equivalent";

    @Getter
    final String description = "Replace `StringUtils#defaultIfBlank(s, fallback)` with `s == null || s.isBlank() ? fallback : s`.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> precondition = Preconditions.and(
                new UsesJavaVersion<>(11),
                new UsesMethod<>("org.apache.commons.lang3.StringUtils defaultIfBlank(*, *)"));

        return Preconditions.check(precondition, new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (!defaultIfBlankMatcher.matches(mi) || !isRepeatableArgument(mi.getArguments().get(0))) {
                    return super.visitMethodInvocation(mi, ctx);
                }

                Expression arg0 = mi.getArguments().get(0);
                Expression arg1 = mi.getArguments().get(1);
                maybeRemoveImport("org.apache.commons.lang3.StringUtils");
                return JavaTemplate.apply(DEFAULT_IF_BLANK_REPLACEMENT,
                        updateCursor(mi), mi.getCoordinates().replace(),
                        arg0, arg0, arg1, arg0);
            }
        });
    }
}

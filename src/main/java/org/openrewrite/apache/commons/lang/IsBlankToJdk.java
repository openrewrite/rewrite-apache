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

public class IsBlankToJdk extends Recipe {

    @Getter
    final String displayName = "Replace any StringUtils#isBlank(String) and #isNotBlank(String)";

    @Getter
    final String description = "Replace any `StringUtils#isBlank(String)` and `#isNotBlank(String)` with `s == null || s.isBlank()` and `s != null && !s.isBlank()`.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> precondition = Preconditions.and(
                new UsesJavaVersion<>(11),
                Preconditions.or(
                        new UsesMethod<>("org.apache.commons.lang3.StringUtils isBlank(..)"),
                        new UsesMethod<>("org.apache.commons.lang3.StringUtils isNotBlank(..)"),
                        new UsesMethod<>("org.apache.maven.shared.utils.StringUtils isBlank(..)"),
                        new UsesMethod<>("org.apache.maven.shared.utils.StringUtils isNotBlank(..)"),
                        new UsesMethod<>("org.codehaus.plexus.util.StringUtils isBlank(..)"),
                        new UsesMethod<>("org.codehaus.plexus.util.StringUtils isNotBlank(..)")));

        return Preconditions.check(precondition, new JavaVisitor<ExecutionContext>() {
            private static final String IS_BLANK_REPLACEMENT = "#{any(String)} == null || #{any(String)}.isBlank()";
            private static final String IS_NOT_BLANK_REPLACEMENT = "#{any(String)} != null && !#{any(String)}.isBlank()";

            private final MethodMatcher isBlankMatcher = new MethodMatcher("*..StringUtils isBlank(..)");
            private final MethodMatcher isNotBlankMatcher = new MethodMatcher("*..StringUtils isNotBlank(..)");

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                if (unary.getOperator() != J.Unary.Type.Not) {
                    return super.visitUnary(unary, ctx);
                }

                Expression expr = unary.getExpression().unwrap();

                if (!(expr instanceof J.MethodInvocation)) {
                    return super.visitUnary(unary, ctx);
                }

                J.MethodInvocation mi = (J.MethodInvocation) expr;
                boolean isBlankCall = isBlankMatcher.matches(mi);
                if (!isBlankCall && !isNotBlankMatcher.matches(mi)) {
                    return super.visitUnary(unary, ctx);
                }

                Expression arg = mi.getArguments().get(0);
                if (!isRepeatableArgument(arg)) {
                    return super.visitUnary(unary, ctx);
                }

                // Swap: !isBlank -> isNotBlank replacement, !isNotBlank -> isBlank replacement
                String template = isBlankCall ? IS_NOT_BLANK_REPLACEMENT : IS_BLANK_REPLACEMENT;
                maybeRemoveImport("org.apache.commons.lang3.StringUtils");
                maybeRemoveImport("org.apache.maven.shared.utils.StringUtils");
                maybeRemoveImport("org.codehaus.plexus.util.StringUtils");

                return JavaTemplate.apply(template, updateCursor(unary), unary.getCoordinates().replace(), arg, arg);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                boolean isBlankCall = isBlankMatcher.matches(mi);
                if (!isBlankCall && !isNotBlankMatcher.matches(mi)) {
                    return mi;
                }

                Expression arg = mi.getArguments().get(0);

                // Replace StringUtils.isBlank(var) with var == null || var.isBlank()
                if (isRepeatableArgument(arg)) {
                    String template = isBlankCall ? IS_BLANK_REPLACEMENT : IS_NOT_BLANK_REPLACEMENT;
                    maybeRemoveImport("org.apache.commons.lang3.StringUtils");
                    maybeRemoveImport("org.apache.maven.shared.utils.StringUtils");
                    maybeRemoveImport("org.codehaus.plexus.util.StringUtils");
    
                    return JavaTemplate.apply(template, updateCursor(mi), mi.getCoordinates().replace(), arg, arg);
                }

                return super.visitMethodInvocation(mi, ctx);
            }
        });
    }
}

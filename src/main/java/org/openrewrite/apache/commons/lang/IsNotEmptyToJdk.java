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
package org.openrewrite.apache.commons.lang;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.template.Semantics;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.apache.commons.lang.RepeatableArgumentMatcher.isRepeatableArgument;

public class IsNotEmptyToJdk extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace any StringUtils#isEmpty(String) and #isNotEmpty(String)";
    }

    @Override
    public String getDescription() {
        return "Replace any `StringUtils#isEmpty(String)` and `#isNotEmpty(String)` with `s == null || s.isEmpty()` and `s != null && !s.isEmpty()`.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("apache", "commons"));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> precondition = Preconditions.or(
                new UsesMethod<>("org.apache.commons.lang3.StringUtils isEmpty(..)"),
                new UsesMethod<>("org.apache.commons.lang3.StringUtils isNotEmpty(..)"),
                new UsesMethod<>("org.apache.maven.shared.utils.StringUtils isEmpty(..)"),
                new UsesMethod<>("org.apache.maven.shared.utils.StringUtils isNotEmpty(..)"),
                new UsesMethod<>("org.codehaus.plexus.util.StringUtils isEmpty(..)"),
                new UsesMethod<>("org.codehaus.plexus.util.StringUtils isNotEmpty(..)"),
                new UsesMethod<>("java.lang.String length()"));

        return Preconditions.check(precondition, new JavaVisitor<ExecutionContext>() {
            private final MethodMatcher isEmptyMatcher = new MethodMatcher("*..StringUtils isEmpty(..)");
            private final MethodMatcher isNotEmptyMatcher = new MethodMatcher("*..StringUtils isNotEmpty(..)");
            private final MethodMatcher lengthMatcher     = new MethodMatcher("java.lang.String length()");
            private final MethodMatcher trimMatcher = new MethodMatcher("java.lang.String trim()");

            private final JavaTemplate isEmptyReplacement = Semantics.expression(this, "IsEmpty", (String s) -> (s == null || s.isEmpty())).build();
            private final JavaTemplate isNotEmptyReplacement = Semantics.expression(this, "IsNotEmpty", (String s) -> (s != null && !s.isEmpty())).build();
            private final JavaTemplate isEmptyTrimmed = Semantics.expression(this, "IsEmptyTrimmed", (String s) -> s.trim().isEmpty()).build();
            private final JavaTemplate isNotEmptyTrimmed = Semantics.expression(this, "IsNotEmptyTrimmed", (String s) -> !s.trim().isEmpty()).build();
            private final JavaTemplate isEmptyLength = Semantics.expression(this, "IsEmptyLength", (String s) -> s.isEmpty()).build();
            private final JavaTemplate isNotEmptyLength = Semantics.expression(this, "IsNotEmptyLength", (String s) -> !s.isEmpty()).build();
            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                boolean isEmptyCall = isEmptyMatcher.matches(mi);
                if (!isEmptyCall && !isNotEmptyMatcher.matches(mi)) {
                    return mi;
                }

                Expression arg = mi.getArguments().get(0);

                // Replace StringUtils.isEmpty(var) with var == null || var.isEmpty()
                if (isRepeatableArgument(arg)) {
                    JavaTemplate replacementTemplate = isEmptyCall ? isEmptyReplacement : isNotEmptyReplacement;
                    // Maybe remove imports
                    maybeRemoveImport("org.apache.commons.lang3.StringUtils");
                    maybeRemoveImport("org.apache.maven.shared.utils.StringUtils");
                    maybeRemoveImport("org.codehaus.plexus.util.StringUtils");
                    // Remove excess parentheses inserted in lambda that may be required depending on the context
                    doAfterVisit(new org.openrewrite.staticanalysis.UnnecessaryParentheses().getVisitor());
                    return replacementTemplate.apply(updateCursor(mi), mi.getCoordinates().replace(), arg);
                }

                // Replace StringUtils.isEmpty(var.trim()) with var.trim().isEmpty()
                if (trimMatcher.matches(arg) &&
                        (((J.MethodInvocation) arg).getSelect() instanceof J.Identifier || ((J.MethodInvocation) arg).getSelect() instanceof J.FieldAccess)) {
                    JavaTemplate replacementTemplate = isEmptyCall ? isEmptyTrimmed : isNotEmptyTrimmed;
                    // Maybe remove imports
                    maybeRemoveImport("org.apache.commons.lang3.StringUtils");
                    maybeRemoveImport("org.apache.maven.shared.utils.StringUtils");
                    maybeRemoveImport("org.codehaus.plexus.util.StringUtils");
                    return replacementTemplate.apply(updateCursor(mi), mi.getCoordinates().replace(), ((J.MethodInvocation) arg).getSelect());
                }

                return super.visitMethodInvocation(mi, ctx);
            }

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                // s.length() == 0  or  s.length() != 0
                if (binary.getLeft() instanceof J.MethodInvocation) {
                    J.MethodInvocation left = (J.MethodInvocation) binary.getLeft();
                    if (lengthMatcher.matches(left)
                            && binary.getRight() instanceof J.Literal) {

                        J.Literal lit = (J.Literal) binary.getRight();
                        Object value = lit.getValue();
                        if (value instanceof Number && ((Number) value).intValue() == 0) {
                            JavaTemplate tpl = binary.getOperator() == J.Binary.Type.Equal
                                    ? isEmptyLength
                                    : isNotEmptyLength;
                            return tpl.apply(
                                    updateCursor(binary),
                                    binary.getCoordinates().replace(),
                                    left.getSelect()
                            );
                        }
                    }
                }

                // s.trim().length() == 0  or  s.trim().length() != 0
                if (binary.getLeft() instanceof J.MethodInvocation) {
                    J.MethodInvocation lengthCall = (J.MethodInvocation) binary.getLeft();
                    if (trimMatcher.matches(lengthCall.getSelect())
                            && lengthMatcher.matches(lengthCall)
                            && binary.getRight() instanceof J.Literal) {

                        J.Literal lit = (J.Literal) binary.getRight();
                        Object value = lit.getValue();
                        if (value instanceof Number && ((Number) value).intValue() == 0) {
                            JavaTemplate tpl = binary.getOperator() == J.Binary.Type.Equal
                                    ? isEmptyTrimmed
                                    : isNotEmptyTrimmed;
                            return tpl.apply(
                                    updateCursor(binary),
                                    binary.getCoordinates().replace(),
                                    ((J.MethodInvocation) lengthCall.getSelect()).getSelect()
                            );
                        }
                    }
                }

                return super.visitBinary(binary, ctx);
            }
        });
    }
}

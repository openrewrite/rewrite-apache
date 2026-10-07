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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class SystemUtilsIsJavaVersionAtLeastToJavaVersion extends Recipe {

    // The Commons Lang 2.x overloads survive a rename of their declaring type, so match both package names
    private static final List<MethodMatcher> MATCHERS = Stream
            .of("org.apache.commons.lang.SystemUtils", "org.apache.commons.lang3.SystemUtils")
            .flatMap(type -> Stream.of(
                    new MethodMatcher(type + " isJavaVersionAtLeast(float)"),
                    new MethodMatcher(type + " isJavaVersionAtLeast(int)")))
            .collect(toList());

    @Getter
    final String displayName = "Replace `SystemUtils#isJavaVersionAtLeast(float)` and `(int)` with the `JavaVersion` overload";

    @Getter
    final String description = "Commons Lang 2.x compared Java versions as a `float` such as `1.4f` or an `int` such " +
                               "as `140`; Commons Lang 3.x only accepts a `JavaVersion`. Calls whose argument is not a " +
                               "literal that names a `JavaVersion` constant are left alone.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons", "lang"));

    private static @Nullable String javaVersionConstant(Expression argument) {
        if (!(argument instanceof J.Literal)) {
            return null;
        }
        Object value = ((J.Literal) argument).getValue();
        int tenths;
        if (value instanceof Float || value instanceof Double) {
            double d = ((Number) value).doubleValue() * 10;
            tenths = (int) Math.round(d);
            if (Math.abs(d - tenths) > 1e-6) {
                return null;
            }
        } else if (value instanceof Integer) {
            int i = (Integer) value;
            if (i % 10 != 0) {
                return null;
            }
            tenths = i / 10;
        } else {
            return null;
        }
        if (tenths == 19) {
            return "JAVA_9";
        }
        return 11 <= tenths && tenths <= 18 ? "JAVA_1_" + (tenths - 10) : null;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> precondition = Preconditions.or(MATCHERS.stream()
                .map(matcher -> (TreeVisitor<?, ExecutionContext>) new UsesMethod<ExecutionContext>(matcher))
                .toArray(TreeVisitor[]::new));

        return Preconditions.check(precondition, new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (MATCHERS.stream().noneMatch(matcher -> matcher.matches(mi))) {
                    return super.visitMethodInvocation(mi, ctx);
                }

                String constant = javaVersionConstant(mi.getArguments().get(0));
                if (constant == null) {
                    return super.visitMethodInvocation(mi, ctx);
                }

                maybeAddImport("org.apache.commons.lang3.JavaVersion");
                return JavaTemplate.builder("JavaVersion." + constant)
                        .imports("org.apache.commons.lang3.JavaVersion")
                        .javaParser(JavaParser.fromJavaVersion().classpath("commons-lang3"))
                        .build()
                        .apply(updateCursor(mi), mi.getCoordinates().replaceArguments());
            }
        });
    }
}

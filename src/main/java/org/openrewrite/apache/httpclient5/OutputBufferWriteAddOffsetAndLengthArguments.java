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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.template.Matcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class OutputBufferWriteAddOffsetAndLengthArguments extends Recipe {
    private static final String writePattern = "org.apache.hc.core5.http.nio.support.classic.SharedOutputBuffer write(byte[])";
    private static final MethodMatcher writeMatcher = new MethodMatcher(writePattern);

    @Override
    public String getDisplayName() {
        return "Adds offset and length arguments to the write method of SharedOutputBuffer";
    }

    @Override
    public String getDescription() {
        return "In Apache Http Client 5.x migration, the shortened form of the `write(byte[])` has been removed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(writePattern), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m =  super.visitMethodInvocation(method, ctx);
                if (writeMatcher.matches(m)) {
                    Expression firstArg = m.getArguments().get(0);
                    Matcher<Expression> simpleCaseMatcher = new RepeatableByteArrayArgumentMatcher();
                    Matcher<Expression> messyCaseMatcher = new NonRepeatableByteArrayArgumentMatcher();
                    JavaTemplate after = JavaTemplate
                            .builder("#{any(org.apache.hc.core5.http.nio.support.classic.SharedOutputBuffer)}.write(#{any(byte[])}, 0, #{any(byte[])}.length)")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                            .build();
                    if (simpleCaseMatcher.matches(firstArg)) {
                        return after.apply(getCursor(), m.getCoordinates().replace(), requireNonNull(m.getSelect()), firstArg, firstArg);
                    }
                    if (messyCaseMatcher.matches(firstArg)) {
                        return after.apply(getCursor(), m.getCoordinates().replace(), requireNonNull(m.getSelect()), firstArg, firstArg)
                                .withComments(ListUtils.concat(m.getComments(),
                                        new TextComment(true, " TODO: Please check that repeated obtaining of byte[] is safe here ", m.getPrefix().getWhitespace(), Markers.EMPTY)));
                    }
                    return m;
                }
                return m;
            }
        });
    }
}

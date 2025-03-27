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
package org.openrewrite.apache.poi;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReplaceSetBoldweightWithSetBold extends Recipe {
    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Replace `Font.setBoldweight(short)` with `Font.setBold(boolean)";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Replace `Font.setBoldweight(short)` or equivalent with `Font.setBold(boolean)`.";
    }

    private static final MethodMatcher SET_BOLDWEIGHT = new MethodMatcher("org.apache.poi.ss.usermodel.Font setBoldweight(short)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return Preconditions.check(new UsesMethod<>(SET_BOLDWEIGHT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if(!SET_BOLDWEIGHT.matches(m) || m.getSelect() == null) {
                    return m;
                }
                if(isBoldweightNormal(m, ctx)) {
                    m = JavaTemplate.builder("#{font:any(org.apache.poi.ss.usermodel.Font)}.setBold(false)")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getSelect());
                } else if(isBoldweightBold(m, ctx)) {
                    m = JavaTemplate.builder("#{font:any(org.apache.poi.ss.usermodel.Font)}.setBold(true)")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getSelect());
                }
                return m;
            }

            private boolean isBoldweightNormal(J.MethodInvocation method, ExecutionContext ctx) {
                AtomicBoolean found = new AtomicBoolean(false);
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                        if (Objects.equals(400, literal.getValue())) {
                            found.set(true);
                        }
                        return super.visitLiteral(literal, executionContext);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext executionContext) {
                        if("BOLDWEIGHT_NORMAL".equals(identifier.getSimpleName())) {
                            found.set(true);
                        }
                        return super.visitIdentifier(identifier, executionContext);
                    }
                }.visit(method, ctx);
                return found.get();
            }

            private boolean isBoldweightBold(J.MethodInvocation method, ExecutionContext ctx) {
                AtomicBoolean found = new AtomicBoolean(false);
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                        if (Objects.equals(700, literal.getValue())) {
                            found.set(true);
                        }
                        return super.visitLiteral(literal, executionContext);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext executionContext) {
                        if("BOLDWEIGHT_BOLD".equals(identifier.getSimpleName())) {
                            found.set(true);
                        }
                        return super.visitIdentifier(identifier, executionContext);
                    }
                }.visit(method, ctx);
                return found.get();
            }
        });
    }
}

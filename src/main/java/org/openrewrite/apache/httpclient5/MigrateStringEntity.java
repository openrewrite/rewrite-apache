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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.J.CompilationUnit;

@Value
@EqualsAndHashCode(callSuper = false)
public class MigrateStringEntity extends Recipe {

    private static final String METHOD_PATTERN = "org.apache.http.entity.StringEntity setContentEncoding(..)";
    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher(METHOD_PATTERN);

    @Override
    public String getDisplayName() {
        return "Migrates StringEntity.setContentEncoding()";
    }

    @Override
    public String getDescription() {
        return "Replace `org.apache.http.entity.StringEntity.setContentEncoding` with constructor arg.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(METHOD_MATCHER),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitCompilationUnit(CompilationUnit cu, ExecutionContext ctx) {
                        return super.visitCompilationUnit(cu, ctx);
                        return super.visitCompilationUnit(cu, p);
                    }
                    @Override
                    public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess f = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                        // Type was already relocated by previous recipe
                        if ("ANY".equals(f.getSimpleName()) && TypeUtils.isOfClassType(f.getTarget().getType(), "org.apache.hc.client5.http.auth.AuthScope")) {
                            maybeAddImport("org.apache.hc.client5.http.auth.AuthScope");
                            return JavaTemplate.builder("new AuthScope(null, -1)")
                                    .imports("org.apache.hc.client5.http.auth.AuthScope")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5"))
                                    .build()
                                    .apply(updateCursor(f), f.getCoordinates().replace());
                        }
                        return f;
                    }
                });
    }
}

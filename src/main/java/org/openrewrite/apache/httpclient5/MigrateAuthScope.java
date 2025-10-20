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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateAuthScope extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replaces `AuthScope.ANY`";
    }

    @Override
    public String getDescription() {
        return "Replace removed constant `org.apache.http.auth.AuthScope.AuthScope.ANY` with `new org.apache.hc.client5.http.auth.AuthScope(null, -1)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.apache.hc.client5.http.auth.AuthScope", false),
                new JavaVisitor<ExecutionContext>() {
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

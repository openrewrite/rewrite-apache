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
package org.openrewrite.apache.httpclient5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateHttpStatusConstantsInterface extends Recipe {

    private static final String HTTP_STATUS = "org.apache.http.HttpStatus";

    String displayName = "Stop inheriting constants from HttpClient 4 `HttpStatus`";

    String description = "HttpClient 4's `org.apache.http.HttpStatus` is an interface of constants, while HttpClient 5's " +
            "`org.apache.hc.core5.http.HttpStatus` is a final class. Remove `HttpStatus` from `implements` (and from an " +
            "interface's `extends`), and qualify the `SC_*` constants that were inherited through it as `HttpStatus.SC_*`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HTTP_STATUS, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                if (c.getImplements() == null ||
                        c.getImplements().stream().noneMatch(i -> TypeUtils.isOfClassType(i.getType(), HTTP_STATUS))) {
                    return c;
                }
                J.ClassDeclaration.Padding padding = c.getPadding();
                if (c.getImplements().size() == 1) {
                    c = padding.withImplements(null);
                } else {
                    boolean firstRemoved = TypeUtils.isOfClassType(c.getImplements().get(0).getType(), HTTP_STATUS);
                    c = c.withImplements(ListUtils.filter(c.getImplements(),
                            i -> !TypeUtils.isOfClassType(i.getType(), HTTP_STATUS)));
                    if (firstRemoved) {
                        c = c.withImplements(ListUtils.mapFirst(c.getImplements(),
                                i -> i.withPrefix(classDecl.getImplements().get(0).getPrefix())));
                    }
                }
                maybeRemoveImport(HTTP_STATUS);
                return c;
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
                if (!isHttpStatusConstant(id.getFieldType()) || getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess) {
                    return id;
                }
                // Only constants inherited by an enclosing class; a static import keeps resolving after migration
                for (Object value : (Iterable<Object>) () -> getCursor().getPath()) {
                    if (value instanceof J.ClassDeclaration) {
                        JavaType.FullyQualified type = ((J.ClassDeclaration) value).getType();
                        if (TypeUtils.isOfClassType(type, HTTP_STATUS)) {
                            return id;
                        }
                        if (TypeUtils.isAssignableTo(HTTP_STATUS, type)) {
                            return qualify(id, ctx);
                        }
                    }
                }
                return id;
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                if (!isHttpStatusConstant(f.getName().getFieldType()) || !isTypeReference(f.getTarget()) ||
                        TypeUtils.isOfClassType(f.getTarget().getType(), HTTP_STATUS)) {
                    return f;
                }
                // `MyConstants.SC_OK`, where `MyConstants implements HttpStatus`
                return qualify(f, ctx);
            }

            private boolean isHttpStatusConstant(JavaType.@Nullable Variable variable) {
                return variable != null && TypeUtils.isOfClassType(variable.getOwner(), HTTP_STATUS);
            }

            private boolean isTypeReference(J target) {
                if (target instanceof J.Identifier) {
                    return ((J.Identifier) target).getFieldType() == null && target instanceof TypeTree &&
                            ((J.Identifier) target).getType() instanceof JavaType.FullyQualified;
                }
                return target instanceof J.FieldAccess && ((J.FieldAccess) target).getName().getFieldType() == null &&
                        ((J.FieldAccess) target).getType() instanceof JavaType.FullyQualified;
            }

            private J qualify(Expression tree, ExecutionContext ctx) {
                String name = tree instanceof J.Identifier ?
                        ((J.Identifier) tree).getSimpleName() :
                        ((J.FieldAccess) tree).getSimpleName();
                maybeAddImport(HTTP_STATUS);
                return JavaTemplate.builder("HttpStatus." + name)
                        .imports(HTTP_STATUS)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore-4"))
                        .build()
                        .apply(getCursor(), tree.getCoordinates().replace())
                        .withPrefix(tree.getPrefix());
            }
        });
    }
}

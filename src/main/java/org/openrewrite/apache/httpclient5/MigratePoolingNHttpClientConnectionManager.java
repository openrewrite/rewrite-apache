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
import lombok.Getter;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class MigratePoolingNHttpClientConnectionManager extends Recipe {

    private static final String FQN_OLD = "org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager";
    private static final String FQN_NEW = "org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager";
    private static final String FQN_BUILDER = "org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder";

    @Getter
    String displayName = "Migrate `PoolingNHttpClientConnectionManager` to `PoolingAsyncClientConnectionManager`";

    @Getter
    String description = "Migrates `PoolingNHttpClientConnectionManager` from Apache HttpAsyncClient 4.x to " +
                         "`PoolingAsyncClientConnectionManager` in HttpClient 5.x using the builder pattern.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_OLD, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                JavaType type = nc.getType();
                if (TypeUtils.isOfClassType(type, FQN_OLD)) {
                    maybeAddImport(FQN_BUILDER);
                    maybeRemoveImport(FQN_OLD);
                    return JavaTemplate.builder("PoolingAsyncClientConnectionManagerBuilder.create().build()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .imports(FQN_BUILDER)
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace());
                }
                return nc;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                boolean hasRelevantTypeExpression = multiVariable.getTypeExpression() != null && TypeUtils.isOfClassType(multiVariable.getTypeExpression().getType(), FQN_OLD);
                J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                if (hasRelevantTypeExpression) {
                    maybeAddImport(FQN_NEW);
                    maybeRemoveImport(FQN_OLD);
                    JavaType.FullyQualified newType = JavaType.ShallowClass.build(FQN_NEW);
                    if (vd.getTypeExpression() instanceof J.Identifier) {
                        vd = vd.withTypeExpression(((J.Identifier)vd.getTypeExpression())
                                .withType(newType)
                                .withSimpleName("PoolingAsyncClientConnectionManager")
                        );
                    } else if (vd.getTypeExpression() instanceof J.FieldAccess) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) vd.getTypeExpression();
                        vd = vd.withTypeExpression(new J.Identifier(
                                fieldAccess.getId(),
                                fieldAccess.getPrefix(),
                                fieldAccess.getMarkers(),
                                fieldAccess.getName().getAnnotations(),
                                "PoolingAsyncClientConnectionManager",
                                newType,
                                null
                        ));
                    }
                    vd = vd.withVariables(ListUtils.map(vd.getVariables(), v -> {
                        if (v.getVariableType() != null && TypeUtils.isOfClassType(v.getType(), FQN_OLD)) {
                            v = v.withVariableType(v.getVariableType().withType(newType))
                                    .withName(v.getName().withType(newType));
                            if (v.getName().getFieldType() != null) {
                                v = v.withName(
                                        v.getName().withFieldType(
                                                v.getName().getFieldType().withType(newType)));
                            }
                        }
                        return v;
                    }));
                }
                return vd;
            }



            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                boolean hasRelevantReturnTypeExpression = method.getReturnTypeExpression() != null && TypeUtils.isOfClassType(method.getReturnTypeExpression().getType(), FQN_OLD);
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (hasRelevantReturnTypeExpression) {
                    maybeAddImport(FQN_NEW);
                    maybeRemoveImport(FQN_OLD);
                    JavaType.FullyQualified newType = JavaType.ShallowClass.build(FQN_NEW);
                    if (md.getReturnTypeExpression() instanceof J.Identifier) {
                        md = md.withReturnTypeExpression(((J.Identifier)md.getReturnTypeExpression())
                                .withType(newType)
                                .withSimpleName("PoolingAsyncClientConnectionManager")
                        );
                    } else if (md.getReturnTypeExpression() instanceof J.FieldAccess) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) md.getReturnTypeExpression();
                        md = md.withReturnTypeExpression(new J.Identifier(
                                fieldAccess.getId(),
                                fieldAccess.getPrefix(),
                                fieldAccess.getMarkers(),
                                fieldAccess.getName().getAnnotations(),
                                "PoolingAsyncClientConnectionManager",
                                newType,
                                null
                        ));
                    }
                }
                return md;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (mi.getMethodType() != null && TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), FQN_OLD)) {
                    JavaType.FullyQualified newType = JavaType.ShallowClass.build(FQN_NEW);
                    JavaType.Method updatedMethodType = mi.getMethodType().withDeclaringType(newType);
                    mi = mi.withMethodType(updatedMethodType);
                    if (mi.getName().getType() != null) {
                        mi = mi.withName(mi.getName().withType(updatedMethodType));
                    }
                }
                return mi;
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
                if (TypeUtils.isOfClassType(id.getType(), FQN_OLD) && getCursor().firstEnclosing(J.Import.class) == null) {
                    JavaType.FullyQualified newType = JavaType.ShallowClass.build(FQN_NEW);
                    id = id.withType(newType);
                    if (id.getFieldType() != null && TypeUtils.isOfClassType(id.getFieldType().getType(), FQN_OLD)) {
                        id = id.withFieldType(id.getFieldType().withType(newType));
                    }
                }
                return id;
            }
        });
    }
}

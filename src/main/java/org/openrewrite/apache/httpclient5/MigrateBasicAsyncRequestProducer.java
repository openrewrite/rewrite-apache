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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateBasicAsyncRequestProducer extends Recipe {

    private static final String FQN_PRODUCER = "org.apache.http.nio.protocol.BasicAsyncRequestProducer";
    private static final String FQN_HTTP_HOST = "org.apache.http.HttpHost";
    private static final String FQN_ASYNC_REQUEST_BUILDER = "org.apache.hc.core5.http.nio.support.AsyncRequestBuilder";
    private static final String FQN_ASYNC_ENTITY_PRODUCERS = "org.apache.hc.core5.http.nio.entity.AsyncEntityProducers";

    private static final String FQN_N_STRING_ENTITY = "org.apache.http.nio.entity.NStringEntity";
    private static final String FQN_N_BYTE_ARRAY_ENTITY = "org.apache.http.nio.entity.NByteArrayEntity";
    private static final String FQN_N_FILE_ENTITY = "org.apache.http.nio.entity.NFileEntity";

    private static final Map<String, String> VERB_BY_HTTP_METHOD = new HashMap<String, String>() {{
        put("org.apache.http.client.methods.HttpGet", "get");
        put("org.apache.http.client.methods.HttpPost", "post");
        put("org.apache.http.client.methods.HttpPut", "put");
        put("org.apache.http.client.methods.HttpDelete", "delete");
    }};

    String displayName = "Migrate `BasicAsyncRequestProducer` to `AsyncRequestBuilder`";

    String description = "Rewrites `new BasicAsyncRequestProducer(HttpHost.create(uri), new HttpMethod(path).setEntity(new N*Entity(...)))` " +
            "to `AsyncRequestBuilder.<verb>(uri).setEntity(AsyncEntityProducers.create(...)).build()`. " +
            "Sites that do not match this specific shape are left alone for later migration steps.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_PRODUCER, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (!isNewClassOfName(nc, "BasicAsyncRequestProducer", FQN_PRODUCER)) {
                    return nc;
                }
                List<Expression> args = nc.getArguments();
                if (args == null || args.size() != 2) {
                    return nc;
                }

                String uriLiteral = extractHttpHostCreateLiteral(args.get(0));
                if (uriLiteral == null) {
                    return nc;
                }

                Expression requestArg = args.get(1);
                MethodInvocationInfo setEntity = asSetEntityChain(requestArg);
                if (setEntity == null) {
                    return nc;
                }

                String pathLiteral = extractHttpMethodPathLiteral(setEntity.select);
                String verb = extractHttpMethodVerb(setEntity.select);
                if (pathLiteral == null || verb == null) {
                    return nc;
                }

                J.NewClass entityCtor = asNEntityCtor(setEntity.argument);
                if (entityCtor == null) {
                    return nc;
                }

                String combinedUri = joinUriAndPath(uriLiteral, pathLiteral);
                List<Expression> entityArgs = entityCtor.getArguments();
                if (entityArgs == null || entityArgs.isEmpty()) {
                    return nc;
                }

                maybeRemoveImport(FQN_PRODUCER);
                maybeRemoveImport(FQN_HTTP_HOST);
                maybeRemoveImport(FQN_N_STRING_ENTITY);
                maybeRemoveImport(FQN_N_BYTE_ARRAY_ENTITY);
                maybeRemoveImport(FQN_N_FILE_ENTITY);
                maybeAddImport(FQN_ASYNC_REQUEST_BUILDER);
                maybeAddImport(FQN_ASYNC_ENTITY_PRODUCERS);

                StringBuilder producer = new StringBuilder("AsyncEntityProducers.create(");
                for (int i = 0; i < entityArgs.size(); i++) {
                    if (i > 0) {
                        producer.append(", ");
                    }
                    producer.append("#{any()}");
                }
                producer.append(")");

                String template = "AsyncRequestBuilder." + verb + "(\"" + combinedUri + "\").setEntity(" + producer + ").build()";
                return JavaTemplate.builder(template)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                        .imports(FQN_ASYNC_REQUEST_BUILDER, FQN_ASYNC_ENTITY_PRODUCERS)
                        .build()
                        .apply(getCursor(), nc.getCoordinates().replace(), entityArgs.toArray());
            }

            private String extractHttpHostCreateLiteral(Expression expr) {
                if (!(expr instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation mi = (J.MethodInvocation) expr;
                if (!"create".equals(mi.getSimpleName()) || mi.getMethodType() == null) {
                    return null;
                }
                if (!TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), FQN_HTTP_HOST)) {
                    return null;
                }
                if (mi.getArguments().size() != 1) {
                    return null;
                }
                return asStringLiteral(mi.getArguments().get(0));
            }

            private MethodInvocationInfo asSetEntityChain(Expression expr) {
                if (!(expr instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation mi = (J.MethodInvocation) expr;
                if (!"setEntity".equals(mi.getSimpleName()) || mi.getSelect() == null) {
                    return null;
                }
                if (mi.getArguments().size() != 1) {
                    return null;
                }
                return new MethodInvocationInfo(mi.getSelect(), mi.getArguments().get(0));
            }

            private String extractHttpMethodPathLiteral(Expression expr) {
                if (!(expr instanceof J.NewClass)) {
                    return null;
                }
                J.NewClass nc = (J.NewClass) expr;
                if (matchedHttpMethodFqn(nc) == null) {
                    return null;
                }
                if (nc.getArguments() == null || nc.getArguments().size() != 1) {
                    return null;
                }
                return asStringLiteral(nc.getArguments().get(0));
            }

            private String extractHttpMethodVerb(Expression expr) {
                if (!(expr instanceof J.NewClass)) {
                    return null;
                }
                String fqn = matchedHttpMethodFqn((J.NewClass) expr);
                return fqn == null ? null : VERB_BY_HTTP_METHOD.get(fqn);
            }

            private String matchedHttpMethodFqn(J.NewClass nc) {
                String typeFqn = fullyQualifiedName(nc.getType());
                if (typeFqn != null && VERB_BY_HTTP_METHOD.containsKey(typeFqn)) {
                    return typeFqn;
                }
                String simple = simpleNameOfNewClass(nc);
                if (simple != null) {
                    for (String fqn : VERB_BY_HTTP_METHOD.keySet()) {
                        if (fqn.endsWith("." + simple)) {
                            return fqn;
                        }
                    }
                }
                return null;
            }

            private J.NewClass asNEntityCtor(Expression expr) {
                if (!(expr instanceof J.NewClass)) {
                    return null;
                }
                J.NewClass nc = (J.NewClass) expr;
                if (isNewClassOfName(nc, "NStringEntity", FQN_N_STRING_ENTITY) ||
                        isNewClassOfName(nc, "NByteArrayEntity", FQN_N_BYTE_ARRAY_ENTITY) ||
                        isNewClassOfName(nc, "NFileEntity", FQN_N_FILE_ENTITY)) {
                    return nc;
                }
                return null;
            }

            private boolean isNewClassOfName(J.NewClass nc, String simpleName, String fqn) {
                String typeFqn = fullyQualifiedName(nc.getType());
                if (fqn.equals(typeFqn)) {
                    return true;
                }
                return simpleName.equals(simpleNameOfNewClass(nc));
            }

            private String simpleNameOfNewClass(J.NewClass nc) {
                if (nc.getClazz() instanceof J.Identifier) {
                    return ((J.Identifier) nc.getClazz()).getSimpleName();
                }
                if (nc.getClazz() instanceof J.FieldAccess) {
                    return ((J.FieldAccess) nc.getClazz()).getSimpleName();
                }
                return null;
            }

            private String fullyQualifiedName(org.openrewrite.java.tree.JavaType type) {
                if (type instanceof org.openrewrite.java.tree.JavaType.FullyQualified) {
                    return ((org.openrewrite.java.tree.JavaType.FullyQualified) type).getFullyQualifiedName();
                }
                return null;
            }

            private String asStringLiteral(Expression expr) {
                if (expr instanceof J.Literal) {
                    Object v = ((J.Literal) expr).getValue();
                    return v instanceof String ? (String) v : null;
                }
                return null;
            }

            private String joinUriAndPath(String uri, String path) {
                if (path.isEmpty()) {
                    return uri;
                }
                if (uri.endsWith("/") && path.startsWith("/")) {
                    return uri + path.substring(1);
                }
                if (!uri.endsWith("/") && !path.startsWith("/")) {
                    return uri + "/" + path;
                }
                return uri + path;
            }
        });
    }

    @Value
    private static class MethodInvocationInfo {
        Expression select;
        Expression argument;
    }
}

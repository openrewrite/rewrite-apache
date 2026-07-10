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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
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

    private static final MethodMatcher HTTP_HOST_CREATE = new MethodMatcher(FQN_HTTP_HOST + " create(String)");

    private static final MethodMatcher HTTP_GET_CTOR = new MethodMatcher("org.apache.http.client.methods.HttpGet <constructor>(String)");
    private static final MethodMatcher HTTP_POST_CTOR = new MethodMatcher("org.apache.http.client.methods.HttpPost <constructor>(String)");
    private static final MethodMatcher HTTP_PUT_CTOR = new MethodMatcher("org.apache.http.client.methods.HttpPut <constructor>(String)");
    private static final MethodMatcher HTTP_DELETE_CTOR = new MethodMatcher("org.apache.http.client.methods.HttpDelete <constructor>(String)");

    private static final MethodMatcher N_STRING_ENTITY_CTOR = new MethodMatcher(FQN_N_STRING_ENTITY + " <constructor>(..)");
    private static final MethodMatcher N_BYTE_ARRAY_ENTITY_CTOR = new MethodMatcher(FQN_N_BYTE_ARRAY_ENTITY + " <constructor>(..)");
    private static final MethodMatcher N_FILE_ENTITY_CTOR = new MethodMatcher(FQN_N_FILE_ENTITY + " <constructor>(..)");

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
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(FQN_PRODUCER, false),
                        Preconditions.or(
                                new UsesType<>(FQN_N_STRING_ENTITY, false),
                                new UsesType<>(FQN_N_BYTE_ARRAY_ENTITY, false),
                                new UsesType<>(FQN_N_FILE_ENTITY, false))),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                        if (!isProducerNewClass(nc)) {
                            return nc;
                        }
                        List<Expression> args = nc.getArguments();
                        if (args == null || args.size() != 2) {
                            return nc;
                        }

                        Expression uriLiteral = extractHttpHostCreateLiteral(args.get(0));
                        if (uriLiteral == null) {
                            return nc;
                        }

                        J.MethodInvocation setEntity = asSetEntityChain(args.get(1));
                        if (setEntity == null || !(setEntity.getSelect() instanceof J.NewClass)) {
                            return nc;
                        }
                        J.NewClass requestCtor = (J.NewClass) setEntity.getSelect();
                        String verb = verbFor(requestCtor);
                        Expression pathLiteral = pathLiteralFor(requestCtor);
                        if (verb == null || pathLiteral == null) {
                            return nc;
                        }

                        J.NewClass entityCtor = asNEntityCtor(setEntity.getArguments().get(0));
                        if (entityCtor == null || entityCtor.getArguments() == null || entityCtor.getArguments().isEmpty()) {
                            return nc;
                        }
                        List<Expression> entityArgs = entityCtor.getArguments();

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

                        String template = "AsyncRequestBuilder." + verb +
                                "(#{any(java.lang.String)} + #{any(java.lang.String)})" +
                                ".setEntity(" + producer + ").build()";
                        Object[] templateArgs = new Object[entityArgs.size() + 2];
                        templateArgs[0] = uriLiteral;
                        templateArgs[1] = pathLiteral;
                        for (int i = 0; i < entityArgs.size(); i++) {
                            templateArgs[i + 2] = entityArgs.get(i);
                        }
                        return JavaTemplate.builder(template)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                                .imports(FQN_ASYNC_REQUEST_BUILDER, FQN_ASYNC_ENTITY_PRODUCERS)
                                .build()
                                .apply(getCursor(), nc.getCoordinates().replace(), templateArgs);
                    }

                    private boolean isProducerNewClass(J.NewClass nc) {
                        if (TypeUtils.isOfClassType(nc.getType(), FQN_PRODUCER)) {
                            return true;
                        }
                        if (nc.getClazz() != null) {
                            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nc.getClazz().getType());
                            if (fq != null && FQN_PRODUCER.equals(fq.getFullyQualifiedName())) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private @Nullable Expression extractHttpHostCreateLiteral(Expression expr) {
                        if (!(expr instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation mi = (J.MethodInvocation) expr;
                        if (!HTTP_HOST_CREATE.matches(mi) || mi.getArguments().size() != 1) {
                            return null;
                        }
                        return isStringLiteral(mi.getArguments().get(0)) ? mi.getArguments().get(0) : null;
                    }

                    private J.@Nullable MethodInvocation asSetEntityChain(Expression expr) {
                        if (!(expr instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation mi = (J.MethodInvocation) expr;
                        if (!"setEntity".equals(mi.getSimpleName()) || mi.getSelect() == null || mi.getArguments().size() != 1) {
                            return null;
                        }
                        return mi;
                    }

                    private @Nullable String verbFor(J.NewClass nc) {
                        if (HTTP_GET_CTOR.matches(nc)) return VERB_BY_HTTP_METHOD.get("org.apache.http.client.methods.HttpGet");
                        if (HTTP_POST_CTOR.matches(nc)) return VERB_BY_HTTP_METHOD.get("org.apache.http.client.methods.HttpPost");
                        if (HTTP_PUT_CTOR.matches(nc)) return VERB_BY_HTTP_METHOD.get("org.apache.http.client.methods.HttpPut");
                        if (HTTP_DELETE_CTOR.matches(nc)) return VERB_BY_HTTP_METHOD.get("org.apache.http.client.methods.HttpDelete");
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nc.getType());
                        return fq == null ? null : VERB_BY_HTTP_METHOD.get(fq.getFullyQualifiedName());
                    }

                    private @Nullable Expression pathLiteralFor(J.NewClass nc) {
                        if (nc.getArguments() == null || nc.getArguments().size() != 1) {
                            return null;
                        }
                        Expression arg = nc.getArguments().get(0);
                        return isStringLiteral(arg) ? arg : null;
                    }

                    private J.@Nullable NewClass asNEntityCtor(Expression expr) {
                        if (!(expr instanceof J.NewClass)) {
                            return null;
                        }
                        J.NewClass nc = (J.NewClass) expr;
                        if (N_STRING_ENTITY_CTOR.matches(nc) || N_BYTE_ARRAY_ENTITY_CTOR.matches(nc) || N_FILE_ENTITY_CTOR.matches(nc)) {
                            return nc;
                        }
                        return null;
                    }

                    private boolean isStringLiteral(Expression expr) {
                        return expr instanceof J.Literal && ((J.Literal) expr).getValue() instanceof String;
                    }
                });
    }
}

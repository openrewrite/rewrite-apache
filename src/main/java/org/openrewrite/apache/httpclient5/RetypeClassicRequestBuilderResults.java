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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class RetypeClassicRequestBuilderResults extends Recipe {

    private static final String FQN_HTTP_URI_REQUEST = "org.apache.hc.client5.http.classic.methods.HttpUriRequest";
    private static final String FQN_CLASSIC_HTTP_REQUEST = "org.apache.hc.core5.http.ClassicHttpRequest";
    private static final MethodMatcher BUILD = new MethodMatcher("org.apache.hc.core5.http.io.support.ClassicRequestBuilder build()");

    String displayName = "Declare `ClassicRequestBuilder.build()` results as `ClassicHttpRequest`";

    String description = "HttpClient 4's `RequestBuilder.build()` returned an `HttpUriRequest`, while HttpCore 5's " +
            "`ClassicRequestBuilder.build()` returns a `ClassicHttpRequest`. Change the type of the local variables " +
            "that `build()` initializes to match.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(BUILD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                TypeTree typeExpression = vd.getTypeExpression();
                if (typeExpression == null || vd.getVariables().size() != 1 ||
                    !TypeUtils.isOfClassType(vd.getType(), FQN_HTTP_URI_REQUEST)) {
                    return vd;
                }
                Expression initializer = vd.getVariables().get(0).getInitializer();
                if (!(initializer instanceof J.MethodInvocation) || !BUILD.matches((J.MethodInvocation) initializer)) {
                    return vd;
                }
                maybeRemoveImport(FQN_HTTP_URI_REQUEST);
                maybeAddImport(FQN_CLASSIC_HTTP_REQUEST);
                JavaType.FullyQualified type = JavaType.ShallowClass.build(FQN_CLASSIC_HTTP_REQUEST);
                J.Identifier classicHttpRequest = TypeTree.<J.Identifier>build("ClassicHttpRequest")
                        .withType(type)
                        .withPrefix(typeExpression.getPrefix());
                return vd.withTypeExpression(classicHttpRequest).withVariables(ListUtils.map(vd.getVariables(), v -> {
                    JavaType.Variable variableType = v.getVariableType() == null ? null : v.getVariableType().withType(type);
                    return v.withName(v.getName().withType(type).withFieldType(variableType)).withVariableType(variableType);
                }));
            }
        });
    }
}

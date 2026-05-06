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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateAuthSchemeCredentials extends Recipe {

    private static final String FQN_AUTH_EXCHANGE = "org.apache.hc.client5.http.auth.AuthExchange";
    private static final String FQN_AUTH_SCHEME = "org.apache.hc.client5.http.auth.AuthScheme";
    private static final String FQN_BASIC_SCHEME = "org.apache.hc.client5.http.impl.auth.BasicScheme";
    private static final String FQN_CREDENTIALS = "org.apache.hc.client5.http.auth.Credentials";

    private static final MethodMatcher UPDATE = new MethodMatcher(
            FQN_AUTH_EXCHANGE + " update(" + FQN_AUTH_SCHEME + ", " + FQN_CREDENTIALS + ")");
    private static final MethodMatcher GET_AUTH_SCHEME_ON_SCHEME = new MethodMatcher(
            FQN_AUTH_SCHEME + " getAuthScheme()");

    String displayName = "Migrate `AuthScheme` credential handling";

    String description = "Rewrites `AuthExchange#update(BasicScheme, Credentials)` to `BasicScheme#initPreemptive(Credentials)` followed by `AuthExchange#select(AuthScheme)`. " +
            "Unwraps leftover `AuthOption#getAuthScheme()` calls (now on `AuthScheme` after the type rename) to the receiver itself. " +
            "Other `update`/`setCredentials`/`getCredentials` call sites are flagged separately by `AddCommentToMethodInvocations`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);

                List<J.MethodInvocation> basicSchemeUpdates = new ArrayList<>();
                for (Statement stmt : b.getStatements()) {
                    if (stmt instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) stmt;
                        if (UPDATE.matches(mi)
                                && TypeUtils.isOfClassType(mi.getArguments().get(0).getType(), FQN_BASIC_SCHEME)) {
                            basicSchemeUpdates.add(mi);
                        }
                    }
                }

                for (J.MethodInvocation mi : basicSchemeUpdates) {
                    Expression schemeArg = mi.getArguments().get(0);
                    Expression credsArg = mi.getArguments().get(1);
                    Expression receiver = mi.getSelect();

                    b = JavaTemplate.builder("#{any(" + FQN_BASIC_SCHEME + ")}.initPreemptive(#{any(" + FQN_CREDENTIALS + ")});")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .build()
                            .apply(new Cursor(getCursor().getParent(), b), mi.getCoordinates().before(),
                                    schemeArg, credsArg);

                    b = JavaTemplate.builder("#{any(" + FQN_AUTH_EXCHANGE + ")}.select(#{any(" + FQN_AUTH_SCHEME + ")})")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpclient5", "httpcore5"))
                            .build()
                            .apply(new Cursor(getCursor().getParent(), b), mi.getCoordinates().replace(),
                                    receiver, schemeArg);
                }
                return b;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (GET_AUTH_SCHEME_ON_SCHEME.matches(m) && m.getSelect() != null) {
                    return m.getSelect().withPrefix(m.getPrefix());
                }
                return m;
            }
        };
    }
}

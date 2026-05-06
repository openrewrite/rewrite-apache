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
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateAuthSchemeCredentials extends Recipe {

    private static final String FQN_AUTH_EXCHANGE = "org.apache.hc.client5.http.auth.AuthExchange";
    private static final String FQN_AUTH_SCHEME = "org.apache.hc.client5.http.auth.AuthScheme";
    private static final String FQN_BASIC_SCHEME = "org.apache.hc.client5.http.impl.auth.BasicScheme";
    private static final String FQN_CREDENTIALS = "org.apache.hc.client5.http.auth.Credentials";

    private static final MethodMatcher UPDATE = new MethodMatcher(FQN_AUTH_EXCHANGE + " update(..)");
    private static final MethodMatcher SET_CREDENTIALS = new MethodMatcher(FQN_AUTH_EXCHANGE + " setCredentials(..)");
    private static final MethodMatcher GET_AUTH_SCHEME_ON_SCHEME = new MethodMatcher(FQN_AUTH_SCHEME + " getAuthScheme()");

    private static final List<String> CREDENTIAL_BINDING_COMMENT = Arrays.asList(
            " HttpClient 5: AuthScheme no longer stores credentials directly. For preemptive Basic auth,",
            " cast to BasicScheme and call initPreemptive(creds). Otherwise, register the credentials with",
            " a CredentialsProvider on HttpClientBuilder and let the scheme look them up per-request.");

    String displayName = "Migrate `AuthScheme` credential handling";

    String description = "Rewrites `AuthExchange#update(BasicScheme, Credentials)` to `BasicScheme#initPreemptive(Credentials)` followed by `AuthExchange#select(AuthScheme)`. " +
            "Adds explanatory comments for non-`BasicScheme` `update`/`setCredentials` call sites. " +
            "Unwraps leftover `AuthOption#getAuthScheme()` calls (now on `AuthScheme` after the type rename) to the receiver itself.";

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
                        if (UPDATE.matches(mi) && mi.getArguments().size() == 2 && mi.getSelect() != null
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

                // option.getAuthScheme() -> option (after AuthOption -> AuthScheme type rename)
                if (GET_AUTH_SCHEME_ON_SCHEME.matches(m) && m.getSelect() != null) {
                    return m.getSelect().withPrefix(m.getPrefix());
                }

                // authExchange.update(non-BasicScheme, creds) — comment only; BasicScheme case handled in visitBlock.
                if (UPDATE.matches(m) && m.getArguments().size() == 2 && m.getSelect() != null) {
                    if (!TypeUtils.isOfClassType(m.getArguments().get(0).getType(), FQN_BASIC_SCHEME)) {
                        return addLeadingComments(m, CREDENTIAL_BINDING_COMMENT);
                    }
                    return m;
                }

                // authExchange.setCredentials(creds) — no clean rewrite; comment.
                if (SET_CREDENTIALS.matches(m)) {
                    return addLeadingComments(m, CREDENTIAL_BINDING_COMMENT);
                }

                return m;
            }
        };
    }

    private static J.MethodInvocation addLeadingComments(J.MethodInvocation m, List<String> lines) {
        Space prefix = m.getPrefix();
        for (Comment existing : prefix.getComments()) {
            if (existing instanceof TextComment && lines.get(0).equals(((TextComment) existing).getText())) {
                return m;
            }
        }
        String suffix = prefix.getWhitespace();
        List<Comment> updated = new ArrayList<>(prefix.getComments());
        for (String line : lines) {
            updated.add(new TextComment(false, line, suffix, Markers.EMPTY));
        }
        return m.withPrefix(prefix.withComments(updated));
    }
}

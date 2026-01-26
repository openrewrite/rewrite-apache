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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class UsePoolingAsyncClientConnectionManagerBuilder extends Recipe {

    private static final String FQN_MANAGER = "org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager";
    private static final String FQN_BUILDER = "org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder";

    // Methods that exist on both PoolingAsyncClientConnectionManager and PoolingAsyncClientConnectionManagerBuilder
    private static final Set<String> BUILDER_METHODS = new HashSet<String>() {{
        add("setMaxTotal");
        add("setDefaultMaxPerRoute");
        add("setConnectionTimeToLive");
        add("setValidateAfterInactivity");
    }};

    @Getter
    String displayName = "Use `PoolingAsyncClientConnectionManagerBuilder` for configuration";

    @Getter
    String description = "Moves method calls that exist on both `PoolingAsyncClientConnectionManager` and " +
                         "`PoolingAsyncClientConnectionManagerBuilder` into the builder chain.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_BUILDER, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);

                // Find variable declarations initialized with builder.build() and collect subsequent builder-compatible method calls
                Map<String, List<MethodCallInfo>> builderMethodCalls = new LinkedHashMap<>();
                Set<Statement> statementsToRemove = new HashSet<>();

                String currentVarName = null;
                for (Statement stmt : b.getStatements()) {
                    if (stmt instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                        for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                            if (var.getInitializer() != null && isBuilderBuildCall(var.getInitializer())) {
                                currentVarName = var.getSimpleName();
                                builderMethodCalls.put(currentVarName, new ArrayList<>());
                            }
                        }
                    } else if (currentVarName != null && stmt instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) stmt;
                        if (isBuilderCompatibleMethodCall(mi, currentVarName)) {
                            builderMethodCalls.get(currentVarName).add(new MethodCallInfo(mi.getName(), mi.getArguments()));
                            statementsToRemove.add(stmt);
                        } else {
                            currentVarName = null;
                        }
                    } else {
                        currentVarName = null;
                    }
                }

                if (statementsToRemove.isEmpty()) {
                    return b;
                }

                // Remove statements that will be folded into the builder
                b = b.withStatements(ListUtils.map(b.getStatements(), stmt ->
                        statementsToRemove.contains(stmt) ? null : stmt));

                // Update variable declarations to include builder method calls
                b = b.withStatements(ListUtils.map(b.getStatements(), stmt -> {
                    if (stmt instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                        return vd.withVariables(ListUtils.map(vd.getVariables(), var -> {
                            List<MethodCallInfo> calls = builderMethodCalls.get(var.getSimpleName());
                            if (calls != null && !calls.isEmpty() && var.getInitializer() != null && isBuilderBuildCall(var.getInitializer())) {
                                J.MethodInvocation buildCall = (J.MethodInvocation) var.getInitializer();
                                J.MethodInvocation builderChain = (J.MethodInvocation) buildCall.getSelect();

                                // Add method calls to the builder chain
                                for (MethodCallInfo call : calls) {
                                    builderChain = createMethodInvocation(builderChain, call.name, call.arguments);
                                }

                                // Reconstruct the .build() call with the new chain
                                return var.withInitializer(buildCall.withSelect(builderChain));
                            }
                            return var;
                        }));
                    }
                    return stmt;
                }));

                return b;
            }

            private boolean isBuilderBuildCall(Expression expr) {
                if (expr instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) expr;
                    return "build".equals(mi.getSimpleName()) &&
                           mi.getMethodType() != null &&
                           TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), FQN_BUILDER);
                }
                return false;
            }

            private boolean isBuilderCompatibleMethodCall(J.MethodInvocation mi, String varName) {
                if (mi.getSelect() instanceof J.Identifier) {
                    J.Identifier select = (J.Identifier) mi.getSelect();
                    return varName.equals(select.getSimpleName()) && BUILDER_METHODS.contains(mi.getSimpleName());
                }
                return false;
            }

            private J.MethodInvocation createMethodInvocation(J.MethodInvocation builderChain, J.Identifier name, List<Expression> arguments) {
                JavaType.FullyQualified builderType = JavaType.ShallowClass.build(FQN_BUILDER);
                JavaType.Method updatedMethodType = null;
                if (builderChain.getMethodType() != null) {
                    updatedMethodType = builderChain.getMethodType()
                            .withName(name.getSimpleName());
                }
                return new J.MethodInvocation(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        JRightPadded.build(builderChain),
                        null,
                        name.withType(updatedMethodType),
                        JContainer.build(arguments.stream()
                                .map(arg -> JRightPadded.build((Expression) arg.withPrefix(Space.EMPTY)))
                                .collect(toList())),
                        null
                ).withMethodType(updatedMethodType);
            }
        });
    }

    @Value
    private static class MethodCallInfo {
        J.Identifier name;
        List<Expression> arguments;
    }
}

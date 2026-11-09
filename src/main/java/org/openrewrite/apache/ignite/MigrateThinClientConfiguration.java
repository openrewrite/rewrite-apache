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
package org.openrewrite.apache.ignite;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateThinClientConfiguration extends Recipe {

    private static final String CLIENT_CONFIGURATION = "org.apache.ignite.configuration.ClientConfiguration";
    private static final String CONSUMED_STATEMENTS = "IGNITE_CLIENT_CONFIGURATION_STATEMENTS";

    private static final MethodMatcher START_CLIENT = new MethodMatcher(
            "org.apache.ignite.Ignition startClient(" + CLIENT_CONFIGURATION + ")");

    static final String UNSUPPORTED_TODO = "TODO Ignite 3: build the client with IgniteClient.builder()...build(). " +
            "Some of the ClientConfiguration settings used here have no Ignite 3 equivalent, " +
            "or are set where this recipe cannot follow them.";

    String displayName = "Migrate the Ignite 2 thin client configuration to `IgniteClient.builder()`";

    String description = "Ignite 3 builds a thin client with `IgniteClient.builder()` instead of starting one from a " +
            "`ClientConfiguration` with `Ignition.startClient`. The addresses, the address finder, the user name and " +
            "password, the heartbeat interval, the async continuation executor and the built-in retry policies move to " +
            "the builder; the timeout becomes both the connect and the operation timeout, as it was used for both in " +
            "Ignite 2; partition awareness is dropped, as Ignite 3 always uses it. Note that Ignite 3 retries only " +
            "reads by default, where Ignite 2 retried every operation. A configuration that sets anything else, " +
            "uses an address with a port range, or is built where this recipe cannot follow it, is left as it is " +
            "with a comment.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(START_CLIENT), new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // The builder is printed in Java syntax
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                Set<UUID> consumed = getCursor().pollMessage(CONSUMED_STATEMENTS);
                if (consumed == null) {
                    return TodoComments.addNoted(b, getCursor());
                }
                // The configuration variable and its setters are now part of the builder. Their comments move to
                // the statement after them.
                List<Statement> kept = new ArrayList<>();
                List<Space> removed = new ArrayList<>();
                boolean removedFromStart = false;
                List<Statement> statements = b.getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Statement s = statements.get(i);
                    if (consumed.contains(s.getId())) {
                        if (removed.isEmpty()) {
                            removedFromStart = i == 0;
                        }
                        removed.add(s.getPrefix());
                        continue;
                    }
                    if (!removed.isEmpty()) {
                        s = s.withPrefix(mergePrefixes(removed, s.getPrefix(), removedFromStart));
                        removed.clear();
                    }
                    kept.add(s);
                }
                return TodoComments.addNoted(b.withStatements(kept), getCursor());
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation) || !START_CLIENT.matches((J.MethodInvocation) j)) {
                    return j;
                }
                J.MethodInvocation mi = (J.MethodInvocation) j;
                Expression cfg = mi.getArguments().get(0);
                List<J.MethodInvocation> setters = new ArrayList<>();
                Expression builder = null;
                if (cfg instanceof J.Identifier) {
                    Cursor declaringBlock = localSetters((J.Identifier) cfg, setters);
                    if (declaringBlock != null) {
                        builder = builder(setters, mi, ctx);
                        if (builder != null) {
                            Set<UUID> consumed = declaringBlock.computeMessageIfAbsent(CONSUMED_STATEMENTS, k -> new HashSet<>());
                            consumed.addAll(lastConsumed);
                        }
                    }
                } else if (collectSetters(cfg, setters, false)) {
                    builder = builder(setters, mi, ctx);
                }
                if (builder == null) {
                    TodoComments.noteOnEnclosingStatement(getCursor(), UNSUPPORTED_TODO);
                    return mi;
                }
                removeIgnite2Imports();
                return builder.withPrefix(mi.getPrefix());
            }

            private final Set<UUID> lastConsumed = new HashSet<>();

            /**
             * Find the block declaring this local {@code ClientConfiguration}, and collect the setters it is given
             * there, in its initializer and in statements of their own before the one starting the client. The variable
             * must not be used in any other way. Returns the cursor of that block, or {@code null}.
             */
            private @Nullable Cursor localSetters(J.Identifier cfg, List<J.MethodInvocation> setters) {
                lastConsumed.clear();
                Object child = getCursor().getValue();
                boolean inLoop = false;
                for (Cursor c = getCursor().getParentOrThrow(); c.getParent() != null; c = c.getParent()) {
                    Object tree = c.getValue();
                    if (tree instanceof J.MethodDeclaration || tree instanceof J.ClassDeclaration || tree instanceof J.Lambda) {
                        return null;
                    }
                    // The builder would run each time round the loop, where the configuration was built once
                    inLoop |= tree instanceof Loop;
                    if (tree instanceof J.Block) {
                        J.Block block = (J.Block) tree;
                        int declaration = -1;
                        List<Statement> statements = block.getStatements();
                        for (int i = 0; i < statements.size(); i++) {
                            J.VariableDeclarations.NamedVariable variable = localClientConfiguration(statements.get(i));
                            if (variable != null && variable.getSimpleName().equals(cfg.getSimpleName())) {
                                declaration = i;
                                break;
                            }
                        }
                        if (declaration >= 0) {
                            J.VariableDeclarations.NamedVariable variable = localClientConfiguration(statements.get(declaration));
                            if (inLoop || variable == null || !collectSetters(variable.getInitializer(), setters, false)) {
                                return null;
                            }
                            lastConsumed.add(statements.get(declaration).getId());
                            List<Statement> between = new ArrayList<>();
                            boolean beforeStart = true;
                            for (int i = declaration + 1; i < statements.size(); i++) {
                                Statement s = statements.get(i);
                                if (s == child) {
                                    beforeStart = false;
                                    if (countReferences(s, cfg.getSimpleName()) != 1) {
                                        return null;
                                    }
                                } else if (beforeStart && isSetterStatementOn(s, cfg.getSimpleName())) {
                                    if (!collectSetters((Expression) s, setters, true)) {
                                        return null;
                                    }
                                    lastConsumed.add(s.getId());
                                } else if (countReferences(s, cfg.getSimpleName()) > 0) {
                                    return null;
                                } else if (beforeStart) {
                                    between.add(s);
                                }
                            }
                            return beforeStart || !canMoveAfter(setters, between) ? null : c;
                        }
                    }
                    if (tree instanceof J) {
                        child = tree;
                    }
                }
                return null;
            }

            private void removeIgnite2Imports() {
                maybeRemoveImport("org.apache.ignite.Ignition");
                maybeRemoveImport(CLIENT_CONFIGURATION);
                maybeRemoveImport("org.apache.ignite.client.ClientRetryAllPolicy");
                maybeRemoveImport("org.apache.ignite.client.ClientRetryReadPolicy");
                maybeRemoveImport("org.apache.ignite.client.ClientRetryNonePolicy");
            }

            private @Nullable Expression builder(List<J.MethodInvocation> setters, J.MethodInvocation startClient, ExecutionContext ctx) {
                StringBuilder code = new StringBuilder("IgniteClient.builder()");
                List<Object> parameters = new ArrayList<>();
                Set<String> imports = new LinkedHashSet<>();
                imports.add("org.apache.ignite.client.IgniteClient");

                Expression userName = null;
                Expression password = null;
                for (J.MethodInvocation setter : setters) {
                    if ("setUserName".equals(setter.getSimpleName())) {
                        userName = setter.getArguments().get(0);
                    } else if ("setUserPassword".equals(setter.getSimpleName())) {
                        password = setter.getArguments().get(0);
                    }
                }
                if ((userName == null) != (password == null)) {
                    return null;
                }

                int calls = 0;
                for (J.MethodInvocation setter : setters) {
                    List<Expression> args = setter.getArguments();
                    switch (setter.getSimpleName()) {
                        case "setAddresses":
                            StringJoiner addresses = new StringJoiner(", ", "\n.addresses(", ")");
                            for (Expression address : args) {
                                if (address instanceof J.Literal && String.valueOf(((J.Literal) address).getValue()).contains("..")) {
                                    // Ignite 3 takes no port ranges
                                    return null;
                                }
                                addresses.add(address.getType() instanceof JavaType.Array ?
                                        "#{anyArray(java.lang.String)}" : "#{any(java.lang.String)}");
                                parameters.add(address);
                            }
                            code.append(addresses);
                            calls++;
                            break;
                        case "setTimeout":
                            Expression timeout = args.get(0);
                            if (!(timeout instanceof J.Literal || timeout instanceof J.Identifier || timeout instanceof J.FieldAccess)) {
                                return null;
                            }
                            code.append("\n.connectTimeout(#{any(long)})\n.operationTimeout(#{any(long)})");
                            parameters.add(timeout);
                            parameters.add(new RandomizeIdVisitor<Integer>().visitNonNull(timeout, 0));
                            calls += 2;
                            break;
                        case "setUserName":
                            code.append("\n.authenticator(BasicAuthenticator.builder().username(#{any(java.lang.String)}).password(#{any(java.lang.String)}).build())");
                            parameters.add(userName);
                            parameters.add(password);
                            imports.add("org.apache.ignite.client.BasicAuthenticator");
                            calls++;
                            break;
                        case "setUserPassword":
                        case "setPartitionAwarenessEnabled":
                            // The password goes with the user name; Ignite 3 is always partition aware
                            break;
                        case "setHeartbeatInterval":
                            code.append("\n.heartbeatInterval(#{any(long)})");
                            parameters.add(args.get(0));
                            calls++;
                            break;
                        case "setAsyncContinuationExecutor":
                            code.append("\n.asyncContinuationExecutor(#{any(java.util.concurrent.Executor)})");
                            parameters.add(args.get(0));
                            calls++;
                            break;
                        case "setAddressesFinder":
                            code.append("\n.addressFinder(#{any(org.apache.ignite.client.IgniteClientAddressFinder)})");
                            parameters.add(args.get(0));
                            calls++;
                            break;
                        case "setRetryPolicy":
                            Expression policy = args.get(0);
                            if (!(policy instanceof J.NewClass) || ((J.NewClass) policy).getArguments().stream().anyMatch(a -> !(a instanceof J.Empty))) {
                                return null;
                            }
                            if (TypeUtils.isOfClassType(policy.getType(), "org.apache.ignite.client.ClientRetryNonePolicy")) {
                                code.append("\n.retryPolicy(null)");
                            } else if (TypeUtils.isOfClassType(policy.getType(), "org.apache.ignite.client.ClientRetryAllPolicy")) {
                                code.append("\n.retryPolicy(new RetryLimitPolicy())");
                                imports.add("org.apache.ignite.client.RetryLimitPolicy");
                            } else if (TypeUtils.isOfClassType(policy.getType(), "org.apache.ignite.client.ClientRetryReadPolicy")) {
                                code.append("\n.retryPolicy(new RetryReadPolicy())");
                                imports.add("org.apache.ignite.client.RetryReadPolicy");
                            } else {
                                return null;
                            }
                            calls++;
                            break;
                        default:
                            return null;
                    }
                }
                code.append("\n.build()");

                String template = calls <= 1 ? code.toString().replace("\n", "") : code.toString();
                for (String i : imports) {
                    maybeAddImport(i);
                }
                return JavaTemplate.builder(template)
                        .imports(imports.toArray(new String[0]))
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "ignite-api-3", "ignite-client-3"))
                        .build()
                        .apply(new Cursor(getCursor().getParentOrThrow(), startClient), startClient.getCoordinates().replace(), parameters.toArray());
            }
        });
    }

    private static J.VariableDeclarations.@Nullable NamedVariable localClientConfiguration(Statement statement) {
        if (statement instanceof J.VariableDeclarations) {
            J.VariableDeclarations vd = (J.VariableDeclarations) statement;
            if (vd.getVariables().size() == 1 && TypeUtils.isOfClassType(vd.getType(), CLIENT_CONFIGURATION) &&
                vd.getVariables().get(0).getInitializer() != null) {
                return vd.getVariables().get(0);
            }
        }
        return null;
    }

    private static boolean isSetterStatementOn(Statement s, String cfgName) {
        if (!(s instanceof J.MethodInvocation)) {
            return false;
        }
        Expression e = (Expression) s;
        while (e instanceof J.MethodInvocation) {
            e = ((J.MethodInvocation) e).getSelect();
        }
        return e instanceof J.Identifier && ((J.Identifier) e).getSimpleName().equals(cfgName);
    }

    /**
     * Collect, in the order they are called, the setters of a chain that starts with {@code new ClientConfiguration()},
     * or with the variable holding it when {@code onVariable}. Returns {@code false} for anything else.
     */
    private static boolean collectSetters(@Nullable Expression e, List<J.MethodInvocation> setters, boolean onVariable) {
        List<J.MethodInvocation> chain = new ArrayList<>();
        while (e instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) e;
            if (mi.getMethodType() == null || !TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), CLIENT_CONFIGURATION) ||
                !mi.getSimpleName().startsWith("set")) {
                return false;
            }
            chain.add(0, mi);
            e = mi.getSelect();
        }
        boolean startsWithConfiguration = e instanceof J.NewClass &&
                                          TypeUtils.isOfClassType(e.getType(), CLIENT_CONFIGURATION) &&
                                          ((J.NewClass) e).getBody() == null &&
                                          ((J.NewClass) e).getArguments().stream().allMatch(a -> a instanceof J.Empty);
        if (!(onVariable ? e instanceof J.Identifier : startsWithConfiguration)) {
            return false;
        }
        setters.addAll(chain);
        return true;
    }

    /**
     * Whether the arguments of these setters evaluate to the same values, with the same side effects, once they move
     * past these other statements into the builder: they call nothing, and use no variable these statements assign.
     */
    private static boolean canMoveAfter(List<J.MethodInvocation> setters, List<Statement> between) {
        if (between.isEmpty()) {
            return true;
        }
        Set<String> assigned = new HashSet<>();
        for (Statement statement : between) {
            new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, Set<String> a) {
                    add(assignment.getVariable(), a);
                    return super.visitAssignment(assignment, a);
                }

                @Override
                public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, Set<String> a) {
                    add(assignOp.getVariable(), a);
                    return super.visitAssignmentOperation(assignOp, a);
                }

                @Override
                public J.Unary visitUnary(J.Unary unary, Set<String> a) {
                    if (unary.getOperator().isModifying()) {
                        add(unary.getExpression(), a);
                    }
                    return super.visitUnary(unary, a);
                }

                private void add(Expression variable, Set<String> a) {
                    if (variable instanceof J.Identifier) {
                        a.add(((J.Identifier) variable).getSimpleName());
                    } else if (variable instanceof J.FieldAccess) {
                        a.add(((J.FieldAccess) variable).getSimpleName());
                    }
                }
            }.visit(statement, assigned);
        }
        for (J.MethodInvocation setter : setters) {
            if ("setRetryPolicy".equals(setter.getSimpleName())) {
                // Replaced by a policy of Ignite 3 rather than moved
                continue;
            }
            for (Expression argument : setter.getArguments()) {
                AtomicBoolean movable = new AtomicBoolean(true);
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean m) {
                        m.set(false);
                        return method;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean m) {
                        m.set(false);
                        return newClass;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean m) {
                        if (assigned.contains(identifier.getSimpleName())) {
                            m.set(false);
                        }
                        return identifier;
                    }
                }.visit(argument, movable);
                if (!movable.get()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The prefix of the statement after removed ones, holding their comments before its own. When they were the
     * first statements of the block, it takes their whitespace, so that the block does not start with a blank line.
     */
    private static Space mergePrefixes(List<Space> removed, Space kept, boolean removedFromStart) {
        String whitespace = removedFromStart ? removed.get(0).getWhitespace() : null;
        List<Comment> comments = new ArrayList<>();
        for (Space prefix : removed) {
            if (prefix.getComments().isEmpty()) {
                continue;
            }
            if (whitespace == null) {
                whitespace = prefix.getWhitespace();
            } else if (!comments.isEmpty()) {
                comments.set(comments.size() - 1, comments.get(comments.size() - 1).withSuffix(prefix.getWhitespace()));
            }
            comments.addAll(prefix.getComments());
        }
        if (comments.isEmpty()) {
            return whitespace == null ? kept : kept.withWhitespace(whitespace);
        }
        comments.set(comments.size() - 1, comments.get(comments.size() - 1).withSuffix(kept.getWhitespace()));
        comments.addAll(kept.getComments());
        return Space.build(whitespace, comments);
    }

    private static int countReferences(J j, String name) {
        AtomicInteger count = new AtomicInteger();
        new JavaIsoVisitor<AtomicInteger>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger c) {
                if (identifier.getSimpleName().equals(name) && identifier.getFieldType() != null) {
                    c.incrementAndGet();
                }
                return super.visitIdentifier(identifier, c);
            }
        }.visit(j, count);
        return count.get();
    }
}

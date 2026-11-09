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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateTransactions extends Recipe {

    private static final String TRANSACTION = "org.apache.ignite.transactions.Transaction";
    private static final String CLIENT_TRANSACTION = "org.apache.ignite.client.ClientTransaction";
    private static final String IGNITE_TRANSACTIONS = "org.apache.ignite.IgniteTransactions";
    private static final String CLIENT_TRANSACTIONS = "org.apache.ignite.client.ClientTransactions";

    private static final String IGNITE3_TRANSACTION = "org.apache.ignite.tx.Transaction";
    private static final String IGNITE3_TRANSACTIONS = "org.apache.ignite.tx.IgniteTransactions";

    private static final MethodMatcher TX_START = new MethodMatcher(IGNITE_TRANSACTIONS + " txStart(..)");
    private static final MethodMatcher CLIENT_TX_START = new MethodMatcher(CLIENT_TRANSACTIONS + " txStart(..)");
    private static final MethodMatcher TX_CLOSE = new MethodMatcher(TRANSACTION + " close()");
    private static final MethodMatcher CLIENT_TX_CLOSE = new MethodMatcher(CLIENT_TRANSACTION + " close()");

    static final String THREAD_BOUND_TODO = "TODO Ignite 3: transactions are no longer bound to the thread. " +
            "Pass tx as the first argument of each table and SQL operation that should run in it, " +
            "including those in methods called from here.";

    static final String NOT_AUTOCLOSEABLE_TODO = "TODO Ignite 3: Transaction is not AutoCloseable. " +
            "Begin it before the try block and roll it back in its finally block.";

    String displayName = "Migrate Ignite 2 transactions to Ignite 3";

    String description = "Ignite 3 transactions are begun with `IgniteTransactions.begin()`, are not `AutoCloseable`, " +
            "and are not bound to the thread. A try-with-resources over `txStart()` becomes a transaction begun before " +
            "a try block that rolls it back in its `finally` block, which does nothing once the transaction has committed, " +
            "as `close()` did in Ignite 2. Ignite 3 has no concurrency or isolation modes, so those arguments are dropped; " +
            "a timeout becomes `TransactionOptions.timeoutMillis`. Where the transaction is in scope of a call that may " +
            "have run in it implicitly, such as a method of the application, a comment says that the operations meant " +
            "to run in it now need it as an argument.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(IGNITE_TRANSACTIONS, true),
                        new UsesType<>(CLIENT_TRANSACTIONS, true),
                        new UsesType<>(TRANSACTION, true),
                        new UsesType<>(CLIENT_TRANSACTION, true)),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                        // The rewrites print Java syntax, such as class literals and try/finally
                        return sourceFile instanceof J.CompilationUnit;
                    }

                    @Override
                    public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        doAfterVisit(new ChangeType(TRANSACTION, IGNITE3_TRANSACTION, true).getVisitor());
                        doAfterVisit(new ChangeType(CLIENT_TRANSACTION, IGNITE3_TRANSACTION, true).getVisitor());
                        doAfterVisit(new ChangeType(IGNITE_TRANSACTIONS, IGNITE3_TRANSACTIONS, true).getVisitor());
                        doAfterVisit(new ChangeType(CLIENT_TRANSACTIONS, IGNITE3_TRANSACTIONS, true).getVisitor());
                        doAfterVisit(new ChangeType("org.apache.ignite.transactions.TransactionException",
                                "org.apache.ignite.tx.TransactionException", true).getVisitor());
                        return super.visitCompilationUnit(cu, ctx);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (TX_START.matches(mi) || CLIENT_TX_START.matches(mi)) {
                            maybeRemoveImport("org.apache.ignite.transactions.TransactionConcurrency");
                            maybeRemoveImport("org.apache.ignite.transactions.TransactionIsolation");
                            maybeRemoveImport("org.apache.ignite.transactions.TransactionConcurrency.*");
                            maybeRemoveImport("org.apache.ignite.transactions.TransactionIsolation.*");
                            List<Expression> args = mi.getArguments();
                            if (args.size() >= 3) {
                                maybeAddImport("org.apache.ignite.tx.TransactionOptions");
                                return JavaTemplate.builder("#{any(org.apache.ignite.tx.IgniteTransactions)}" +
                                                            ".begin(new TransactionOptions().timeoutMillis(#{any(long)}))")
                                        .imports("org.apache.ignite.tx.TransactionOptions")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "ignite-api-3"))
                                        .build()
                                        .apply(updateCursor(mi), mi.getCoordinates().replace(), mi.getSelect(), args.get(2));
                            }
                            return withNameAndNoArguments(mi, "begin");
                        }
                        if (TX_CLOSE.matches(mi) || CLIENT_TX_CLOSE.matches(mi)) {
                            // Rolling back a committed Ignite 3 transaction does nothing, as closing one did in Ignite 2
                            return withNameAndNoArguments(mi, "rollback");
                        }
                        return mi;
                    }

                    @Override
                    public J visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = (J.Block) super.visitBlock(block, ctx);
                        List<Statement> statements = b.getStatements();
                        List<Statement> migrated = new ArrayList<>(statements.size() + 1);
                        for (int i = 0; i < statements.size(); i++) {
                            Statement statement = statements.get(i);
                            J.VariableDeclarations txDeclaration = transactionResource(statement);
                            String started = startedTransaction(statement);
                            if (txDeclaration != null) {
                                migrated.addAll(withoutResource(b, (J.Try) statement, txDeclaration));
                            } else if (started != null && mayNeedTransaction(statements.subList(i + 1, statements.size()), started)) {
                                migrated.add(TodoComments.withTodos(statement, singletonList(THREAD_BOUND_TODO)));
                            } else {
                                migrated.add(statement);
                            }
                        }
                        return b.withStatements(migrated);
                    }

                    private List<Statement> withoutResource(J.Block block, J.Try tryWithResources, J.VariableDeclarations txDeclaration) {
                        J.Identifier tx = txDeclaration.getVariables().get(0).getName();
                        boolean keepsHandlers = !tryWithResources.getCatches().isEmpty() || tryWithResources.getFinally() != null;
                        boolean clash = declaresElsewhere(block, tryWithResources, tx.getSimpleName());
                        boolean needsTransaction = mayNeedTransaction(tryWithResources.getBody().getStatements(), tx.getSimpleName());
                        String indent = tryWithResources.getPrefix().getIndent();
                        String inner = innerIndent(tryWithResources, indent);

                        if (!keepsHandlers && !clash) {
                            // Transaction tx = ...begin();
                            // try { ... } finally { tx.rollback(); }
                            J.Try tryFinally = tryWithResources.getPadding().withResources(null)
                                    .withPrefix(Space.format("\n" + indent));
                            tryFinally = withRollbackInFinally(tryFinally, tx, indent, inner);
                            return Arrays.asList(
                                    threadBoundComment(txDeclaration.withPrefix(tryWithResources.getPrefix()), needsTransaction),
                                    tryFinally);
                        }

                        // { Transaction tx = ...begin(); try { ... } finally { tx.rollback(); } }
                        J.Block shiftedBody = ShiftFormat.indent(tryWithResources.getBody(), getCursor(), 1);
                        J.Try innerTry = new J.Try(randomId(), Space.format("\n" + inner), Markers.EMPTY, null,
                                shiftedBody, emptyList(), null);
                        innerTry = withRollbackInFinally(innerTry, tx, inner,
                                shiftedBody.getStatements().isEmpty() ? inner + inner.substring(indent.length()) :
                                        shiftedBody.getStatements().get(0).getPrefix().getIndent());
                        J.Block wrapped = new J.Block(randomId(), tryWithResources.getBody().getPrefix(), Markers.EMPTY,
                                JRightPadded.build(false),
                                Arrays.asList(
                                        JRightPadded.build(threadBoundComment(
                                                txDeclaration.withPrefix(Space.format("\n" + inner)), needsTransaction)),
                                        JRightPadded.build(innerTry)),
                                Space.format("\n" + indent));
                        if (keepsHandlers) {
                            // try { ... } catch (...) { ... }
                            return singletonList(tryWithResources.getPadding().withResources(null).withBody(wrapped));
                        }
                        // A block of its own keeps the transaction apart from another variable of the same name
                        return singletonList(wrapped.withPrefix(tryWithResources.getPrefix()));
                    }

                    @Override
                    public J visitTry(J.Try tryable, ExecutionContext ctx) {
                        J j = super.visitTry(tryable, ctx);
                        if (!(j instanceof J.Try)) {
                            return j;
                        }
                        J.Try t = (J.Try) j;
                        String tx = transactionAmongResources(t);
                        if (tx == null ||
                            transactionResource(t) != null && getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                            // visitBlock moves a transaction that is the only resource of a statement of a block
                            return j;
                        }
                        // Among other resources, an existing variable, or not a statement of a block, as in a case of a switch
                        List<String> todos = new ArrayList<>();
                        todos.add(NOT_AUTOCLOSEABLE_TODO);
                        if (mayNeedTransaction(t.getBody().getStatements(), tx)) {
                            todos.add(THREAD_BOUND_TODO);
                        }
                        return TodoComments.withTodos(t, todos);
                    }

                    private J.VariableDeclarations threadBoundComment(J.VariableDeclarations txDeclaration, boolean needsTransaction) {
                        return needsTransaction ?
                                TodoComments.withTodos(txDeclaration, singletonList(THREAD_BOUND_TODO)) : txDeclaration;
                    }
                }
        );
    }

    private static J.MethodInvocation withNameAndNoArguments(J.MethodInvocation mi, String name) {
        JavaType.Method methodType = mi.getMethodType();
        if (methodType != null) {
            methodType = methodType.withName(name).withParameterTypes(emptyList()).withParameterNames(emptyList());
        }
        return mi.withName(mi.getName().withSimpleName(name).withType(methodType))
                .withArguments(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)))
                .withMethodType(methodType);
    }

    /**
     * The Ignite 2 transaction a try-with-resources holds as its only resource.
     */
    private static J.@Nullable VariableDeclarations transactionResource(Statement statement) {
        if (!(statement instanceof J.Try)) {
            return null;
        }
        List<J.Try.Resource> resources = ((J.Try) statement).getResources();
        if (resources == null || resources.size() != 1 ||
            !(resources.get(0).getVariableDeclarations() instanceof J.VariableDeclarations)) {
            return null;
        }
        J.VariableDeclarations declaration = (J.VariableDeclarations) resources.get(0).getVariableDeclarations();
        if (declaration.getVariables().size() != 1 || declaration.getVariables().get(0).getInitializer() == null ||
            !(TypeUtils.isOfClassType(declaration.getType(), TRANSACTION) ||
              TypeUtils.isOfClassType(declaration.getType(), CLIENT_TRANSACTION))) {
            return null;
        }
        return declaration;
    }

    private static boolean isIgnite2Transaction(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, TRANSACTION) || TypeUtils.isOfClassType(type, CLIENT_TRANSACTION);
    }

    private static boolean isNullLiteral(@Nullable Expression e) {
        return e instanceof J.Literal && ((J.Literal) e).getValue() == null;
    }

    /**
     * The name of the Ignite 2 transaction variable that this statement declares or assigns a transaction to.
     */
    private static @Nullable String startedTransaction(Statement statement) {
        if (statement instanceof J.VariableDeclarations) {
            J.VariableDeclarations vd = (J.VariableDeclarations) statement;
            if (vd.getVariables().size() == 1 && isIgnite2Transaction(vd.getType()) &&
                vd.getVariables().get(0).getInitializer() != null && !isNullLiteral(vd.getVariables().get(0).getInitializer())) {
                return vd.getVariables().get(0).getSimpleName();
            }
        } else if (statement instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) statement;
            if (assignment.getVariable() instanceof J.Identifier && isIgnite2Transaction(assignment.getVariable().getType()) &&
                !isNullLiteral(assignment.getAssignment())) {
                return ((J.Identifier) assignment.getVariable()).getSimpleName();
            }
        }
        return null;
    }

    /**
     * The name of an Ignite 2 transaction among the resources of this try, declared there or not.
     */
    private static @Nullable String transactionAmongResources(J.Try t) {
        if (t.getResources() != null) {
            for (J.Try.Resource resource : t.getResources()) {
                TypedTree r = resource.getVariableDeclarations();
                if (r instanceof J.VariableDeclarations && isIgnite2Transaction(((J.VariableDeclarations) r).getType())) {
                    return ((J.VariableDeclarations) r).getVariables().get(0).getSimpleName();
                }
                if (r instanceof J.Identifier && isIgnite2Transaction(r.getType())) {
                    return ((J.Identifier) r).getSimpleName();
                }
            }
        }
        return null;
    }

    /**
     * Whether another statement of the block declares a variable of this name, which the transaction would clash
     * with once it is declared in the block itself: a statement before it, or anything in a statement after it.
     */
    private static boolean declaresElsewhere(J.Block block, Statement tryWithResources, String name) {
        boolean after = false;
        for (Statement statement : block.getStatements()) {
            if (statement == tryWithResources) {
                after = true;
            } else if (after ? declaresAnywhere(statement, name) : declaresDirectly(statement, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresDirectly(Statement statement, String name) {
        if (statement instanceof J.VariableDeclarations) {
            return declares((J.VariableDeclarations) statement, name);
        }
        if (statement instanceof J.Try && ((J.Try) statement).getResources() != null) {
            for (J.Try.Resource resource : ((J.Try) statement).getResources()) {
                if (resource.getVariableDeclarations() instanceof J.VariableDeclarations &&
                    declares((J.VariableDeclarations) resource.getVariableDeclarations(), name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean declares(J.VariableDeclarations declaration, String name) {
        for (J.VariableDeclarations.NamedVariable variable : declaration.getVariables()) {
            if (variable.getSimpleName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this tree declares a variable of this name anywhere: a local variable, a resource, a loop variable,
     * or a parameter of a catch or a lambda.
     */
    private static boolean declaresAnywhere(J tree, String name) {
        AtomicBoolean declared = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, AtomicBoolean d) {
                if (variable.getSimpleName().equals(name)) {
                    d.set(true);
                }
                return super.visitVariable(variable, d);
            }
        }.visit(tree, declared);
        return declared.get();
    }

    private static final Set<String> SAME_THREAD_PACKAGES = new HashSet<>(Arrays.asList(
            "java.lang", "java.util", "java.util.stream", "java.util.regex", "java.util.concurrent",
            "java.util.concurrent.atomic", "java.util.logging", "java.math", "java.time", "java.time.format",
            "java.text", "java.io", "java.nio.charset", "org.slf4j", "org.apache.logging.log4j",
            "org.apache.commons.logging"));

    /**
     * Whether any of these statements calls something that may have run in the Ignite 2 transaction of this thread
     * without being given it: an Ignite 2 cache operation that was not migrated, a method of the application or of
     * another library, or a callback. Operations passed the transaction, Ignite APIs, and the JDK and logging APIs
     * that run no code of the application do not count.
     */
    private static boolean mayNeedTransaction(List<? extends J> trees, String tx) {
        AtomicBoolean needed = new AtomicBoolean();
        JavaIsoVisitor<AtomicBoolean> visitor = new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean n) {
                if (!passes(method.getArguments(), tx) && mayRunApplicationCode(method)) {
                    n.set(true);
                }
                return super.visitMethodInvocation(method, n);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicBoolean n) {
                JavaType.Method type = memberRef.getMethodType();
                if (type == null || !isSameThreadApi(type.getDeclaringType())) {
                    n.set(true);
                }
                return super.visitMemberReference(memberRef, n);
            }
        };
        for (J tree : trees) {
            visitor.visit(tree, needed);
        }
        return needed.get();
    }

    private static boolean passes(List<Expression> arguments, String tx) {
        for (Expression argument : arguments) {
            if (argument instanceof J.Identifier && ((J.Identifier) argument).getSimpleName().equals(tx)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mayRunApplicationCode(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null) {
            return true;
        }
        JavaType.FullyQualified declaringType = type.getDeclaringType();
        if (TypeUtils.isAssignableTo("org.apache.ignite.IgniteCache", declaringType) ||
            TypeUtils.isAssignableTo("org.apache.ignite.client.ClientCache", declaringType) ||
            "query".equals(method.getSimpleName()) && TypeUtils.isOfClassType(declaringType, "org.apache.ignite.client.IgniteClient")) {
            // Cache operations that were not given the transaction
            return true;
        }
        if (!isSameThreadApi(declaringType)) {
            return true;
        }
        for (Expression argument : method.getArguments()) {
            if (!(argument instanceof J.Lambda) && !(argument instanceof J.MemberReference) && isCallback(argument.getType())) {
                // A callback held in a variable runs code that the visitor cannot see
                return true;
            }
        }
        return false;
    }

    private static boolean isSameThreadApi(JavaType.FullyQualified type) {
        return !isCallback(type) && (type.getFullyQualifiedName().startsWith("org.apache.ignite.") ||
                                     SAME_THREAD_PACKAGES.contains(type.getPackageName()));
    }

    private static boolean isCallback(@Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && ("java.util.function".equals(fq.getPackageName()) ||
                              TypeUtils.isOfClassType(fq, "java.lang.Runnable") ||
                              TypeUtils.isOfClassType(fq, "java.util.concurrent.Callable"));
    }

    private static String innerIndent(J.Try tryWithResources, String indent) {
        List<Statement> statements = tryWithResources.getBody().getStatements();
        if (!statements.isEmpty()) {
            String first = statements.get(0).getPrefix().getIndent();
            if (first.length() > indent.length()) {
                return first;
            }
        }
        return indent + "    ";
    }

    private static J.Try withRollbackInFinally(J.Try t, J.Identifier tx, String indent, String statementIndent) {
        JavaType.Method rollbackType = rollbackMethod(tx.getType());
        J.MethodInvocation rollback = new J.MethodInvocation(randomId(), Space.format("\n" + statementIndent), Markers.EMPTY,
                JRightPadded.build(tx.withId(randomId()).withPrefix(Space.EMPTY)), null,
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "rollback", rollbackType, null),
                JContainer.build(Space.EMPTY, singletonList(JRightPadded.build(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))), Markers.EMPTY),
                rollbackType);
        J.Block finallyBlock = new J.Block(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, JRightPadded.build(false),
                singletonList(JRightPadded.build(rollback)), Space.format("\n" + indent));
        return t.getPadding().withFinally(new JLeftPadded<>(Space.SINGLE_SPACE, finallyBlock, Markers.EMPTY));
    }

    private static JavaType.@Nullable Method rollbackMethod(@Nullable JavaType txType) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(txType);
        if (fq != null) {
            for (JavaType.Method method : fq.getMethods()) {
                if ("rollback".equals(method.getName()) && method.getParameterTypes().isEmpty()) {
                    return method;
                }
            }
        }
        return null;
    }
}

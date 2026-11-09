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
            "a timeout becomes `TransactionOptions.timeoutMillis`. Where the transaction is not passed to any operation " +
            "inside the block, a comment says that the operations meant to run in it now need it as an argument.";

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
                        return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                            J.VariableDeclarations txDeclaration = transactionResource(statement);
                            if (txDeclaration == null) {
                                return statement;
                            }
                            J.Try tryWithResources = (J.Try) statement;
                            J.Identifier tx = txDeclaration.getVariables().get(0).getName();
                            boolean clash = declaresElsewhere(b, tryWithResources, tx.getSimpleName());
                            boolean keepsHandlers = !tryWithResources.getCatches().isEmpty() || tryWithResources.getFinally() != null;
                            if (clash && !keepsHandlers) {
                                // Declared in the block, the transaction would clash with the other variable
                                return TodoComments.withTodos(statement, singletonList(NOT_AUTOCLOSEABLE_TODO));
                            }

                            boolean passesTransaction = passesTransaction(tryWithResources.getBody(), tx);
                            String indent = statement.getPrefix().getIndent();
                            if (!keepsHandlers) {
                                // Transaction tx = ...begin();
                                // try { ... } finally { tx.rollback(); }
                                J.Try tryFinally = tryWithResources.getPadding().withResources(null)
                                        .withPrefix(Space.format("\n" + indent));
                                tryFinally = withRollbackInFinally(tryFinally, tx, indent, innerIndent(tryWithResources, indent));
                                return Arrays.asList(
                                        threadBoundComment(txDeclaration.withPrefix(statement.getPrefix()), passesTransaction),
                                        tryFinally);
                            }

                            // try { Transaction tx = ...begin(); try { ... } finally { tx.rollback(); } } catch (...) { ... }
                            String inner = innerIndent(tryWithResources, indent);
                            J.Block shiftedBody = ShiftFormat.indent(tryWithResources.getBody(), getCursor(), 1);
                            J.Try innerTry = new J.Try(randomId(), Space.format("\n" + inner), Markers.EMPTY, null,
                                    shiftedBody, emptyList(), null);
                            innerTry = withRollbackInFinally(innerTry, tx, inner,
                                    shiftedBody.getStatements().isEmpty() ? inner + inner.substring(indent.length()) :
                                            shiftedBody.getStatements().get(0).getPrefix().getIndent());
                            J.Block outerBody = new J.Block(randomId(), tryWithResources.getBody().getPrefix(), Markers.EMPTY,
                                    JRightPadded.build(false),
                                    Arrays.asList(
                                            JRightPadded.build(threadBoundComment(
                                                    txDeclaration.withPrefix(Space.format("\n" + inner)), passesTransaction)),
                                            JRightPadded.build(innerTry)),
                                    Space.format("\n" + indent));
                            return tryWithResources.getPadding().withResources(null).withBody(outerBody);
                        }));
                    }

                    @Override
                    public J visitTry(J.Try tryable, ExecutionContext ctx) {
                        J j = super.visitTry(tryable, ctx);
                        if (j instanceof J.Try && transactionResource((J.Try) j) != null &&
                            !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
                            // Not a statement of a block, as in a case of a switch, which visitBlock cannot rewrite
                            return TodoComments.withTodos(j, singletonList(NOT_AUTOCLOSEABLE_TODO));
                        }
                        return j;
                    }

                    private J.VariableDeclarations threadBoundComment(J.VariableDeclarations txDeclaration, boolean passesTransaction) {
                        return passesTransaction ? txDeclaration :
                                TodoComments.withTodos(txDeclaration, singletonList(THREAD_BOUND_TODO));
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

    /**
     * Whether another statement of the block declares a variable of this name, which the transaction
     * would clash with once it is declared in the block itself.
     */
    private static boolean declaresElsewhere(J.Block block, Statement exclude, String name) {
        for (Statement statement : block.getStatements()) {
            if (statement == exclude) {
                continue;
            }
            J.VariableDeclarations declaration = statement instanceof J.VariableDeclarations ?
                    (J.VariableDeclarations) statement : null;
            if (declaration == null && statement instanceof J.Try && ((J.Try) statement).getResources() != null) {
                for (J.Try.Resource resource : ((J.Try) statement).getResources()) {
                    if (resource.getVariableDeclarations() instanceof J.VariableDeclarations &&
                        declares((J.VariableDeclarations) resource.getVariableDeclarations(), name)) {
                        return true;
                    }
                }
            }
            if (declaration != null && declares(declaration, name)) {
                return true;
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
     * Whether the transaction is an argument of some call in the block, as it is once cache operations
     * have been migrated to take it.
     */
    private static boolean passesTransaction(J.Block body, J.Identifier tx) {
        AtomicBoolean passed = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                for (Expression argument : method.getArguments()) {
                    if (argument instanceof J.Identifier && ((J.Identifier) argument).getSimpleName().equals(tx.getSimpleName())) {
                        found.set(true);
                    }
                }
                return super.visitMethodInvocation(method, found);
            }
        }.visit(body, passed);
        return passed.get();
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

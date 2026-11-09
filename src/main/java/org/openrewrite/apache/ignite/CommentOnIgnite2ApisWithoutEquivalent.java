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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class CommentOnIgnite2ApisWithoutEquivalent extends Recipe {

    private static final String IGNITE = "org.apache.ignite.Ignite";
    private static final String IGNITE_CLIENT = "org.apache.ignite.client.IgniteClient";
    private static final String IGNITE_CACHE = "org.apache.ignite.IgniteCache";
    private static final String CLIENT_CACHE = "org.apache.ignite.client.ClientCache";

    static final String EMBEDDED_NODE = "TODO Ignite 3: there is no node to start from an Ignite 2 configuration. " +
            "Connect to an Ignite 3 cluster with IgniteClient.builder().addresses(...).build(), or start a node with " +
            "IgniteServer.start(name, configPath, workDir) from ignite-runner; the migration-tools configuration-converter " +
            "turns Ignite 2 XML configuration into Ignite 3 HOCON.";
    static final String CREATE_TABLE = "TODO Ignite 3: tables are not created on demand. Create the table with " +
            "CREATE TABLE through ignite.sql() or with ignite.catalog().createTable(...), for example from the DDL that the " +
            "migration-tools sql-ddl-generator writes for the cache configuration, and read it with ignite.tables().table(name).";
    static final String DROP_TABLE = "TODO Ignite 3: drop the table with DROP TABLE through ignite.sql() " +
            "or with ignite.catalog().dropTable(...).";
    static final String COMPUTE = "TODO Ignite 3: compute jobs are ComputeJob<T, R> classes deployed to the cluster and " +
            "run with ignite.compute().execute(JobTarget, JobDescriptor, arg); closures, lambdas and peer class loading are gone.";
    static final String DATA_STREAMER = "TODO Ignite 3: stream data into a table with " +
            "KeyValueView.streamData(Flow.Publisher, DataStreamerOptions) or RecordView.streamData(...).";
    static final String NO_EQUIVALENT = "TODO Ignite 3: has no equivalent of this Ignite 2 API.";
    static final String QUERY = "TODO Ignite 3: run SQL with ignite.sql().execute(tx, sql, args...), which returns a " +
            "ResultSet<SqlRow>, and scan queries with KeyValueView.query(tx, criteria). Review the SQL itself: the table of a " +
            "migrated cache is PUBLIC.\"<cache name>\", and the engine is Calcite rather than H2.";
    static final String SIZE = "TODO Ignite 3: KeyValueView has no size(); count the rows with SELECT COUNT(*) through ignite.sql().";
    static final String BINARY = "TODO Ignite 3: there are no binary objects; table.keyValueView() reads and writes " +
            "the columns as a KeyValueView<Tuple, Tuple>.";
    static final String CACHE_OPERATION = "TODO Ignite 3: KeyValueView has no equivalent of this cache operation.";
    static final String ASYNC = "TODO Ignite 3: the asynchronous KeyValueView operations take the transaction as their " +
            "first argument and return a CompletableFuture.";
    static final String CACHE_CONFIGURATION = "TODO Ignite 3: define a table with CREATE TABLE or ignite.catalog() " +
            "instead of a CacheConfiguration; the migration-tools sql-ddl-generator writes the DDL for existing cache configurations.";
    static final String ITERATION = "TODO Ignite 3: a KeyValueView cannot be iterated; read its entries with " +
            "query(tx, criteria), which returns a Cursor of Map.Entry, or with SELECT through ignite.sql().";
    static final String TRANSACTION = "TODO Ignite 3: a Transaction only commits or rolls back, with commit(), rollback(), " +
            "and commitAsync() and rollbackAsync(), which return a CompletableFuture; it has no state, label, timeout or " +
            "rollback-only flag, and cannot be suspended.";
    static final String TRANSACTIONS = "TODO Ignite 3: IgniteTransactions only begins transactions, with " +
            "begin(TransactionOptions), and runs code in one with runInTransaction(...); there is no current transaction " +
            "of the thread, and no labels, tracing or metrics.";
    static final String TRANSACTION_MODES = "TODO Ignite 3: transactions have no concurrency or isolation modes; " +
            "TransactionOptions sets only a timeout and whether the transaction is read-only.";
    static final String TRANSACTION_EXCEPTIONS = "TODO Ignite 3: transactions throw org.apache.ignite.tx.TransactionException, " +
            "with no subclasses for optimistic conflicts, timeouts, rollbacks, heuristic outcomes or deadlocks.";
    static final String NODE_CONFIGURATION = "TODO Ignite 3: nodes are configured with HOCON rather than IgniteConfiguration; " +
            "the migration-tools configuration-converter turns Ignite 2 configuration into node and cluster configuration.";

    private static final Map<MethodMatcher, String> TODOS = new LinkedHashMap<>();

    static {
        todo("org.apache.ignite.Ignition", EMBEDDED_NODE, "start(..)", "getOrStart(..)", "ignite(..)", "localIgnite()");
        for (String ignite : new String[]{IGNITE, IGNITE_CLIENT}) {
            todo(ignite, CREATE_TABLE, "getOrCreateCache*(..)", "createCache*(..)");
            todo(ignite, DROP_TABLE, "destroyCache*(..)");
        }
        todo(IGNITE, CREATE_TABLE, "getOrCreateCaches(..)", "createCaches(..)", "addCacheConfiguration(..)");
        todo(IGNITE, DROP_TABLE, "destroyCaches(..)");
        for (String compute : new String[]{"org.apache.ignite.IgniteCompute", "org.apache.ignite.client.ClientCompute"}) {
            todo(compute, COMPUTE, "run*(..)", "call*(..)", "broadcast*(..)", "execute*(..)", "apply*(..)",
                    "affinityRun*(..)", "affinityCall*(..)");
        }
        todo(IGNITE, DATA_STREAMER, "dataStreamer(..)");
        todo(IGNITE, NO_EQUIVALENT, "services(..)", "message(..)", "events(..)", "scheduler()", "atomicLong(..)",
                "atomicSequence(..)", "atomicReference(..)", "atomicStamped(..)", "countDownLatch(..)", "semaphore(..)",
                "reentrantLock(..)", "queue(..)", "set(..)", "affinity(..)", "binary()", "plugin(..)", "snapshot()",
                "encryption()");
        todo(IGNITE_CLIENT, NO_EQUIVALENT, "services(..)", "atomicLong(..)", "set(..)", "binary()");
        todo(IGNITE_CLIENT, QUERY, "query(..)");
        todo("org.apache.ignite.configuration.CacheConfiguration", CACHE_CONFIGURATION, "<constructor>(..)");
        todo("org.apache.ignite.configuration.IgniteConfiguration", NODE_CONFIGURATION, "<constructor>(..)");
    }

    private static final List<String> TRANSACTION_TYPES = Arrays.asList(
            "org.apache.ignite.transactions.Transaction", "org.apache.ignite.client.ClientTransaction");
    private static final List<String> TRANSACTIONS_TYPES = Arrays.asList(
            "org.apache.ignite.IgniteTransactions", "org.apache.ignite.client.ClientTransactions");
    private static final List<String> TRANSACTION_MODE_TYPES = Arrays.asList(
            "org.apache.ignite.transactions.TransactionConcurrency", "org.apache.ignite.transactions.TransactionIsolation");
    private static final List<String> TRANSACTION_EXCEPTION_TYPES = Arrays.asList(
            "org.apache.ignite.transactions.TransactionOptimisticException",
            "org.apache.ignite.transactions.TransactionTimeoutException",
            "org.apache.ignite.transactions.TransactionRollbackException",
            "org.apache.ignite.transactions.TransactionHeuristicException",
            "org.apache.ignite.transactions.TransactionDeadlockException");
    private static final MethodMatcher TX_START = new MethodMatcher("org.apache.ignite.IgniteTransactions txStart(..)");
    private static final MethodMatcher CLIENT_TX_START = new MethodMatcher("org.apache.ignite.client.ClientTransactions txStart(..)");

    private static void todo(String type, String comment, String... methods) {
        for (String method : methods) {
            TODOS.put(new MethodMatcher(type + " " + method, true), comment);
        }
    }

    /**
     * What to say about a method called on an Ignite 2 cache or transaction, whichever type declares it: every cache
     * method that {@link MigrateCacheToKeyValueView} does not migrate, and every transaction method but commit,
     * rollback and close.
     */
    private static @Nullable String receiverTodo(J.MethodInvocation mi) {
        if (mi.getSelect() == null || mi.getMethodType() == null ||
            TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), "java.lang.Object")) {
            return null;
        }
        JavaType receiver = mi.getSelect().getType();
        String name = mi.getSimpleName();
        if (isIgnite2Cache(receiver)) {
            if (MigrateCacheToKeyValueView.keyValueViewMethodName(mi) != null) {
                return null;
            }
            if (name.endsWith("Async")) {
                return ASYNC;
            }
            switch (name) {
                case "query":
                    return QUERY;
                case "size":
                case "sizeLong":
                    return SIZE;
                case "withKeepBinary":
                    return BINARY;
                case "iterator":
                case "forEach":
                case "spliterator":
                    return ITERATION;
                case "destroy":
                    return DROP_TABLE;
                default:
                    return CACHE_OPERATION;
            }
        }
        if (isOfAnyType(receiver, TRANSACTION_TYPES) &&
            !"commit".equals(name) && !"rollback".equals(name) && !"close".equals(name)) {
            return TRANSACTION;
        }
        if (isOfAnyType(receiver, TRANSACTIONS_TYPES) && !"txStart".equals(name)) {
            return TRANSACTIONS;
        }
        return null;
    }

    private static boolean isIgnite2Cache(@Nullable JavaType type) {
        return TypeUtils.isAssignableTo(IGNITE_CACHE, type) || TypeUtils.isAssignableTo(CLIENT_CACHE, type);
    }

    private static boolean isOfAnyType(@Nullable JavaType type, List<String> fullyQualifiedNames) {
        for (String fqn : fullyQualifiedNames) {
            if (TypeUtils.isOfClassType(type, fqn)) {
                return true;
            }
        }
        return false;
    }

    String displayName = "Comment on Ignite 2 APIs that Ignite 3 has no direct equivalent for";

    String description = "Adds a `// TODO Ignite 3` comment above each statement that uses an Ignite 2 API which " +
            "Ignite 3 removed, or replaced with something that cannot be derived from the call alone: starting an embedded " +
            "node, creating caches on demand, compute closures, data streamers, services and data structures, cache " +
            "queries, binary objects, iterating over a cache and the other cache operations that `KeyValueView` does " +
            "not have, and the transaction APIs beyond beginning, committing and rolling back. The comment says what " +
            "Ignite 3 offers instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.ignite..*", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                return TodoComments.addNoted(super.visitBlock(block, ctx), getCursor());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                noteTodos(method);
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                noteTodos(newClass);
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.ForEachLoop visitForEachLoop(J.ForEachLoop forLoop, ExecutionContext ctx) {
                if (isIgnite2Cache(forLoop.getControl().getIterable().getType())) {
                    TodoComments.noteOnEnclosingStatement(getCursor(), ITERATION);
                }
                return super.visitForEachLoop(forLoop, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                if (isOfAnyType(identifier.getType(), TRANSACTION_EXCEPTION_TYPES)) {
                    TodoComments.noteOnEnclosingStatement(getCursor(), TRANSACTION_EXCEPTIONS);
                } else if (isOfAnyType(identifier.getType(), TRANSACTION_MODE_TYPES) && !isTxStartArgument()) {
                    // MigrateTransactions drops the modes passed to txStart
                    TodoComments.noteOnEnclosingStatement(getCursor(), TRANSACTION_MODES);
                }
                return super.visitIdentifier(identifier, ctx);
            }

            private boolean isTxStartArgument() {
                J.MethodInvocation call = getCursor().firstEnclosing(J.MethodInvocation.class);
                return call != null && (TX_START.matches(call) || CLIENT_TX_START.matches(call));
            }

            private void noteTodos(MethodCall call) {
                Set<String> todos = new LinkedHashSet<>();
                for (Map.Entry<MethodMatcher, String> todo : TODOS.entrySet()) {
                    if (todo.getKey().matches(call)) {
                        todos.add(todo.getValue());
                    }
                }
                if (call instanceof J.MethodInvocation) {
                    String todo = receiverTodo((J.MethodInvocation) call);
                    if (todo != null) {
                        todos.add(todo);
                    }
                }
                for (String todo : todos) {
                    TodoComments.noteOnEnclosingStatement(getCursor(), todo);
                }
            }
        });
    }
}

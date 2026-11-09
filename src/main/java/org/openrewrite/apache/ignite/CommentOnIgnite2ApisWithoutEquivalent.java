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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        for (String cache : new String[]{IGNITE_CACHE, CLIENT_CACHE}) {
            todo(cache, QUERY, "query(..)");
            todo(cache, SIZE, "size*(..)");
            todo(cache, BINARY, "withKeepBinary()");
            todo(cache, ASYNC, "*Async(..)");
            todo(cache, CACHE_OPERATION, "invoke(..)", "invokeAll(..)", "withExpiryPolicy(..)", "getAndPutIfAbsent(..)",
                    "clear(java.lang.Object)", "clearAll(..)", "registerCacheEntryListener(..)",
                    "deregisterCacheEntryListener(..)");
        }
        todo(IGNITE_CACHE, CACHE_OPERATION, "lock(..)", "lockAll(..)", "localPeek(..)", "localClear(..)",
                "localClearAll(..)", "localEntries(..)", "localSize*(..)", "loadCache(..)", "localLoadCache(..)",
                "withSkipStore()", "withNoRetries()", "withPartitionRecover()", "withReadRepair(..)",
                "withAllowAtomicOpsInTx()", "localEvict(..)", "localPreloadPartition(..)", "preloadPartition(..)",
                "lostPartitions()", "rebalance()", "metrics(..)", "isLocalLocked(..)");
        todo("org.apache.ignite.configuration.CacheConfiguration", CACHE_CONFIGURATION, "<constructor>(..)");
        todo("org.apache.ignite.configuration.IgniteConfiguration", NODE_CONFIGURATION, "<constructor>(..)");
    }

    private static void todo(String type, String comment, String... methods) {
        for (String method : methods) {
            TODOS.put(new MethodMatcher(type + " " + method, true), comment);
        }
    }

    String displayName = "Comment on Ignite 2 APIs that Ignite 3 has no direct equivalent for";

    String description = "Adds a `// TODO Ignite 3` comment above each statement that uses an Ignite 2 API which " +
            "Ignite 3 removed, or replaced with something that cannot be derived from the call alone: starting an embedded " +
            "node, creating caches on demand, compute closures, data streamers, services and data structures, cache " +
            "queries, binary objects, and cache operations that `KeyValueView` does not have. The comment says what " +
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

            private void noteTodos(MethodCall call) {
                List<String> todos = new ArrayList<>();
                for (Map.Entry<MethodMatcher, String> todo : TODOS.entrySet()) {
                    if (todo.getKey().matches(call) && !todos.contains(todo.getValue())) {
                        todos.add(todo.getValue());
                    }
                }
                for (String todo : todos) {
                    TodoComments.noteOnEnclosingStatement(getCursor(), todo);
                }
            }
        });
    }
}

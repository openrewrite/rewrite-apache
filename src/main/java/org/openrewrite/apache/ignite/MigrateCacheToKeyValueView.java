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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateCacheToKeyValueView extends Recipe {

    static final String IGNITE_CACHE = "org.apache.ignite.IgniteCache";
    static final String CLIENT_CACHE = "org.apache.ignite.client.ClientCache";
    static final String KEY_VALUE_VIEW = "org.apache.ignite.table.KeyValueView";
    static final String TRANSACTION = "org.apache.ignite.tx.Transaction";

    private static final List<String> TRANSACTION_TYPES = Arrays.asList(
            "org.apache.ignite.transactions.Transaction",
            "org.apache.ignite.client.ClientTransaction",
            // Transactions that MigrateTransactions has already migrated
            TRANSACTION);

    private static final Set<String> ENDING_METHODS = new HashSet<>(Arrays.asList(
            "commit", "rollback", "close", "commitAsync", "rollbackAsync"));

    private static final MethodMatcher IGNITE_CACHE_LOOKUP = new MethodMatcher("org.apache.ignite.Ignite cache(java.lang.String)");
    private static final MethodMatcher IGNITE_GET_OR_CREATE = new MethodMatcher("org.apache.ignite.Ignite getOrCreateCache(java.lang.String)");
    private static final MethodMatcher CLIENT_CACHE_LOOKUP = new MethodMatcher("org.apache.ignite.client.IgniteClient cache(java.lang.String)");
    private static final MethodMatcher CLIENT_GET_OR_CREATE = new MethodMatcher("org.apache.ignite.client.IgniteClient getOrCreateCache(java.lang.String)");

    /**
     * A cache name that Ignite 3 reads as the same table name without quoting it.
     * A conservative subset of what {@code IgniteNameUtils.quoteIfNeeded} leaves alone.
     */
    private static final Pattern NORMALIZED_IDENTIFIER = Pattern.compile("[A-Z_][A-Z0-9_]*");

    static final String LOOKUP_TODO = "TODO Ignite 3: read the table with " +
            "ignite.tables().table(name).keyValueView(KeyClass.class, ValueClass.class); " +
            "the key and value types could not be determined here.";

    static final String OBJECT_TODO = "TODO Ignite 3: a KeyValueView maps keys and values to table columns, " +
            "so it cannot hold java.lang.Object; use table.keyValueView() for a KeyValueView<Tuple, Tuple>, " +
            "or classes whose fields match the columns.";

    static final String CALLBACK_TODO = "TODO Ignite 3: Ignite 2 ran this operation in the enclosing transaction only if " +
            "this lambda or anonymous class ran on the thread that began it. Pass null instead of the transaction if it " +
            "runs on another thread.";

    static final String CALLBACK_NOT_FINAL_TODO = "TODO Ignite 3: Ignite 2 ran this operation in the enclosing transaction " +
            "if this lambda or anonymous class ran on the thread that began it. It now runs in a transaction of its own, " +
            "as the variable holding that transaction is not effectively final and cannot be used here.";

    String displayName = "Migrate Ignite 2 caches to Ignite 3 `KeyValueView` tables";

    String description = "Ignite 3 replaces caches with tables. A cache obtained with `cache(name)` or " +
            "`getOrCreateCache(name)` becomes `tables().table(IgniteNameUtils.quoteIfNeeded(name)).keyValueView(K.class, V.class)`, " +
            "which reads the table that the Ignite 3 migration tools create for that cache. " +
            "The key-value operations of `IgniteCache` and `ClientCache` move to `KeyValueView`, " +
            "taking the transaction as their first argument: the enclosing Ignite 2 transaction where there is one, " +
            "and `null` otherwise, which runs the operation in an implicit transaction of its own. A lambda or " +
            "anonymous class inside a transaction gets a comment unless it is passed to a `java.util` method that " +
            "runs it on the same thread. `getAllOutTx` becomes `getAll` outside any transaction. " +
            "`containsKey` becomes `contains`, `containsKeys` becomes `containsAll`, and `clear` becomes `removeAll`. " +
            "Operations without an Ignite 3 equivalent, such as `size`, `query` and `invoke`, are left as they are. " +
            "A lookup with `getOrCreateCache` gets a comment, since the table has to be created before it can be read.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(IGNITE_CACHE, true),
                        new UsesType<>(CLIENT_CACHE, true),
                        new UsesType<>("org.apache.ignite.Ignite", true),
                        new UsesType<>("org.apache.ignite.client.IgniteClient", true)),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                        // The rewrites print Java syntax, such as class literals and try/finally
                        return sourceFile instanceof J.CompilationUnit;
                    }

                    @Override
                    public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        // Every file changes the cache types, so that signatures still agree across files
                        doAfterVisit(new ChangeType(IGNITE_CACHE, KEY_VALUE_VIEW, true).getVisitor());
                        doAfterVisit(new ChangeType(CLIENT_CACHE, KEY_VALUE_VIEW, true).getVisitor());
                        return super.visitCompilationUnit(cu, ctx);
                    }

                    @Override
                    public J visitBlock(J.Block block, ExecutionContext ctx) {
                        return TodoComments.addNoted((J.Block) super.visitBlock(block, ctx), getCursor());
                    }

                    @Override
                    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        JavaType.Parameterized cacheType = TypeUtils.asParameterized(multiVariable.getType());
                        if (cacheType != null && isIgnite2Cache(cacheType) &&
                            cacheType.getTypeParameters().stream().anyMatch(TypeUtils::isObject)) {
                            TodoComments.noteOnEnclosingStatement(getCursor(), OBJECT_TODO);
                        }
                        return super.visitVariableDeclarations(multiVariable, ctx);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        // Decide on the receiver as written, before a chained cache lookup is rewritten into a view
                        boolean onIgnite2Cache = method.getSelect() != null &&
                                isIgnite2Cache(method.getSelect().getType()) &&
                                !isKeyValueViewMethod(method.getMethodType());

                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation mi = (J.MethodInvocation) j;

                        if (IGNITE_CACHE_LOOKUP.matches(mi) || IGNITE_GET_OR_CREATE.matches(mi) ||
                            CLIENT_CACHE_LOOKUP.matches(mi) || CLIENT_GET_OR_CREATE.matches(mi)) {
                            return migrateLookup(mi, ctx);
                        }

                        if (onIgnite2Cache) {
                            String newName = keyValueViewMethodName(mi);
                            if (newName != null) {
                                if ("getAllOutTx".equals(mi.getSimpleName())) {
                                    // Read outside of any transaction, as Ignite 2 did
                                    return withTransactionArgument(mi, newName, null);
                                }
                                InTransaction inTransaction = transactionFor(getCursor());
                                if (inTransaction.todo != null) {
                                    TodoComments.noteOnEnclosingStatement(getCursor(), inTransaction.todo);
                                }
                                return withTransactionArgument(mi, newName, inTransaction.tx);
                            }
                        }
                        return mi;
                    }

                    private J migrateLookup(J.MethodInvocation mi, ExecutionContext ctx) {
                        JavaType.FullyQualified[] keyValue = keyAndValueTypes(mi);
                        if (keyValue == null || mi.getSelect() == null || !isTargetAnIgnite2Cache()) {
                            if (!isSelectOfOperationWithoutEquivalent()) {
                                TodoComments.noteOnEnclosingStatement(getCursor(), LOOKUP_TODO);
                            }
                            return mi;
                        }

                        Expression name = mi.getArguments().get(0);
                        String tableName;
                        if (name instanceof J.Literal && ((J.Literal) name).getValue() instanceof String &&
                            NORMALIZED_IDENTIFIER.matcher((String) ((J.Literal) name).getValue()).matches()) {
                            tableName = "#{any(java.lang.String)}";
                        } else {
                            tableName = "IgniteNameUtils.quoteIfNeeded(#{any(java.lang.String)})";
                            maybeAddImport("org.apache.ignite.lang.util.IgniteNameUtils");
                        }

                        if (IGNITE_GET_OR_CREATE.matches(mi) || CLIENT_GET_OR_CREATE.matches(mi)) {
                            // table() returns null for a table that does not exist
                            TodoComments.noteOnEnclosingStatement(getCursor(), CommentOnIgnite2ApisWithoutEquivalent.CREATE_TABLE);
                        }

                        for (JavaType.FullyQualified type : keyValue) {
                            JavaType.FullyQualified topLevel = type;
                            while (topLevel.getOwningClass() != null) {
                                topLevel = topLevel.getOwningClass();
                            }
                            maybeAddImport(topLevel.getFullyQualifiedName(), false);
                        }

                        return JavaTemplate.builder("#{any(org.apache.ignite.Ignite)}.tables().table(" + tableName +
                                                    ").keyValueView(#{any(java.lang.Class)}, #{any(java.lang.Class)})")
                                .imports("org.apache.ignite.lang.util.IgniteNameUtils")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "ignite-api-3"))
                                .build()
                                .apply(updateCursor(mi), mi.getCoordinates().replace(),
                                        mi.getSelect(), name, classLiteral(keyValue[0]), classLiteral(keyValue[1]));
                    }

                    /**
                     * Whether the lookup is used straight away for an operation that {@code KeyValueView} has no
                     * equivalent of, which is commented on by itself.
                     */
                    private boolean isSelectOfOperationWithoutEquivalent() {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        return parent instanceof J.MethodInvocation &&
                               ((J.MethodInvocation) parent).getSelect() == getCursor().getValue() &&
                               keyValueViewMethodName((J.MethodInvocation) parent) == null;
                    }

                    /**
                     * Only rewrite a lookup whose value lands somewhere typed as an Ignite 2 cache, which
                     * becomes a {@code KeyValueView} too, or that is used straight away as a cache.
                     */
                    private boolean isTargetAnIgnite2Cache() {
                        Cursor parent = getCursor().dropParentUntil(p -> !(p instanceof JRightPadded) &&
                                                                         !(p instanceof JLeftPadded) &&
                                                                         !(p instanceof J.Parentheses));
                        Object tree = parent.getValue();
                        if (tree instanceof J.VariableDeclarations.NamedVariable) {
                            return isIgnite2Cache(((J.VariableDeclarations.NamedVariable) tree).getType());
                        }
                        if (tree instanceof J.Assignment) {
                            return isIgnite2Cache(((J.Assignment) tree).getVariable().getType());
                        }
                        if (tree instanceof J.Return) {
                            J.MethodDeclaration md = parent.firstEnclosing(J.MethodDeclaration.class);
                            return md != null && md.getReturnTypeExpression() != null &&
                                   isIgnite2Cache(md.getReturnTypeExpression().getType());
                        }
                        if (tree instanceof J.MethodInvocation) {
                            J.MethodInvocation outer = (J.MethodInvocation) tree;
                            return outer.getSelect() != null && outer.getSelect().getId().equals(getCursor().<J>getValue().getId());
                        }
                        return false;
                    }
                }
        );
    }

    private static boolean isIgnite2Cache(@Nullable JavaType type) {
        return TypeUtils.isAssignableTo(IGNITE_CACHE, type) || TypeUtils.isAssignableTo(CLIENT_CACHE, type);
    }

    private static boolean isKeyValueViewMethod(JavaType.@Nullable Method methodType) {
        return methodType != null && TypeUtils.isOfClassType(methodType.getDeclaringType(), KEY_VALUE_VIEW);
    }

    /**
     * The key and value types the cache lookup was inferred to return, when both are plain classes a
     * {@code KeyValueView} can map.
     */
    private static JavaType.FullyQualified @Nullable [] keyAndValueTypes(J.MethodInvocation mi) {
        if (mi.getMethodType() == null) {
            return null;
        }
        JavaType.Parameterized returnType = TypeUtils.asParameterized(mi.getMethodType().getReturnType());
        if (returnType == null || returnType.getTypeParameters().size() != 2) {
            return null;
        }
        JavaType.FullyQualified[] keyValue = new JavaType.FullyQualified[2];
        for (int i = 0; i < 2; i++) {
            JavaType typeParameter = returnType.getTypeParameters().get(i);
            if (!(typeParameter instanceof JavaType.Class) ||
                TypeUtils.isObject(typeParameter) ||
                TypeUtils.isOfClassType(typeParameter, "org.apache.ignite.binary.BinaryObject")) {
                return null;
            }
            keyValue[i] = (JavaType.FullyQualified) typeParameter;
        }
        return keyValue;
    }

    private static J.FieldAccess classLiteral(JavaType.FullyQualified type) {
        JavaType.Parameterized classType = new JavaType.Parameterized(null,
                JavaType.ShallowClass.build("java.lang.Class"), singletonList(type));
        String className = type.getClassName();
        return new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY,
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), className, type, null),
                JLeftPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", classType, null)),
                classType);
    }

    /**
     * The {@code KeyValueView} method that does what this Ignite 2 cache method does, or {@code null} when there is none.
     */
    static @Nullable String keyValueViewMethodName(J.MethodInvocation mi) {
        int args = (int) mi.getArguments().stream().filter(a -> !(a instanceof J.Empty)).count();
        switch (mi.getSimpleName()) {
            case "get":
            case "getAndRemove":
                return args == 1 ? mi.getSimpleName() : null;
            case "getAll":
            case "getAllOutTx":
                return args == 1 ? "getAll" : null;
            case "containsKey":
                return args == 1 ? "contains" : null;
            case "containsKeys":
                return args == 1 ? "containsAll" : null;
            case "put":
            case "getAndPut":
            case "putIfAbsent":
            case "getAndReplace":
                return args == 2 ? mi.getSimpleName() : null;
            case "putAll":
                return args == 1 ? "putAll" : null;
            case "remove":
                return args == 1 || args == 2 ? "remove" : null;
            case "replace":
                return args == 2 || args == 3 ? "replace" : null;
            case "removeAll":
                return args <= 1 ? "removeAll" : null;
            case "clear":
                return args == 0 ? "removeAll" : null;
            default:
                return null;
        }
    }

    private static final class InTransaction {
        static final InTransaction NONE = new InTransaction(null, null);

        final J.@Nullable Identifier tx;
        final @Nullable String todo;

        InTransaction(J.@Nullable Identifier tx, @Nullable String todo) {
            this.tx = tx;
            this.todo = todo;
        }
    }

    /**
     * The Ignite 2 transaction that the operation at this cursor runs in. Ignite 2 binds transactions to the thread,
     * so this is the transaction begun most recently before the operation, in an enclosing block or as the resource
     * of an enclosing try-with-resources, or the transaction that the enclosing method or lambda is given, as long as
     * it has not been committed, rolled back or closed since.
     */
    private static InTransaction transactionFor(Cursor cursor) {
        // Transactions ended between where they were begun and the operation
        Set<String> ended = new HashSet<>();
        // Whether the operation is in a lambda or anonymous class, which can only use effectively final variables
        boolean inLambda = false;
        // Whether that lambda or anonymous class may run on another thread
        boolean inCallback = false;
        Object child = cursor.getValue();
        for (Cursor c = cursor.getParent(); c != null; c = c.getParent()) {
            Object tree = c.getValue();
            if (!(tree instanceof J)) {
                continue;
            }
            J.Identifier tx = null;
            J scope = null;
            boolean assignedOnce = false;
            if (tree instanceof J.ClassDeclaration) {
                return InTransaction.NONE;
            } else if (tree instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) tree;
                tx = transactionParameter(method.getParameters());
                scope = method.getBody();
                if (tx == null) {
                    if (!isMethodOfAnonymousClass(c)) {
                        return InTransaction.NONE;
                    }
                    inLambda = true;
                    inCallback = true;
                }
            } else if (tree instanceof J.Lambda) {
                J.Lambda lambda = (J.Lambda) tree;
                tx = transactionParameter(lambda.getParameters().getParameters());
                scope = lambda.getBody();
                if (tx == null) {
                    inLambda = true;
                    inCallback |= !runsOnSameThread(c, lambda);
                }
            } else if (tree instanceof J.Try) {
                J.Try t = (J.Try) tree;
                if (child == t.getBody() && t.getResources() != null) {
                    for (J.Try.Resource resource : t.getResources()) {
                        tx = startedTransaction(resource.getVariableDeclarations());
                        if (tx != null) {
                            assignedOnce = true;
                            break;
                        }
                    }
                } else if (child == t.getFinally()) {
                    // A finally block runs once the try block has committed or rolled back
                    ended.addAll(endedTransactions(t.getBody()));
                }
            } else if (tree instanceof J.Block) {
                List<Statement> statements = ((J.Block) tree).getStatements();
                int index = indexOf(statements, child);
                for (int i = index - 1; i >= 0 && tx == null; i--) {
                    Statement statement = statements.get(i);
                    tx = startedTransaction(statement);
                    if (tx == null) {
                        ended.addAll(endedTransactions(statement));
                    } else {
                        scope = (J) tree;
                        assignedOnce = statement instanceof J.VariableDeclarations && !isAssigned(scope, tx.getSimpleName());
                    }
                }
            }

            if (tx != null) {
                if (ended.contains(tx.getSimpleName())) {
                    return InTransaction.NONE;
                }
                if (!inLambda) {
                    return new InTransaction(tx, null);
                }
                boolean effectivelyFinal = assignedOnce ||
                                           !(tree instanceof J.Block) && !isAssigned(scope, tx.getSimpleName());
                if (!effectivelyFinal) {
                    return new InTransaction(null, CALLBACK_NOT_FINAL_TODO);
                }
                return new InTransaction(tx, inCallback ? CALLBACK_TODO : null);
            }
            child = tree;
        }
        return InTransaction.NONE;
    }

    private static int indexOf(List<Statement> statements, Object statement) {
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i) == statement) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTransaction(@Nullable JavaType type) {
        for (String transactionType : TRANSACTION_TYPES) {
            if (TypeUtils.isOfClassType(type, transactionType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullLiteral(@Nullable Expression e) {
        return e instanceof J.Literal && ((J.Literal) e).getValue() == null;
    }

    /**
     * The transaction variable that this declaration or assignment gives a transaction to.
     */
    private static J.@Nullable Identifier startedTransaction(@Nullable J j) {
        if (j instanceof J.VariableDeclarations) {
            J.VariableDeclarations vd = (J.VariableDeclarations) j;
            if (vd.getVariables().size() == 1 && isTransaction(vd.getType())) {
                Expression initializer = vd.getVariables().get(0).getInitializer();
                if (initializer != null && !isNullLiteral(initializer)) {
                    return vd.getVariables().get(0).getName();
                }
            }
        } else if (j instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) j;
            if (assignment.getVariable() instanceof J.Identifier && isTransaction(assignment.getVariable().getType()) &&
                !isNullLiteral(assignment.getAssignment())) {
                return (J.Identifier) assignment.getVariable();
            }
        }
        return null;
    }

    private static J.@Nullable Identifier transactionParameter(List<? extends J> parameters) {
        for (J parameter : parameters) {
            if (parameter instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) parameter;
                if (vd.getVariables().size() == 1 && isTransaction(vd.getType())) {
                    return vd.getVariables().get(0).getName();
                }
            }
        }
        return null;
    }

    /**
     * The transaction variables this tree commits, rolls back or closes, outside of lambdas and classes.
     */
    private static Set<String> endedTransactions(J tree) {
        Set<String> names = new HashSet<>();
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Lambda visitLambda(J.Lambda lambda, Set<String> n) {
                return lambda;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Set<String> n) {
                return classDecl;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, Set<String> n) {
                return newClass.getBody() == null ? super.visitNewClass(newClass, n) : newClass;
            }

            @Override
            public J.Try visitTry(J.Try tryable, Set<String> n) {
                if (tryable.getResources() != null) {
                    for (J.Try.Resource resource : tryable.getResources()) {
                        if (resource.getVariableDeclarations() instanceof J.Identifier &&
                            isTransaction(resource.getVariableDeclarations().getType())) {
                            n.add(((J.Identifier) resource.getVariableDeclarations()).getSimpleName());
                        }
                    }
                }
                return super.visitTry(tryable, n);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<String> n) {
                if (method.getSelect() instanceof J.Identifier && ENDING_METHODS.contains(method.getSimpleName()) &&
                    isTransaction(method.getSelect().getType())) {
                    n.add(((J.Identifier) method.getSelect()).getSimpleName());
                }
                return super.visitMethodInvocation(method, n);
            }
        }.visit(tree, names);
        return names;
    }

    /**
     * Whether a variable of this name is assigned anywhere in this tree, which keeps it from being effectively final.
     */
    private static boolean isAssigned(@Nullable J tree, String name) {
        if (tree == null) {
            return false;
        }
        AtomicBoolean assigned = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean a) {
                check(assignment.getVariable(), a);
                return super.visitAssignment(assignment, a);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean a) {
                check(assignOp.getVariable(), a);
                return super.visitAssignmentOperation(assignOp, a);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean a) {
                if (unary.getOperator().isModifying()) {
                    check(unary.getExpression(), a);
                }
                return super.visitUnary(unary, a);
            }

            private void check(Expression variable, AtomicBoolean a) {
                if (variable instanceof J.Identifier && ((J.Identifier) variable).getSimpleName().equals(name)) {
                    a.set(true);
                }
            }
        }.visit(tree, assigned);
        return assigned.get();
    }

    private static boolean isMethodOfAnonymousClass(Cursor methodCursor) {
        Cursor body = methodCursor.getParentTreeCursor();
        return body.getValue() instanceof J.Block && body.getParentTreeCursor().getValue() instanceof J.NewClass;
    }

    /**
     * Whether this lambda is passed to a method of {@code java.util} or {@code java.lang.Iterable}, such as
     * {@code forEach} or {@code computeIfAbsent}, which runs it on the calling thread.
     */
    private static boolean runsOnSameThread(Cursor lambdaCursor, J.Lambda lambda) {
        Object parent = lambdaCursor.getParentTreeCursor().getValue();
        if (!(parent instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation call = (J.MethodInvocation) parent;
        if (call.getMethodType() == null || call.getArguments().stream().noneMatch(a -> a == lambda)) {
            return false;
        }
        JavaType.FullyQualified declaringType = call.getMethodType().getDeclaringType();
        String packageName = declaringType.getPackageName();
        return "java.util".equals(packageName) || "java.util.stream".equals(packageName) ||
               TypeUtils.isOfClassType(declaringType, "java.lang.Iterable");
    }

    private static J.MethodInvocation withTransactionArgument(J.MethodInvocation mi, String newName, J.@Nullable Identifier tx) {
        Expression txArgument = tx == null ?
                new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, null, "null", null, JavaType.Primitive.Null) :
                tx.withId(randomId()).withPrefix(Space.EMPTY);

        List<Expression> arguments = new ArrayList<>();
        List<Expression> original = mi.getArguments();
        if (original.size() == 1 && original.get(0) instanceof J.Empty) {
            arguments.add(txArgument);
        } else {
            arguments.add(txArgument.withPrefix(original.get(0).getPrefix()));
            arguments.add(original.get(0).withPrefix(Space.SINGLE_SPACE));
            arguments.addAll(original.subList(1, original.size()));
        }

        JavaType.Method methodType = mi.getMethodType();
        if (methodType != null) {
            List<JavaType> parameterTypes = new ArrayList<>();
            parameterTypes.add(JavaType.ShallowClass.build(TRANSACTION));
            parameterTypes.addAll(methodType.getParameterTypes());
            List<String> parameterNames = new ArrayList<>();
            parameterNames.add("tx");
            parameterNames.addAll(methodType.getParameterNames());
            JavaType returnType = methodType.getReturnType();
            if ("removeAll".equals(newName) && original.size() == 1 && !(original.get(0) instanceof J.Empty)) {
                // Ignite 3 returns the keys that were not there to remove
                returnType = JavaType.ShallowClass.build("java.util.Collection");
            }
            methodType = methodType
                    .withName(newName)
                    .withDeclaringType(JavaType.ShallowClass.build(KEY_VALUE_VIEW))
                    .withParameterTypes(parameterTypes)
                    .withParameterNames(parameterNames)
                    .withReturnType(returnType);
        }

        return mi.withName(mi.getName().withSimpleName(newName).withType(methodType))
                .withArguments(arguments)
                .withMethodType(methodType);
    }
}

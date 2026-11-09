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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
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

    private static final List<String> IGNITE2_TRANSACTIONS = Arrays.asList(
            "org.apache.ignite.transactions.Transaction",
            "org.apache.ignite.client.ClientTransaction");

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

    String displayName = "Migrate Ignite 2 caches to Ignite 3 `KeyValueView` tables";

    String description = "Ignite 3 replaces caches with tables. A cache obtained with `cache(name)` or " +
            "`getOrCreateCache(name)` becomes `tables().table(IgniteNameUtils.quoteIfNeeded(name)).keyValueView(K.class, V.class)`, " +
            "which reads the table that the Ignite 3 migration tools create for that cache. " +
            "The key-value operations of `IgniteCache` and `ClientCache` move to `KeyValueView`, " +
            "taking the transaction as their first argument: the enclosing Ignite 2 transaction where there is one, " +
            "and `null` otherwise, which runs the operation in an implicit transaction of its own. " +
            "`containsKey` becomes `contains`, `containsKeys` becomes `containsAll`, and `clear` becomes `removeAll`. " +
            "Operations without an Ignite 3 equivalent, such as `size`, `query` and `invoke`, are left as they are.";

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
                                return withTransactionArgument(mi, newName, transactionFor(getCursor()));
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
    private static @Nullable String keyValueViewMethodName(J.MethodInvocation mi) {
        int args = (int) mi.getArguments().stream().filter(a -> !(a instanceof J.Empty)).count();
        switch (mi.getSimpleName()) {
            case "get":
            case "getAndRemove":
                return args == 1 ? mi.getSimpleName() : null;
            case "getAll":
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

    /**
     * The Ignite 2 transaction that the operation at this cursor runs in: the resource of an enclosing
     * try-with-resources, or a variable declared earlier in an enclosing block. Ignite 2 binds transactions
     * to the thread, so these are the operations it enlisted implicitly.
     */
    private static J.@Nullable Identifier transactionFor(Cursor cursor) {
        Set<String> ended = new HashSet<>();
        Object child = cursor.getValue();
        for (Cursor c = cursor.getParent(); c != null; c = c.getParent()) {
            Object tree = c.getValue();
            if (tree instanceof J.MethodDeclaration || tree instanceof J.ClassDeclaration ||
                tree instanceof J.NewClass && child == ((J.NewClass) tree).getBody()) {
                return null;
            }
            if (tree instanceof J.Try && ((J.Try) tree).getResources() != null && child == ((J.Try) tree).getBody()) {
                for (J.Try.Resource resource : ((J.Try) tree).getResources()) {
                    J.Identifier tx = transactionVariable(resource.getVariableDeclarations());
                    if (tx != null) {
                        return ended.contains(tx.getSimpleName()) ? null : tx;
                    }
                }
            }
            if (tree instanceof J.Block) {
                J.Identifier tx = null;
                for (Statement statement : ((J.Block) tree).getStatements()) {
                    if (statement == child) {
                        break;
                    }
                    J.Identifier declared = transactionVariable(statement);
                    if (declared != null) {
                        tx = declared;
                        ended.remove(declared.getSimpleName());
                    } else {
                        String endedName = endedTransaction(statement);
                        if (endedName != null) {
                            ended.add(endedName);
                        }
                    }
                }
                if (tx != null) {
                    return ended.contains(tx.getSimpleName()) ? null : tx;
                }
            }
            if (tree instanceof J) {
                child = tree;
            }
        }
        return null;
    }

    /**
     * The name of the variable this statement commits, rolls back or closes, after which later
     * operations no longer run in that Ignite 2 transaction.
     */
    private static @Nullable String endedTransaction(Statement statement) {
        if (statement instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) statement;
            if (mi.getSelect() instanceof J.Identifier &&
                ("commit".equals(mi.getSimpleName()) || "rollback".equals(mi.getSimpleName()) ||
                 "close".equals(mi.getSimpleName())) &&
                IGNITE2_TRANSACTIONS.stream().anyMatch(t -> TypeUtils.isOfClassType(mi.getSelect().getType(), t))) {
                return ((J.Identifier) mi.getSelect()).getSimpleName();
            }
        }
        return null;
    }

    private static J.@Nullable Identifier transactionVariable(J j) {
        if (j instanceof J.VariableDeclarations) {
            J.VariableDeclarations vd = (J.VariableDeclarations) j;
            if (vd.getVariables().size() == 1) {
                for (String transactionType : IGNITE2_TRANSACTIONS) {
                    if (TypeUtils.isOfClassType(vd.getType(), transactionType)) {
                        return vd.getVariables().get(0).getName();
                    }
                }
            }
        }
        return null;
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

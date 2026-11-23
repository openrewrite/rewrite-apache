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
package org.openrewrite.apache.commons.configuration;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateFileBasedConfigurations extends Recipe {

    String displayName = "Migrate Commons Configuration 1.x file handling to 2.x";

    String description = "Configuration 2 creates a file-based configuration with a `FileBasedConfigurationBuilder`, " +
            "and loads, saves and locates it with a `FileHandler`, where 1.x did all of that in the configuration. " +
            "This replaces the 1.x constructors that took a file name, `File` or `URL` with a builder, and the " +
            "`load`, `save` and file location methods with those of a `FileHandler`. A local variable that is saved " +
            "or asked for its file keeps the builder or `FileHandler` in a variable of its own, so that it is saved " +
            "where it was loaded from. A subclass constructor that had its superclass load a file loads it with a " +
            "`FileHandler` of its own. Configurations created from a file, or created empty in a statement of their " +
            "own, are given a `DefaultListDelimiterHandler(',')`, as 1.x split values at commas by default and 2.x " +
            "does not. The location of a configuration held in a field is not known where it is used, so a `save()` " +
            "without arguments, or a question for its file, on a field is left to be migrated by hand, and so is the " +
            "list delimiter of one created empty in a field's initializer. " +
            "This recipe matches the 1.x API by the names the move to the `org.apache.commons.configuration2` " +
            "package gives it, and is meant to run as part of `UpgradeApacheCommonsConfiguration_1_2`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + "*", true), new JavaVisitor<ExecutionContext>() {

            /**
             * The local variables of the blocks being visited that get a builder or a file handler, by name.
             */
            private final Map<String, Plan> plans = new HashMap<>();

            /**
             * Constructor calls that a plan replaces along with the declaration they initialize.
             */
            private final Set<UUID> plannedConstructions = new HashSet<>();

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                // The statements of a class body are declarations of its members
                Object parent = getCursor().getParentTreeCursor().getValue();
                List<Plan> blockPlans = parent instanceof J.ClassDeclaration || parent instanceof J.NewClass ?
                        emptyList() : plan(block, ctx);
                for (Plan plan : blockPlans) {
                    if (plan.handle != null) {
                        plans.put(plan.name, plan);
                    }
                    if (plan.kind == Kind.BUILDER) {
                        plannedConstructions.add(plan.construction.getId());
                    }
                }
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                for (Plan plan : blockPlans) {
                    plans.remove(plan.name);
                }
                return blockPlans.isEmpty() ? b : apply(b, blockPlans, ctx);
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J j = super.visitMethodDeclaration(method, ctx);
                if (!(j instanceof J.MethodDeclaration)) {
                    return j;
                }
                J.MethodDeclaration m = (J.MethodDeclaration) j;
                J.Block body = m.getBody();
                if (!m.isConstructor() || body == null || body.getStatements().isEmpty() ||
                    !(body.getStatements().get(0) instanceof J.MethodInvocation)) {
                    return m;
                }
                // A subclass constructor that had its 1.x superclass load a file loads it itself
                J.MethodInvocation superCall = (J.MethodInvocation) body.getStatements().get(0);
                JavaType.Method constructor = superCall.getMethodType();
                List<Expression> arguments = arguments(superCall);
                if (!"super".equals(superCall.getSimpleName()) || constructor == null || arguments.size() != 1 ||
                    constructor.getParameterTypes().size() != 1 ||
                    !PARAMETERS_BY_CLASS.containsKey(constructor.getDeclaringType().getFullyQualifiedName())) {
                    return m;
                }
                String locationType = typeName(constructor.getParameterTypes().get(0));
                Expression location = arguments.get(0);
                boolean file = "java.io.File".equals(locationType);
                // 1.x only loaded a File that exists, which is asked of the File again
                if (!LOCATION_SETTERS.containsKey(locationType) || file && !(location instanceof J.Identifier)) {
                    return m;
                }
                maybeAddImport(DEFAULT_LIST_DELIMITER_HANDLER);
                maybeAddImport(FILE_HANDLER);
                Cursor superCallCursor = new Cursor(new Cursor(new Cursor(getCursor().getParentOrThrow(), m), body), superCall);
                Statement delimiter = JavaTemplate.builder("setListDelimiterHandler(new DefaultListDelimiterHandler(','));")
                        .contextSensitive()
                        .javaParser(parser(ctx))
                        .imports(DEFAULT_LIST_DELIMITER_HANDLER)
                        .build()
                        .apply(superCallCursor, superCall.getCoordinates().replace());
                String load = "new FileHandler(this).load(#{any(" + locationType + ")});";
                Statement loading = JavaTemplate.builder(file ? "if (#{any(java.io.File)}.exists()) {\n" + load + "\n}" : load)
                        .contextSensitive()
                        .javaParser(parser(ctx))
                        .imports(FILE_HANDLER)
                        .build()
                        .apply(superCallCursor, superCall.getCoordinates().replace(), file ? new Object[]{location, location} : new Object[]{location});
                Cursor bodyCursor = superCallCursor.getParentOrThrow();
                List<Statement> statements = new ArrayList<>(body.getStatements().size() + 1);
                statements.add(delimiter.withPrefix(superCall.getPrefix()));
                statements.add(autoFormat(loading.withPrefix(Space.format("\n" + superCall.getPrefix().getIndent())), ctx, bodyCursor));
                statements.addAll(body.getStatements().subList(1, body.getStatements().size()));
                return m.withBody(body.withStatements(statements));
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J j = super.visitNewClass(newClass, ctx);
                if (!(j instanceof J.NewClass) || plannedConstructions.contains(j.getId())) {
                    return j;
                }
                J.NewClass n = (J.NewClass) j;
                Source source = Source.of(n);
                if (source == null) {
                    return n;
                }
                addImports(source.configurationClass);
                return JavaTemplate.builder(source.builderExpression(supportsDiamond()) + "\n        .getConfiguration()")
                        .javaParser(parser(ctx))
                        .imports(source.configurationClass, BUILDER, PARAMETERS, DEFAULT_LIST_DELIMITER_HANDLER)
                        .build()
                        .apply(getCursor(), n.getCoordinates().replace(), source.location);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation)) {
                    return j;
                }
                J.MethodInvocation m = (J.MethodInvocation) j;
                JavaType.Method type = m.getMethodType();
                if (type == null || m.getSelect() == null || !isFileOperation(type)) {
                    return m;
                }
                Plan plan = planOf(m.getSelect());
                List<Expression> arguments = arguments(m);
                if (plan != null && plan.kind == Kind.BUILDER) {
                    String call;
                    if ("save".equals(type.getName()) && arguments.isEmpty()) {
                        call = ".save()";
                    } else if ("setAutoSave".equals(type.getName())) {
                        call = ".setAutoSave(#{any(boolean)})";
                    } else if (FILE_HANDLER_OPERATIONS.contains(type.getName())) {
                        call = ".getFileHandler()." + type.getName() + parameters(type);
                    } else {
                        return m;
                    }
                    return apply(m, "#{any(" + BUILDER + "<" + plan.configurationClass + ">)}" + call, plan.handle, arguments, ctx);
                }
                if (!FILE_HANDLER_OPERATIONS.contains(type.getName())) {
                    return m;
                }
                if (plan != null && plan.kind == Kind.FILE_HANDLER) {
                    return apply(m, "#{any(" + FILE_HANDLER + ")}." + type.getName() + parameters(type), plan.handle, arguments, ctx);
                }
                if (("load".equals(type.getName()) || "save".equals(type.getName())) && !arguments.isEmpty()) {
                    // Loading from or saving to a place given in the call doesn't need to know where it came from
                    maybeAddImport(FILE_HANDLER);
                    return apply(m, "new FileHandler(#{any(" + FILE_BASED + ")})." + type.getName() + parameters(type), m.getSelect(), arguments, ctx);
                }
                return m;
            }

            private J apply(J.MethodInvocation m, String code, @Nullable J target, List<Expression> arguments, ExecutionContext ctx) {
                Object[] parameters = new Object[arguments.size() + 1];
                parameters[0] = target;
                for (int i = 0; i < arguments.size(); i++) {
                    parameters[i + 1] = arguments.get(i);
                }
                return JavaTemplate.builder(code)
                        .javaParser(parser(ctx))
                        .imports(FILE_HANDLER, BUILDER)
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), parameters);
            }

            private @Nullable Plan planOf(Expression select) {
                if (!(select instanceof J.Identifier)) {
                    return null;
                }
                J.Identifier identifier = (J.Identifier) select;
                JavaType.Variable variable = identifier.getFieldType();
                if (variable == null || !(variable.getOwner() instanceof JavaType.Method)) {
                    return null;
                }
                return plans.get(identifier.getSimpleName());
            }

            /**
             * Decide what each local variable of a file-based configuration declared in this block needs, and create
             * the builder or file handler variable it gets, which the calls on it are moved to as they are visited.
             */
            private List<Plan> plan(J.Block block, ExecutionContext ctx) {
                List<Plan> blockPlans = new ArrayList<>();
                List<Statement> statements = block.getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    List<Statement> rest = statements.subList(i + 1, statements.size());
                    Cursor statementCursor = new Cursor(getCursor(), statement);
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
                        if (declarations.getVariables().size() != 1) {
                            continue;
                        }
                        J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                        if (!(variable.getInitializer() instanceof J.NewClass)) {
                            continue;
                        }
                        J.NewClass construction = (J.NewClass) variable.getInitializer();
                        Usage usage = Usage.of(variable.getSimpleName(), rest);
                        Source source = Source.of(construction);
                        J.Identifier target = variable.getName().withPrefix(Space.EMPTY);
                        if (source != null) {
                            if (usage.locates && !usage.reassigned) {
                                blockPlans.add(builderPlan(statement, statementCursor, variable.getSimpleName(), source, construction, target, ctx));
                            }
                        } else if (isEmptyConstruction(construction)) {
                            String configurationClass = TypeUtils.asFullyQualified(construction.getType()).getFullyQualifiedName();
                            if (usage.locates && !usage.reassigned) {
                                String handlerName = VariableNameUtils.generateVariableName(variable.getSimpleName() + "Handler",
                                        statementCursor, VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                                J.VariableDeclarations handler = JavaTemplate.builder("FileHandler " + handlerName + " = new FileHandler(#{any(" + FILE_BASED + ")});")
                                        .javaParser(parser(ctx))
                                        .imports(FILE_HANDLER)
                                        .build()
                                        .apply(statementCursor, statement.getCoordinates().replace(), target);
                                blockPlans.add(new Plan(Kind.FILE_HANDLER, statement.getId(), variable.getSimpleName(),
                                        configurationClass, construction, target, !usage.setsDelimiter,
                                        castFor(declarations.getTypeAsFullyQualified(), configurationClass), handler,
                                        handler.getVariables().get(0).getName().withPrefix(Space.EMPTY)));
                            } else if (!usage.setsDelimiter) {
                                blockPlans.add(new Plan(Kind.DELIMITER_ONLY, statement.getId(), variable.getSimpleName(),
                                        configurationClass, construction, target, true,
                                        castFor(declarations.getTypeAsFullyQualified(), configurationClass), null, null));
                            }
                        }
                    } else if (statement instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) statement;
                        J.Identifier assigned = assignedName(assignment.getVariable());
                        if (assigned == null || !(assignment.getAssignment() instanceof J.NewClass)) {
                            continue;
                        }
                        J.NewClass construction = (J.NewClass) assignment.getAssignment();
                        Usage usage = Usage.of(assigned.getSimpleName(), rest);
                        Source source = Source.of(construction);
                        JavaType.Variable variable = assigned.getFieldType();
                        if (source != null) {
                            // A local variable assigned in a statement of its own, such as in a try block
                            if (usage.locates && !usage.reassigned && assignment.getVariable() instanceof J.Identifier &&
                                variable != null && variable.getOwner() instanceof JavaType.Method) {
                                blockPlans.add(builderPlan(statement, statementCursor, assigned.getSimpleName(), source,
                                        construction, assigned.withPrefix(Space.EMPTY), ctx));
                            }
                        } else if (isEmptyConstruction(construction) && !usage.setsDelimiter) {
                            String configurationClass = TypeUtils.asFullyQualified(construction.getType()).getFullyQualifiedName();
                            blockPlans.add(new Plan(Kind.DELIMITER_ONLY, statement.getId(), assigned.getSimpleName(),
                                    configurationClass, construction, assignment.getVariable().withPrefix(Space.EMPTY), true,
                                    castFor(TypeUtils.asFullyQualified(assignment.getVariable().getType()), configurationClass),
                                    null, null));
                        }
                    }
                }
                return blockPlans;
            }

            private Plan builderPlan(Statement statement, Cursor statementCursor, String name, Source source,
                                     J.NewClass construction, J target, ExecutionContext ctx) {
                String builderName = VariableNameUtils.generateVariableName(name + "Builder",
                        statementCursor, VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                J.VariableDeclarations builder = JavaTemplate.builder(
                                "FileBasedConfigurationBuilder<" + simpleName(source.configurationClass) + "> " + builderName + " = " +
                                source.builderExpression(supportsDiamond()) + ";")
                        .javaParser(parser(ctx))
                        .imports(source.configurationClass, BUILDER, PARAMETERS, DEFAULT_LIST_DELIMITER_HANDLER)
                        .build()
                        .apply(statementCursor, statement.getCoordinates().replace(), source.location);
                return new Plan(Kind.BUILDER, statement.getId(), name, source.configurationClass, construction, target,
                        false, null, builder, builder.getVariables().get(0).getName().withPrefix(Space.EMPTY));
            }

            /**
             * Put the variables the plans created into the block, after the calls on them were moved to them.
             */
            private J.Block apply(J.Block b, List<Plan> blockPlans, ExecutionContext ctx) {
                Map<UUID, Plan> byStatement = new HashMap<>();
                for (Plan plan : blockPlans) {
                    byStatement.put(plan.statement, plan);
                }
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                List<Statement> statements = new ArrayList<>();
                boolean changed = false;
                for (Statement statement : b.getStatements()) {
                    Plan plan = byStatement.get(statement.getId());
                    if (plan == null) {
                        statements.add(statement);
                        continue;
                    }
                    changed = true;
                    Cursor statementCursor = new Cursor(blockCursor, statement);
                    Space next = Space.format("\n" + statement.getPrefix().getIndent());
                    if (plan.kind == Kind.BUILDER && plan.created != null && plan.handle != null) {
                        addImports(plan.configurationClass);
                        JavaTemplate getConfiguration = JavaTemplate.builder("#{any(" + BUILDER + "<" + plan.configurationClass + ">)}.getConfiguration()")
                                .javaParser(parser(ctx))
                                .build();
                        statements.add(plan.created.withPrefix(statement.getPrefix()));
                        if (statement instanceof J.VariableDeclarations) {
                            J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
                            J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                            J.NewClass construction = (J.NewClass) variable.getInitializer();
                            Expression configuration = getConfiguration.apply(new Cursor(new Cursor(statementCursor, variable), construction),
                                    construction.getCoordinates().replace(), plan.handle);
                            statements.add(declarations
                                    .withVariables(singletonList(variable.withInitializer(configuration.withPrefix(construction.getPrefix()))))
                                    .withPrefix(next));
                        } else {
                            J.Assignment assignment = (J.Assignment) statement;
                            J.NewClass construction = (J.NewClass) assignment.getAssignment();
                            Expression configuration = getConfiguration.apply(new Cursor(statementCursor, construction),
                                    construction.getCoordinates().replace(), plan.handle);
                            statements.add(assignment.withAssignment(configuration.withPrefix(construction.getPrefix())).withPrefix(next));
                        }
                        continue;
                    }
                    statements.add(statement);
                    if (plan.delimiter) {
                        maybeAddImport(DEFAULT_LIST_DELIMITER_HANDLER);
                        // The method is declared by AbstractConfiguration, which a variable may not be declared as
                        String configuration = plan.cast == null ? "#{any(" + PACKAGE + "AbstractConfiguration)}" :
                                "((" + simpleName(plan.cast) + ") #{any(java.lang.Object)})";
                        statements.add(JavaTemplate.builder(configuration + ".setListDelimiterHandler(new DefaultListDelimiterHandler(','));")
                                .javaParser(parser(ctx))
                                .imports(DEFAULT_LIST_DELIMITER_HANDLER, plan.configurationClass)
                                .build()
                                .<Statement>apply(statementCursor, statement.getCoordinates().replace(), plan.target)
                                .withPrefix(next));
                    }
                    if (plan.kind == Kind.FILE_HANDLER && plan.created != null) {
                        maybeAddImport(FILE_HANDLER);
                        statements.add(plan.created.withPrefix(next));
                    }
                }
                return changed ? b.withStatements(statements) : b;
            }

            private boolean supportsDiamond() {
                JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                JavaVersion version = sourceFile == null ? null : sourceFile.getMarkers().findFirst(JavaVersion.class).orElse(null);
                return version == null || version.getMajorVersion() >= 7;
            }

            private void addImports(String configurationClass) {
                maybeAddImport(configurationClass);
                maybeAddImport(BUILDER);
                maybeAddImport(PARAMETERS);
                maybeAddImport(DEFAULT_LIST_DELIMITER_HANDLER);
            }
        });
    }

    private static J.@Nullable Identifier assignedName(Expression variable) {
        if (variable instanceof J.Identifier) {
            return (J.Identifier) variable;
        }
        if (variable instanceof J.FieldAccess && ((J.FieldAccess) variable).getTarget() instanceof J.Identifier &&
            "this".equals(((J.Identifier) ((J.FieldAccess) variable).getTarget()).getSimpleName())) {
            return ((J.FieldAccess) variable).getName();
        }
        return null;
    }

    /**
     * The class a configuration held in a variable of the given type is cast to so that its list delimiter handler can
     * be set, or null when the type declares {@code setListDelimiterHandler}, such as where it is declared as
     * {@code Configuration}.
     */
    private static @Nullable String castFor(JavaType.@Nullable FullyQualified declaredType, String configurationClass) {
        return extendsAbstractConfiguration(declaredType) ? null : configurationClass;
    }

    private static boolean isEmptyConstruction(J.NewClass construction) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(construction.getType());
        return construction.getBody() == null && type != null &&
               PARAMETERS_BY_CLASS.containsKey(type.getFullyQualifiedName()) &&
               arguments(construction.getArguments()).isEmpty();
    }

    private static boolean isFileOperation(JavaType.Method type) {
        return FILE_CONFIGURATION_TYPES.contains(type.getDeclaringType().getFullyQualifiedName()) &&
               (FILE_HANDLER_OPERATIONS.contains(type.getName()) || BUILDER_OPERATIONS.contains(type.getName()));
    }

    private static List<Expression> arguments(J.MethodInvocation m) {
        return arguments(m.getArguments());
    }

    private static List<Expression> arguments(List<Expression> arguments) {
        List<Expression> actual = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            if (!(argument instanceof J.Empty)) {
                actual.add(argument);
            }
        }
        return actual;
    }

    private static String parameters(JavaType.Method type) {
        StringBuilder parameters = new StringBuilder("(");
        for (int i = 0; i < type.getParameterTypes().size(); i++) {
            if (i > 0) {
                parameters.append(", ");
            }
            parameters.append("#{any(").append(typeName(type.getParameterTypes().get(i))).append(")}");
        }
        return parameters.append(")").toString();
    }

    private static String typeName(JavaType type) {
        if (type instanceof JavaType.Primitive) {
            return ((JavaType.Primitive) type).getKeyword();
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "java.lang.Object" : fq.getFullyQualifiedName();
    }

    private static String simpleName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    private static JavaParser.Builder<?, ?> parser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2");
    }

    /**
     * A 1.x constructor call that loaded a configuration from a file name, {@code File} or {@code URL}.
     */
    @Value
    private static class Source {
        String configurationClass;
        String parameters;
        String setter;
        String locationType;
        Expression location;

        static @Nullable Source of(J.NewClass construction) {
            JavaType.Method constructor = construction.getConstructorType();
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(construction.getType());
            List<Expression> arguments = arguments(construction.getArguments());
            if (construction.getBody() != null || constructor == null || type == null || arguments.size() != 1 ||
                constructor.getParameterTypes().size() != 1) {
                return null;
            }
            String parameters = PARAMETERS_BY_CLASS.get(type.getFullyQualifiedName());
            String locationType = typeName(constructor.getParameterTypes().get(0));
            String setter = LOCATION_SETTERS.get(locationType);
            if (parameters == null || setter == null) {
                return null;
            }
            return new Source(type.getFullyQualifiedName(), parameters, setter, locationType, arguments.get(0));
        }

        /**
         * 1.x only loaded a {@code File} that exists, so the builder may fail to load it and start out empty.
         *
         * @param diamond whether the source is Java 7 or later, which infers the type argument
         */
        String builderExpression(boolean diamond) {
            return "new FileBasedConfigurationBuilder<" + (diamond ? "" : simpleName(configurationClass)) + ">(" +
                   simpleName(configurationClass) + ".class" +
                   ("java.io.File".equals(locationType) ? ", null, true" : "") + ")\n" +
                   "        .configure(new Parameters()." + parameters + "()\n" +
                   "                ." + setter + "(#{any(" + locationType + ")})\n" +
                   "                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')))";
        }
    }

    private enum Kind {
        BUILDER, FILE_HANDLER, DELIMITER_ONLY
    }

    @Value
    private static class Plan {
        Kind kind;
        UUID statement;
        String name;
        String configurationClass;
        J.NewClass construction;

        /**
         * The configuration, as the statements that follow the plan's refer to it.
         */
        J target;

        boolean delimiter;

        /**
         * The class the configuration is cast to so that its list delimiter handler can be set, or null when the
         * variable's type declares {@code setListDelimiterHandler}.
         */
        @Nullable
        String cast;

        J.@Nullable VariableDeclarations created;

        J.@Nullable Identifier handle;
    }

    /**
     * How the statements following a declaration use the variable it declares.
     */
    private static class Usage {
        boolean locates;
        boolean setsDelimiter;
        boolean reassigned;

        static Usage of(String name, List<Statement> statements) {
            Usage usage = new Usage();
            JavaIsoVisitor<Usage> visitor = new JavaIsoVisitor<Usage>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Usage u) {
                    J.MethodInvocation m = super.visitMethodInvocation(method, u);
                    if (!isNamed(m.getSelect(), name) || m.getMethodType() == null) {
                        return m;
                    }
                    String methodName = m.getSimpleName();
                    if (DELIMITER_METHODS.contains(methodName)) {
                        u.setsDelimiter = true;
                    } else if (isFileOperation(m.getMethodType()) &&
                               (!("load".equals(methodName) || "save".equals(methodName)) || arguments(m).isEmpty())) {
                        u.locates = true;
                    }
                    return m;
                }

                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, Usage u) {
                    if (isNamed(assignment.getVariable(), name)) {
                        u.reassigned = true;
                    }
                    return super.visitAssignment(assignment, u);
                }
            };
            for (Statement statement : statements) {
                visitor.visit(statement, usage);
            }
            return usage;
        }

        private static boolean isNamed(@Nullable Expression expression, String name) {
            J.Identifier identifier = expression == null ? null : assignedName(expression);
            return identifier != null && name.equals(identifier.getSimpleName());
        }
    }
}

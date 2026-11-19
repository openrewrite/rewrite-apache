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
package org.openrewrite.apache.poi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

public class ReplaceTypeCodesWithEnums extends Recipe {

    @Getter
    final String displayName = "Replace Apache POI `int` type codes with `CellType` and `HyperlinkType`";

    @Getter
    final String description = "Apache POI 3.x describes cells and hyperlinks with `int` codes, which POI 4.0 replaced with the " +
            "`CellType` and `HyperlinkType` enumerations. This recipe moves the accessors to the enum returning `getCellTypeEnum()`, " +
            "`getCachedFormulaResultTypeEnum()`, `evaluateFormulaCellEnum()` and `getTypeEnum()`, the only ones POI 3.17 has, and " +
            "replaces the `Cell.CELL_TYPE_*` and `Hyperlink.LINK_*` constants and the `int` literals used for them. " +
            "Local variables and the parameters of private methods that hold a code become the enum, and `switch` labels become " +
            "the unqualified names that every Java version accepts. A value that has to stay an `int`, such as the parameter of " +
            "a public method, is converted at that boundary.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.poi..*", true), new ReplaceCodes());
    }

    /**
     * A family of POI 3.x {@code int} codes and the enumeration that replaced it.
     */
    private enum TypeCode {
        CELL("org.apache.poi.ss.usermodel.CellType", "org.apache.poi.ss.usermodel.Cell", "CELL_TYPE_",
                ".getCode()", ".forInt(#{any(int)})",
                "_NONE", "NUMERIC", "STRING", "FORMULA", "BLANK", "BOOLEAN", "ERROR"),
        // POI 5 has no public getCode() or forInt() here, and the ordinal of each link type equals its code
        HYPERLINK("org.apache.poi.common.usermodel.HyperlinkType", "org.apache.poi.common.usermodel.Hyperlink", "LINK_",
                ".ordinal()", ".values()[#{any(int)}]",
                "NONE", null, "URL", "DOCUMENT", "EMAIL", "FILE");

        final String enumType;
        final String simpleName;
        final String constantOwner;
        final String constantPrefix;
        /**
         * What turns an enum constant into its code, for code that has to stay an {@code int}.
         */
        final String toCode;
        /**
         * What follows the enum to turn a code into its constant, for code that has to stay an {@code int}.
         */
        final String fromCode;
        /**
         * The names of the enum constants, indexed by code plus one since the first code is -1.
         */
        final List<@Nullable String> names;

        TypeCode(String enumType, String constantOwner, String constantPrefix, String toCode, String fromCode, @Nullable String... names) {
            this.enumType = enumType;
            this.simpleName = enumType.substring(enumType.lastIndexOf('.') + 1);
            this.constantOwner = constantOwner;
            this.constantPrefix = constantPrefix;
            this.toCode = toCode;
            this.fromCode = fromCode;
            this.names = Arrays.asList(names);
        }

        JavaType.FullyQualified type() {
            return JavaType.ShallowClass.build(enumType);
        }

        @Nullable
        String nameOf(int code) {
            return -1 <= code && code < names.size() - 1 ? names.get(code + 1) : null;
        }

        @Nullable
        String nameOfConstant(String constant) {
            if (constant.startsWith(constantPrefix)) {
                String name = constant.substring(constantPrefix.length());
                return names.contains(name) ? name : null;
            }
            return null;
        }

        int codeOf(String name) {
            return names.indexOf(name) - 1;
        }

        static @Nullable TypeCode ofConstant(JavaType.Variable variable) {
            for (TypeCode code : values()) {
                if (code.nameOfConstant(variable.getName()) != null &&
                    TypeUtils.isAssignableTo(code.constantOwner, variable.getOwner())) {
                    return code;
                }
            }
            return null;
        }
    }

    /**
     * A method that returns an {@code int} code in POI 3.x, and the method of POI 3.17 that returns the enum.
     */
    @RequiredArgsConstructor
    private static final class Accessor {
        final MethodMatcher matcher;
        final String enumAccessor;
        final TypeCode code;

        Accessor(String pattern, String enumAccessor, TypeCode code) {
            this(new MethodMatcher(pattern, true), enumAccessor, code);
        }
    }

    /**
     * A method that takes an {@code int} code in POI 3.x and has an overload that takes the enum.
     */
    @RequiredArgsConstructor
    private static final class Sink {
        final MethodMatcher matcher;
        final int argument;
        final TypeCode code;

        Sink(String pattern, int argument, TypeCode code) {
            this(new MethodMatcher(pattern, true), argument, code);
        }
    }

    private static final List<Accessor> ACCESSORS = Arrays.asList(
            new Accessor("org.apache.poi.ss.usermodel.Cell getCellType()", "getCellTypeEnum", TypeCode.CELL),
            new Accessor("org.apache.poi.ss.usermodel.Cell getCachedFormulaResultType()", "getCachedFormulaResultTypeEnum", TypeCode.CELL),
            new Accessor("org.apache.poi.ss.usermodel.CellValue getCellType()", "getCellTypeEnum", TypeCode.CELL),
            new Accessor("org.apache.poi.ss.usermodel.FormulaEvaluator evaluateFormulaCell(org.apache.poi.ss.usermodel.Cell)",
                    "evaluateFormulaCellEnum", TypeCode.CELL),
            new Accessor("org.apache.poi.common.usermodel.Hyperlink getType()", "getTypeEnum", TypeCode.HYPERLINK));

    private static final List<Sink> SINKS = Arrays.asList(
            new Sink("org.apache.poi.ss.usermodel.Cell setCellType(int)", 0, TypeCode.CELL),
            new Sink("org.apache.poi.ss.usermodel.Row createCell(int, int)", 1, TypeCode.CELL),
            new Sink("org.apache.poi.ss.usermodel.CreationHelper createHyperlink(int)", 0, TypeCode.HYPERLINK));

    private static final List<MethodMatcher> ASSERTIONS = Arrays.asList(
            new MethodMatcher("org.junit.Assert assert*Equals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assert*Equals(..)"),
            new MethodMatcher("org.testng.Assert assert*Equals(..)"),
            new MethodMatcher("junit.framework.Assert assertEquals(..)"),
            new MethodMatcher("junit.framework.TestCase assertEquals(..)"));

    private static @Nullable Accessor accessorOf(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        // POI 4.0 kept these names and made them return the enum, so only an `int` still has to move
        if (type != null && type.getReturnType() == JavaType.Primitive.Int) {
            for (Accessor accessor : ACCESSORS) {
                if (accessor.matcher.matches(method)) {
                    return accessor;
                }
            }
        }
        return null;
    }

    private static @Nullable Sink sinkOf(J.MethodInvocation method) {
        for (Sink sink : SINKS) {
            if (sink.matcher.matches(method)) {
                return sink;
            }
        }
        return null;
    }

    private static JavaType.@Nullable Variable variableOf(@Nullable Expression expression) {
        Expression e = expression == null ? null : expression.unwrap();
        if (e instanceof J.Identifier) {
            return ((J.Identifier) e).getFieldType();
        }
        return e instanceof J.FieldAccess ? ((J.FieldAccess) e).getName().getFieldType() : null;
    }

    private static @Nullable Integer intValue(J tree) {
        J j = tree instanceof Expression ? ((Expression) tree).unwrap() : tree;
        if (j instanceof J.Unary && ((J.Unary) j).getOperator() == J.Unary.Type.Negative) {
            Integer value = intValue(((J.Unary) j).getExpression());
            return value == null ? null : Integer.valueOf(-value);
        }
        return j instanceof J.Literal && ((J.Literal) j).getValue() instanceof Integer ? (Integer) ((J.Literal) j).getValue() : null;
    }

    private static String methodKey(JavaType.Method method) {
        return method.getDeclaringType().getFullyQualifiedName() + '#' + method.getName() + method.getParameterTypes();
    }

    private static String key(JavaType.Variable variable) {
        JavaType owner = variable.getOwner();
        return (owner instanceof JavaType.Method ? methodKey((JavaType.Method) owner) : String.valueOf(owner)) + '#' + variable.getName();
    }

    private static String parameterKey(JavaType.Method method, int parameter) {
        return methodKey(method) + '#' + method.getParameterNames().get(parameter);
    }

    private static J.@Nullable Block casesOf(Object tree) {
        if (tree instanceof J.Switch) {
            return ((J.Switch) tree).getCases();
        }
        return tree instanceof J.SwitchExpression ? ((J.SwitchExpression) tree).getCases() : null;
    }

    private static @Nullable Expression selectorOf(Object tree) {
        if (tree instanceof J.Switch) {
            return ((J.Switch) tree).getSelector().getTree();
        }
        return tree instanceof J.SwitchExpression ? ((J.SwitchExpression) tree).getSelector().getTree() : null;
    }

    private static boolean isDefault(J label) {
        return label instanceof J.Identifier && "default".equals(((J.Identifier) label).getSimpleName());
    }

    /**
     * Which {@code int} variables of a source file can become the enum, and what follows from that for each expression.
     * A variable qualifies when everything assigned to it is a code and everything done with it also works on the enum.
     */
    private static final class Conversions {
        private final Map<String, TypeCode> variables = new HashMap<>();
        private final Set<TypeCode> takenNames = EnumSet.noneOf(TypeCode.class);
        private boolean changed;

        static Conversions of(J.CompilationUnit cu) {
            Conversions conversions = new Conversions();
            new TakenNames().visit(cu, conversions.takenNames);
            Candidates finder = new Candidates();
            Set<String> candidates = new HashSet<>();
            finder.visit(cu, candidates);
            candidates.removeAll(finder.others);
            if (!candidates.isEmpty()) {
                conversions.solve(cu, candidates);
            }
            return conversions;
        }

        private void solve(J.CompilationUnit cu, Set<String> candidates) {
            do {
                changed = false;
                new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> c) {
                        if (variable.getInitializer() != null && variable.getVariableType() != null) {
                            write(key(variable.getVariableType()), variable.getInitializer(), c);
                        }
                        return super.visitVariable(variable, c);
                    }

                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, Set<String> c) {
                        JavaType.Variable variable = variableOf(assignment.getVariable());
                        if (variable != null) {
                            write(key(variable), assignment.getAssignment(), c);
                            TypeCode code = of(variable);
                            if (code != null && isValue(getCursor()) && !acceptsEnum(getCursor(), code)) {
                                reject(variable, c);
                            }
                        }
                        return super.visitAssignment(assignment, c);
                    }

                    @Override
                    public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignment, Set<String> c) {
                        reject(variableOf(assignment.getVariable()), c);
                        return super.visitAssignmentOperation(assignment, c);
                    }

                    @Override
                    public J.Unary visitUnary(J.Unary unary, Set<String> c) {
                        if (unary.getOperator().isModifying()) {
                            reject(variableOf(unary.getExpression()), c);
                        }
                        return super.visitUnary(unary, c);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<String> c) {
                        JavaType.Method type = method.getMethodType();
                        if (type != null) {
                            List<Expression> arguments = method.getArguments();
                            for (int i = 0; i < type.getParameterNames().size(); i++) {
                                String parameter = parameterKey(type, i);
                                if (i < arguments.size() && arguments.size() == type.getParameterNames().size()) {
                                    write(parameter, arguments.get(i), c);
                                } else {
                                    reject(parameter, c);
                                }
                            }
                        }
                        return super.visitMethodInvocation(method, c);
                    }

                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference memberRef, Set<String> c) {
                        JavaType.Method type = memberRef.getMethodType();
                        if (type != null) {
                            for (int i = 0; i < type.getParameterNames().size(); i++) {
                                reject(parameterKey(type, i), c);
                            }
                        }
                        return super.visitMemberReference(memberRef, c);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> c) {
                        JavaType.Variable variable = identifier.getFieldType();
                        TypeCode code = variable == null ? null : variables.get(key(variable));
                        if (code != null && !isWrite(getCursor()) && !acceptsEnum(getCursor(), code)) {
                            reject(variable, c);
                        }
                        return super.visitIdentifier(identifier, c);
                    }
                }.visit(cu, candidates);
            } while (changed);
        }

        /**
         * @return Whether something uses the result of the expression at the cursor, rather than it being a statement.
         */
        static boolean isValue(Cursor cursor) {
            Object parent = cursor.getParentTreeCursor().getValue();
            return !(parent instanceof J.Block || parent instanceof J.ForLoop.Control ||
                     parent instanceof J.Case && ((J.Case) parent).getBody() != cursor.getValue());
        }

        private static boolean isWrite(Cursor identifier) {
            Object parent = identifier.getParentTreeCursor().getValue();
            return parent instanceof J.VariableDeclarations.NamedVariable &&
                   ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier.getValue() ||
                   parent instanceof J.Assignment && ((J.Assignment) parent).getVariable() == identifier.getValue();
        }

        private void write(String variable, Expression value, Set<String> candidates) {
            if (!candidates.contains(variable)) {
                return;
            }
            TypeCode code = variables.get(variable);
            TypeCode written = codeOf(value);
            if (code == null && written != null) {
                variables.put(variable, written);
                changed = true;
            } else if (code != null && !isCode(value, code)) {
                reject(variable, candidates);
            }
        }

        private void reject(JavaType.@Nullable Variable variable, Set<String> candidates) {
            if (variable != null) {
                reject(key(variable), candidates);
            }
        }

        private void reject(String variable, Set<String> candidates) {
            changed |= candidates.remove(variable);
            variables.remove(variable);
        }

        @Nullable
        TypeCode of(JavaType.@Nullable Variable variable) {
            return variable == null ? null : variables.get(key(variable));
        }

        /**
         * @return Whether the simple name of the enum already means something else in this source file.
         */
        boolean isNameTaken(TypeCode code) {
            return takenNames.contains(code);
        }

        boolean isParameterConverted(JavaType.Method method, int parameter) {
            return variables.containsKey(parameterKey(method, parameter));
        }

        /**
         * @return The family of the code this expression stands for, or {@code null} when it is no known code.
         * An {@code int} literal is not one by itself, only {@link #isCode(Expression, TypeCode)} next to something that is.
         */
        @Nullable
        TypeCode codeOf(@Nullable Expression expression) {
            Expression e = expression == null ? null : expression.unwrap();
            if (e instanceof J.MethodInvocation) {
                Accessor accessor = accessorOf((J.MethodInvocation) e);
                return accessor == null ? null : accessor.code;
            }
            if (e instanceof J.Assignment) {
                return of(variableOf(((J.Assignment) e).getVariable()));
            }
            if (e instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) e;
                TypeCode code = codeOf(ternary.getTruePart());
                code = code == null ? codeOf(ternary.getFalsePart()) : code;
                return code != null && isCode(ternary.getTruePart(), code) && isCode(ternary.getFalsePart(), code) ? code : null;
            }
            JavaType.Variable variable = variableOf(e);
            if (variable == null) {
                return null;
            }
            TypeCode constant = TypeCode.ofConstant(variable);
            return constant == null ? of(variable) : constant;
        }

        boolean isCode(Expression expression, TypeCode code) {
            Expression e = expression.unwrap();
            if (e instanceof J.Ternary) {
                return isCode(((J.Ternary) e).getTruePart(), code) && isCode(((J.Ternary) e).getFalsePart(), code);
            }
            Integer value = intValue(e);
            return value == null ? codeOf(e) == code : code.nameOf(value) != null;
        }

        /**
         * @return The family of the enum that the context of the expression at the cursor takes in place of the
         * {@code int}, or {@code null} when the value has to stay an {@code int} there.
         */
        @Nullable
        TypeCode expected(Cursor cursor) {
            Expression self = cursor.getValue();
            Cursor parentCursor = cursor.getParentTreeCursor();
            Object parent = parentCursor.getValue();
            if (parent instanceof J.Parentheses) {
                return expected(parentCursor);
            }
            if (parent instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) parent;
                TypeCode code = ternary.getCondition() == self ? null : expected(parentCursor);
                Expression other = ternary.getTruePart() == self ? ternary.getFalsePart() : ternary.getTruePart();
                return code != null && isCode(other, code) ? code : null;
            }
            if (parent instanceof J.ControlParentheses) {
                return switchCode(parentCursor.getParentTreeCursor().getValue());
            }
            if (parent instanceof J.Binary) {
                J.Binary binary = (J.Binary) parent;
                if (binary.getOperator() != J.Binary.Type.Equal && binary.getOperator() != J.Binary.Type.NotEqual) {
                    return null;
                }
                return comparedWith(self, binary.getLeft() == self ? binary.getRight() : binary.getLeft());
            }
            if (parent instanceof J.VariableDeclarations.NamedVariable) {
                J.VariableDeclarations.NamedVariable variable = (J.VariableDeclarations.NamedVariable) parent;
                return variable.getInitializer() == self ? of(variable.getVariableType()) : null;
            }
            if (parent instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) parent;
                return assignment.getAssignment() == self ? of(variableOf(assignment.getVariable())) : null;
            }
            return parent instanceof J.MethodInvocation ? expectedArgument((J.MethodInvocation) parent, self) : null;
        }

        private @Nullable TypeCode comparedWith(Expression self, Expression other) {
            TypeCode code = codeOf(other);
            if (code == null) {
                code = codeOf(self);
                return code != null && isCode(other, code) ? code : null;
            }
            return code;
        }

        private @Nullable TypeCode expectedArgument(J.MethodInvocation method, Expression self) {
            int index = method.getArguments().indexOf(self);
            if (index < 0) {
                return null;
            }
            Sink sink = sinkOf(method);
            if (sink != null && sink.argument == index) {
                return sink.code;
            }
            JavaType.Method type = method.getMethodType();
            if (type != null && index < type.getParameterNames().size() && isParameterConverted(type, index)) {
                return variables.get(parameterKey(type, index));
            }
            for (MethodMatcher assertion : ASSERTIONS) {
                if (assertion.matches(method)) {
                    for (Expression argument : method.getArguments()) {
                        TypeCode code = argument == self ? null : comparedWith(self, argument);
                        if (code != null) {
                            return code;
                        }
                    }
                }
            }
            return null;
        }

        boolean acceptsEnum(Cursor cursor, TypeCode code) {
            return expected(cursor) == code || isStringConcatenation(cursor);
        }

        private static boolean isStringConcatenation(Cursor cursor) {
            Cursor parent = cursor.getParentTreeCursor();
            if (parent.getValue() instanceof J.Parentheses) {
                return isStringConcatenation(parent);
            }
            return parent.getValue() instanceof J.Binary &&
                   ((J.Binary) parent.getValue()).getOperator() == J.Binary.Type.Addition &&
                   TypeUtils.isString(((J.Binary) parent.getValue()).getType());
        }

        /**
         * @return The family of the enum a {@code switch} can select on, which takes its selector being a code or at
         * least one label being a constant, and every label having an enum constant to become.
         */
        @Nullable
        TypeCode switchCode(Object tree) {
            J.Block cases = casesOf(tree);
            if (cases == null) {
                return null;
            }
            TypeCode code = codeOf(selectorOf(tree));
            List<J> labels = new ArrayList<>();
            for (Statement statement : cases.getStatements()) {
                if (statement instanceof J.Case) {
                    for (J label : ((J.Case) statement).getCaseLabels()) {
                        if (!isDefault(label)) {
                            labels.add(label);
                            JavaType.Variable variable = label instanceof Expression ? variableOf((Expression) label) : null;
                            TypeCode constant = variable == null ? null : TypeCode.ofConstant(variable);
                            code = code == null ? constant : code;
                        }
                    }
                }
            }
            if (code == null) {
                return null;
            }
            for (J label : labels) {
                if (!(label instanceof Expression) || !isLabel((Expression) label, code)) {
                    return null;
                }
            }
            return code;
        }

        private static boolean isLabel(Expression label, TypeCode code) {
            Integer value = intValue(label);
            if (value != null) {
                return code.nameOf(value) != null;
            }
            JavaType.Variable variable = variableOf(label);
            return variable != null && TypeCode.ofConstant(variable) == code;
        }
    }

    /**
     * Finds the enums whose simple name another type already has in this source file, such as an own {@code CellType}.
     */
    private static final class TakenNames extends JavaIsoVisitor<Set<TypeCode>> {
        @Override
        public J.Import visitImport(J.Import anImport, Set<TypeCode> taken) {
            if (!anImport.isStatic()) {
                check(anImport.getClassName(), anImport.getTypeName(), taken);
            }
            return anImport;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, Set<TypeCode> taken) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
            if (type != null && identifier.getFieldType() == null) {
                check(identifier.getSimpleName(), type.getFullyQualifiedName(), taken);
            }
            return identifier;
        }

        private void check(String simpleName, String fullyQualifiedName, Set<TypeCode> taken) {
            for (TypeCode code : TypeCode.values()) {
                if (code.simpleName.equals(simpleName) && !code.enumType.equals(fullyQualifiedName)) {
                    taken.add(code);
                }
            }
        }
    }

    /**
     * Finds the variables that could hold a code: single {@code int} locals and the parameters of private methods.
     * Variables are told apart by owner and name, which same named locals of sibling blocks share, so
     * {@link #others} holds the keys of what cannot convert to rule out any namesake.
     */
    private static final class Candidates extends JavaIsoVisitor<Set<String>> {
        final Set<String> others = new HashSet<>();

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, Set<String> candidates) {
            boolean candidate = multiVariable.getVariables().size() == 1 &&
                                multiVariable.getTypeExpression() instanceof J.Primitive &&
                                multiVariable.getTypeExpression().getType() == JavaType.Primitive.Int &&
                                multiVariable.getVariables().get(0).getDimensionsAfterName().isEmpty() &&
                                isLocalOrPrivateParameter();
            for (J.VariableDeclarations.NamedVariable variable : multiVariable.getVariables()) {
                if (variable.getVariableType() != null) {
                    (candidate ? candidates : others).add(key(variable.getVariableType()));
                }
            }
            return super.visitVariableDeclarations(multiVariable, candidates);
        }

        private boolean isLocalOrPrivateParameter() {
            Cursor parent = getCursor().getParentTreeCursor();
            if (parent.getValue() instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = parent.getValue();
                return method.hasModifier(J.Modifier.Type.Private) && method.getMethodType() != null && !isOverloaded(method, parent);
            }
            return parent.getValue() instanceof J.Case ||
                   parent.getValue() instanceof J.Block && !(parent.getParentTreeCursor().getValue() instanceof J.ClassDeclaration);
        }

        private boolean isOverloaded(J.MethodDeclaration method, Cursor cursor) {
            J.Block body = cursor.getParentTreeCursor().getValue();
            return body.getStatements().stream().anyMatch(s -> s != method && s instanceof J.MethodDeclaration &&
                    ((J.MethodDeclaration) s).getSimpleName().equals(method.getSimpleName()));
        }
    }

    private static final class ReplaceCodes extends JavaVisitor<ExecutionContext> {
        private Conversions conversions = new Conversions();

        @Override
        public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
            return sourceFile instanceof J.CompilationUnit;
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            conversions = Conversions.of(cu);
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J visitImport(J.Import anImport, ExecutionContext ctx) {
            return anImport;
        }

        @Override
        public Space visitSpace(@Nullable Space space, Space.Location loc, ExecutionContext ctx) {
            // Javadoc lives in the whitespace, and what it references is no value to convert
            return space == null ? Space.EMPTY : space;
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
            TypeCode code = conversions.of(multiVariable.getVariables().get(0).getVariableType());
            if (code == null || mv.getTypeExpression() == null) {
                return mv;
            }
            JavaType.FullyQualified type = code.type();
            TypeTree typeExpression = conversions.isNameTaken(code) ?
                    TypeTree.build(code.enumType) :
                    new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), reference(code), type, null);
            return mv
                    .withTypeExpression(typeExpression.withPrefix(mv.getTypeExpression().getPrefix()))
                    .withVariables(ListUtils.map(mv.getVariables(), v -> v.getVariableType() == null ? v :
                            v.withVariableType(v.getVariableType().withType(type))));
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
            JavaType.Method type = convertParameters(method.getMethodType());
            return type == method.getMethodType() ? m : m.withName(m.getName().withType(type)).withMethodType(type);
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            Accessor accessor = accessorOf(method);
            if (accessor != null) {
                return replaceAccessor(m, accessor, ctx);
            }
            Sink sink = sinkOf(method);
            if (sink != null) {
                return replaceSinkArgument(method, m, sink, ctx);
            }
            JavaType.Method type = convertParameters(method.getMethodType());
            return type == method.getMethodType() ? m : withMethodType(m, type);
        }

        private static J.MethodInvocation withMethodType(J.MethodInvocation method, JavaType.@Nullable Method type) {
            return method.withName(method.getName().withType(type)).withMethodType(type);
        }

        private J replaceAccessor(J.MethodInvocation m, Accessor accessor, ExecutionContext ctx) {
            JavaType.Method type = m.getMethodType();
            if (type == null || !Conversions.isValue(getCursor())) {
                // The result is unused, so there is no `int` to migrate
                return m;
            }
            type = type.withName(accessor.enumAccessor).withReturnType(accessor.code.type());
            J.MethodInvocation renamed = withMethodType(m.withName(m.getName().withSimpleName(accessor.enumAccessor)), type);
            if (conversions.acceptsEnum(getCursor(), accessor.code)) {
                return renamed;
            }
            return template("#{any(" + accessor.code.enumType + ")}" + accessor.code.toCode, accessor.code, ctx)
                    .apply(updateCursor(renamed), renamed.getCoordinates().replace(), renamed.withPrefix(Space.EMPTY));
        }

        private J replaceSinkArgument(J.MethodInvocation original, J.MethodInvocation m, Sink sink, ExecutionContext ctx) {
            boolean isCode = conversions.isCode(original.getArguments().get(sink.argument), sink.code);
            J.MethodInvocation replaced = isCode ? m : m.withArguments(ListUtils.map(m.getArguments(),
                    (i, argument) -> i == sink.argument ? forInt(argument, sink.code, ctx) : argument));
            JavaType.Method type = replaced.getMethodType();
            if (type == null) {
                return replaced;
            }
            List<JavaType> parameters = new ArrayList<>(type.getParameterTypes());
            parameters.set(sink.argument, sink.code.type());
            return withMethodType(replaced, type.withParameterTypes(parameters));
        }

        private Expression forInt(Expression expression, TypeCode code, ExecutionContext ctx) {
            return template(reference(code) + code.fromCode, code, ctx)
                    .apply(new Cursor(getCursor(), expression), expression.getCoordinates().replace(), expression.withPrefix(Space.EMPTY));
        }

        private JavaType.@Nullable Method convertParameters(JavaType.@Nullable Method method) {
            if (method == null) {
                return null;
            }
            List<JavaType> parameters = new ArrayList<>(method.getParameterTypes());
            boolean converted = false;
            for (int i = 0; i < method.getParameterNames().size(); i++) {
                TypeCode code = conversions.variables.get(parameterKey(method, i));
                if (code != null) {
                    parameters.set(i, code.type());
                    converted = true;
                }
            }
            return converted ? method.withParameterTypes(parameters) : method;
        }

        @Override
        public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            JavaType.Variable variable = fieldAccess.getName().getFieldType();
            TypeCode code = variable == null ? null : TypeCode.ofConstant(variable);
            return code == null ? super.visitFieldAccess(fieldAccess, ctx) : replaceConstant(fieldAccess, variable, code, ctx);
        }

        @Override
        public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            JavaType.Variable variable = identifier.getFieldType();
            if (variable == null) {
                return identifier;
            }
            TypeCode constant = TypeCode.ofConstant(variable);
            if (constant != null) {
                return replaceConstant(identifier, variable, constant, ctx);
            }
            TypeCode code = conversions.of(variable);
            return code == null ? identifier : identifier.withType(code.type()).withFieldType(variable.withType(code.type()));
        }

        private J replaceConstant(Expression constant, JavaType.Variable variable, TypeCode code, ExecutionContext ctx) {
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.Case && ((J.Case) parent).getCaseLabels().contains(constant)) {
                return constant; // See visitCase
            }
            removeImports(constant, variable);
            String name = code.nameOfConstant(variable.getName());
            boolean acceptsEnum = conversions.acceptsEnum(getCursor(), code);
            if (!acceptsEnum && needsConstantExpression()) {
                return intLiteral(constant.getPrefix(), code.codeOf(name));
            }
            return template(reference(code) + '.' + name + (acceptsEnum ? "" : code.toCode), code, ctx)
                    .apply(getCursor(), constant.getCoordinates().replace());
        }

        /**
         * @return Whether the expression at the cursor may be used where only a constant expression compiles, which
         * rules out converting the enum back to its code: the value of a final variable, which a {@code case} may
         * name, or of an annotation.
         */
        private boolean needsConstantExpression() {
            Cursor parent = getCursor().getParentTreeCursor();
            if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                J.VariableDeclarations declaration = parent.getParentTreeCursor().getValue();
                return declaration.hasModifier(J.Modifier.Type.Final);
            }
            return getCursor().firstEnclosing(J.Annotation.class) != null;
        }

        private static J.Literal intLiteral(Space prefix, int value) {
            return new J.Literal(Tree.randomId(), prefix, Markers.EMPTY, value, String.valueOf(value), null, JavaType.Primitive.Int);
        }

        private void removeImports(Expression constant, JavaType.Variable variable) {
            if (variable.getOwner() instanceof JavaType.FullyQualified) {
                maybeRemoveImport((JavaType.FullyQualified) variable.getOwner());
            }
            if (constant instanceof J.FieldAccess) {
                maybeRemoveImport(TypeUtils.asFullyQualified(((J.FieldAccess) constant).getTarget().getType()));
            }
        }

        @Override
        public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
            return replaceLiteral(literal, ctx);
        }

        @Override
        public J visitUnary(J.Unary unary, ExecutionContext ctx) {
            J replaced = replaceLiteral(unary, ctx);
            return replaced == unary ? super.visitUnary(unary, ctx) : replaced;
        }

        private J replaceLiteral(Expression literal, ExecutionContext ctx) {
            Integer value = intValue(literal);
            TypeCode code = value == null ? null : conversions.expected(getCursor());
            String name = code == null ? null : code.nameOf(value);
            return name == null ? literal : template(reference(code) + '.' + name, code, ctx).apply(getCursor(), literal.getCoordinates().replace());
        }

        @Override
        public J visitSwitch(J.Switch switch_, ExecutionContext ctx) {
            J.Switch s = (J.Switch) super.visitSwitch(switch_, ctx);
            TypeCode code = conversions.switchCode(switch_);
            if (code == null || conversions.codeOf(switch_.getSelector().getTree()) != null) {
                return s;
            }
            return s.withSelector(s.getSelector().withTree(forInt(s.getSelector().getTree(), code, ctx)));
        }

        @Override
        public J visitSwitchExpression(J.SwitchExpression switch_, ExecutionContext ctx) {
            J.SwitchExpression s = (J.SwitchExpression) super.visitSwitchExpression(switch_, ctx);
            TypeCode code = conversions.switchCode(switch_);
            if (code == null || conversions.codeOf(switch_.getSelector().getTree()) != null) {
                return s;
            }
            return s.withSelector(s.getSelector().withTree(forInt(s.getSelector().getTree(), code, ctx)));
        }

        @Override
        public J visitCase(J.Case case_, ExecutionContext ctx) {
            J.Case c = (J.Case) super.visitCase(case_, ctx);
            TypeCode code = conversions.switchCode(getCursor().getParentTreeCursor().getParentTreeCursor().getValue());
            return c.withCaseLabels(ListUtils.map(c.getCaseLabels(), label -> replaceLabel(label, code)));
        }

        private J replaceLabel(J label, @Nullable TypeCode switchCode) {
            if (!(label instanceof Expression) || isDefault(label)) {
                return label;
            }
            Expression expression = (Expression) label;
            JavaType.Variable variable = variableOf(expression);
            TypeCode constant = variable == null ? null : TypeCode.ofConstant(variable);
            if (constant != null) {
                removeImports(expression, variable);
            }
            if (switchCode != null) {
                Integer value = intValue(label);
                String name = constant != null ? constant.nameOfConstant(variable.getName()) : value == null ? null : switchCode.nameOf(value);
                return name == null ? label : enumLabel(expression, name, switchCode);
            }
            if (constant == null) {
                return label;
            }
            // A label must be a constant expression, which rules out converting the enum back
            return intLiteral(expression.getPrefix(), constant.codeOf(constant.nameOfConstant(variable.getName())));
        }

        private J.Identifier enumLabel(Expression label, String name, TypeCode code) {
            JavaType.FullyQualified type = code.type();
            return new J.Identifier(Tree.randomId(), label.getPrefix(), Markers.EMPTY, emptyList(), name, type,
                    new JavaType.Variable(null, Flag.Public.getBitMask() | Flag.Static.getBitMask() | Flag.Final.getBitMask(), name, type, type, emptyList()));
        }

        private JavaTemplate template(String code, TypeCode typeCode, ExecutionContext ctx) {
            return JavaTemplate.builder(code)
                    .imports(typeCode.enumType)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "poi"))
                    .build();
        }

        private String reference(TypeCode code) {
            if (conversions.isNameTaken(code)) {
                return code.enumType;
            }
            maybeAddImport(code.enumType);
            return code.simpleName;
        }
    }
}

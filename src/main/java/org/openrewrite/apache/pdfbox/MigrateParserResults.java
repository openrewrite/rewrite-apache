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
package org.openrewrite.apache.pdfbox;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateParserResults extends Recipe {

    private static final String PDF_PARSER = "org.apache.pdfbox.pdfparser.PDFParser";
    private static final String STREAM_PARSER = "org.apache.pdfbox.pdfparser.PDFStreamParser";
    private static final MethodMatcher PDF_PARSER_PARSE = new MethodMatcher(PDF_PARSER + " parse()");
    private static final MethodMatcher STREAM_PARSER_PARSE = new MethodMatcher(STREAM_PARSER + " parse()");
    private static final MethodMatcher GET_PD_DOCUMENT = new MethodMatcher(PDF_PARSER + " getPDDocument()");
    private static final MethodMatcher GET_DOCUMENT = new MethodMatcher("org.apache.pdfbox.pdfparser.COSParser getDocument()", true);
    private static final MethodMatcher GET_TOKENS = new MethodMatcher(STREAM_PARSER + " getTokens()");

    String displayName = "Use the result of `PDFParser.parse()` and `PDFStreamParser.parse()`";

    String description = "In PDFBox 3.0 `PDFParser.parse()` returns the `PDDocument` and `PDFStreamParser.parse()` returns the tokens, " +
            "and the `getPDDocument()`, `getDocument()` and `getTokens()` methods that returned them after a parse are gone. " +
            "Where a statement calls `parse()` on a local parser and the statements after it use the parser only to get the result, " +
            "this parses where the next statement gets the result, as in `PDDocument document = parser.parse()`, " +
            "or else declares a variable for the result where the code parsed, and uses the variable where the result is got.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(PDF_PARSER_PARSE), new UsesMethod<>(STREAM_PARSER_PARSE)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = (J.Block) super.visitBlock(block, ctx);
                        for (int i = 0; i < b.getStatements().size(); i++) {
                            List<Statement> statements = b.getStatements();
                            JavaType.Variable parser = parsedLocalParser(statements.get(i));
                            if (parser == null) {
                                continue;
                            }
                            List<Integer> using = new ArrayList<>();
                            int uses = 0;
                            int gets = 0;
                            for (int later = i + 1; later < statements.size(); later++) {
                                int count = references(statements.get(later), parser).size();
                                if (count > 0) {
                                    using.add(later);
                                    uses += count;
                                    gets += getters(statements.get(later), parser).size();
                                }
                            }
                            if (uses == 0 || uses != gets) {
                                continue;
                            }
                            Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                            int parseIndex = i;
                            int first = using.get(0);
                            J.@Nullable Identifier assigned = uses == 1 ? null : assignedFrom(statements.get(first), parser);
                            if (first == i + 1 && (uses == 1 || assigned != null) &&
                                evaluatedOnce(statements.get(first), getters(statements.get(first), parser).get(0))) {
                                // Parse where the result is got, right after, as in `PDDocument document = parser.parse()`
                                Statement withParse = (Statement) new ParseInsteadOfGet(parser)
                                        .visitNonNull(statements.get(first), ctx, blockCursor);
                                b = b.withStatements(ListUtils.map(b.getStatements(), (index, s) ->
                                        index == parseIndex ? null :
                                        index == first ? withParse :
                                        index > first && assigned != null ? (Statement) new UseVariable(parser, assigned).visitNonNull(s, ctx, blockCursor) :
                                        s));
                                // The statement after the parse now has its index
                                i--;
                            } else {
                                // Keep the result in a variable of its own, parsing where the code parsed
                                J.MethodInvocation parse = (J.MethodInvocation) statements.get(i);
                                boolean pdf = PDF_PARSER_PARSE.matches(parse);
                                Cursor parseCursor = new Cursor(blockCursor, parse);
                                String name = unusedName(pdf ? "document" : "tokens", parseCursor, b);
                                PdfBox3Template template = new PdfBox3Template(parseCursor);
                                if (pdf) {
                                    template.type("org.apache.pdfbox.pdmodel.PDDocument");
                                } else {
                                    template.type("java.util.List").code("<Object>");
                                }
                                J.VariableDeclarations declaration = template.code(" " + name + " = ")
                                        .expression(requireNonNull(parse.getSelect()), pdf ? PDF_PARSER : STREAM_PARSER)
                                        .code(".parse();")
                                        .apply(this, parseCursor, parse.getCoordinates().replace(), ctx);
                                J.Identifier variable = declaration.getVariables().get(0).getName();
                                b = b.withStatements(ListUtils.map(b.getStatements(), (index, s) ->
                                        index == parseIndex ? declaration :
                                        index > parseIndex ? (Statement) new UseVariable(parser, variable).visitNonNull(s, ctx, blockCursor) :
                                        s));
                            }
                        }
                        return b;
                    }

                    private JavaType.@Nullable Variable parsedLocalParser(Statement statement) {
                        if (statement instanceof J.MethodInvocation) {
                            J.MethodInvocation m = (J.MethodInvocation) statement;
                            if ((PDF_PARSER_PARSE.matches(m) || STREAM_PARSER_PARSE.matches(m)) &&
                                m.getSelect() instanceof J.Identifier) {
                                JavaType.Variable variable = ((J.Identifier) m.getSelect()).getFieldType();
                                if (variable != null && variable.getOwner() instanceof JavaType.Method) {
                                    return variable;
                                }
                            }
                        }
                        return null;
                    }

                    /**
                     * The local variable that the statement declares with the parsed document or tokens, as
                     * {@code PDDocument document = parser.getPDDocument();}, when it is one.
                     */
                    private J.@Nullable Identifier assignedFrom(Statement statement, JavaType.Variable parser) {
                        if (statement instanceof J.VariableDeclarations &&
                            ((J.VariableDeclarations) statement).getVariables().size() == 1) {
                            J.VariableDeclarations.NamedVariable variable = ((J.VariableDeclarations) statement).getVariables().get(0);
                            Expression initializer = variable.getInitializer();
                            if (initializer instanceof J.MethodInvocation &&
                                (GET_PD_DOCUMENT.matches(initializer) || GET_TOKENS.matches(initializer)) &&
                                isParser(((J.MethodInvocation) initializer).getSelect(), parser) &&
                                TypeUtils.isOfType(variable.getType(), initializer.getType())) {
                                return variable.getName();
                            }
                        }
                        return null;
                    }
                });
    }

    /**
     * A name for the result that no variable in scope at the parse, or declared anywhere in the block, has.
     */
    private static String unusedName(String base, Cursor scope, J.Block block) {
        Set<String> taken = new HashSet<>(VariableNameUtils.findNamesInScope(scope));
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> names) {
                names.add(variable.getSimpleName());
                return super.visitVariable(variable, names);
            }
        }.visit(block, taken);
        String name = base;
        for (int n = 1; taken.contains(name); n++) {
            name = base + n;
        }
        return name;
    }

    /**
     * Whether running the statement evaluates the expression exactly once, so that the expression can
     * parse where the statement before it parsed. It is not where a condition, a loop, a lambda or a class
     * body decides, or where a {@code catch} or {@code finally} would see what the parse throws.
     */
    private static boolean evaluatedOnce(Statement statement, J expression) {
        Cursor path = new JavaIsoVisitor<AtomicReference<@Nullable Cursor>>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, AtomicReference<@Nullable Cursor> found) {
                if (tree == expression) {
                    found.set(new Cursor(getCursor(), tree));
                    return (J) tree;
                }
                return found.get() == null ? super.visit(tree, found) : (J) tree;
            }
        }.reduce(statement, new AtomicReference<@Nullable Cursor>()).get();
        if (path == null) {
            return false;
        }
        Object child = path.getValue();
        for (Cursor c = path.getParentTreeCursor(); ; c = c.getParentTreeCursor()) {
            Object parent = c.getValue();
            if (parent instanceof J.If && ((J.If) parent).getIfCondition() != child ||
                parent instanceof J.Ternary && ((J.Ternary) parent).getCondition() != child ||
                parent instanceof J.Binary && ((J.Binary) parent).getLeft() != child &&
                (((J.Binary) parent).getOperator() == J.Binary.Type.And || ((J.Binary) parent).getOperator() == J.Binary.Type.Or) ||
                parent instanceof J.Switch && ((J.Switch) parent).getSelector() != child ||
                parent instanceof J.SwitchExpression && ((J.SwitchExpression) parent).getSelector() != child ||
                parent instanceof J.Try && (((J.Try) parent).getResources() == null || !((J.Try) parent).getCatches().isEmpty() ||
                                            ((J.Try) parent).getFinally() != null || ((J.Try) parent).getBody() == child) ||
                parent instanceof Loop || parent instanceof J.Lambda || parent instanceof J.Assert ||
                parent instanceof J.ClassDeclaration || parent instanceof J.MethodDeclaration ||
                parent instanceof J.NewClass && ((J.NewClass) parent).getBody() == child) {
                return false;
            }
            if (parent == statement) {
                return true;
            }
            child = parent;
        }
    }

    private static List<J.Identifier> references(J tree, JavaType.Variable variable) {
        List<J.Identifier> uses = new ArrayList<>();
        new JavaIsoVisitor<List<J.Identifier>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, List<J.Identifier> found) {
                if (variable.equals(identifier.getFieldType())) {
                    found.add(identifier);
                }
                return identifier;
            }
        }.visit(tree, uses);
        return uses;
    }

    /**
     * The calls in the tree that get the result of a parse from the parser.
     */
    private static List<J.MethodInvocation> getters(J tree, JavaType.Variable parser) {
        List<J.MethodInvocation> getters = new ArrayList<>();
        new JavaIsoVisitor<List<J.MethodInvocation>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<J.MethodInvocation> found) {
                if (isParser(method.getSelect(), parser) &&
                    (GET_PD_DOCUMENT.matches(method) || GET_DOCUMENT.matches(method) || GET_TOKENS.matches(method))) {
                    found.add(method);
                }
                return super.visitMethodInvocation(method, found);
            }
        }.visit(tree, getters);
        return getters;
    }

    private static boolean isParser(@Nullable Expression select, JavaType.Variable parser) {
        return select instanceof J.Identifier && parser.equals(((J.Identifier) select).getFieldType());
    }

    private static class ParseInsteadOfGet extends JavaVisitor<ExecutionContext> {
        private final JavaType.Variable parser;

        ParseInsteadOfGet(JavaType.Variable parser) {
            this.parser = parser;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if (!(m.getSelect() instanceof J.Identifier) || !parser.equals(((J.Identifier) m.getSelect()).getFieldType())) {
                return m;
            }
            PdfBox3Template template = new PdfBox3Template(getCursor());
            if (GET_PD_DOCUMENT.matches(m)) {
                template.expression(m.getSelect(), PDF_PARSER).code(".parse()");
            } else if (GET_DOCUMENT.matches(m)) {
                template.expression(m.getSelect(), PDF_PARSER).code(".parse().getDocument()");
            } else if (GET_TOKENS.matches(m)) {
                template.expression(m.getSelect(), STREAM_PARSER).code(".parse()");
            } else {
                return m;
            }
            return template.apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
        }
    }

    /**
     * Uses the variable that holds the parsed document or tokens where the parser was asked for them.
     */
    private static class UseVariable extends JavaVisitor<ExecutionContext> {
        private final JavaType.Variable parser;
        private final J.Identifier variable;

        UseVariable(JavaType.Variable parser, J.Identifier variable) {
            this.parser = parser;
            this.variable = variable;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if (!isParser(m.getSelect(), parser)) {
                return m;
            }
            J.Identifier result = variable.withId(Tree.randomId()).withPrefix(Space.EMPTY);
            if (GET_PD_DOCUMENT.matches(m) || GET_TOKENS.matches(m)) {
                return result.withPrefix(m.getPrefix());
            } else if (GET_DOCUMENT.matches(m)) {
                return new PdfBox3Template(getCursor())
                        .expression(result, "org.apache.pdfbox.pdmodel.PDDocument").code(".getDocument()")
                        .apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
            }
            return m;
        }
    }
}

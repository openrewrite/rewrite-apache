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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.RANDOM_ACCESS_READ_BUFFER;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.RANDOM_ACCESS_READ_BUFFERED_FILE;
import static org.openrewrite.apache.pdfbox.PdfBox3Template.ioExceptionHandled;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateRandomAccessReads extends Recipe {

    private static final String IO = "org.apache.pdfbox.io.";
    private static final MethodMatcher NEW_RANDOM_ACCESS_BUFFER = new MethodMatcher(IO + "RandomAccessBuffer <constructor>()");
    private static final MethodMatcher NEW_RANDOM_ACCESS_BUFFER_FROM = new MethodMatcher(IO + "RandomAccessBuffer <constructor>(*)");
    private static final MethodMatcher NEW_BUFFERED_FILE_INPUT_STREAM = new MethodMatcher(IO + "RandomAccessBufferedFileInputStream <constructor>(*)");
    private static final MethodMatcher NEW_RANDOM_ACCESS_FILE = new MethodMatcher(IO + "RandomAccessFile <constructor>(java.io.File, java.lang.String)");
    private static final MethodMatcher NEW_PDF_PARSER_WITH_SCRATCH_FILE = new MethodMatcher("org.apache.pdfbox.pdfparser.PDFParser <constructor>(.., " + IO + "ScratchFile)");
    private static final MethodMatcher FONT_PARSER_PARSE = new MethodMatcher("org.apache.fontbox.ttf.TTFParser parse(*)", true);
    private static final MethodMatcher ADD_SOURCE_STREAM = new MethodMatcher("org.apache.pdfbox.multipdf.PDFMergerUtility addSource(java.io.InputStream)");
    private static final MethodMatcher NEW_BYTE_ARRAY_INPUT_STREAM = new MethodMatcher("java.io.ByteArrayInputStream <constructor>(byte[])");
    private static final List<String> REPLACED_TYPES = Arrays.asList(IO + "RandomAccessBuffer", IO + "RandomAccessBufferedFileInputStream",
            IO + "RandomAccessFile", IO + "ScratchFile", IO + "MemoryUsageSetting", "java.io.ByteArrayInputStream");

    String displayName = "Read PDFs and fonts through PDFBox 3 `RandomAccessRead` implementations";

    String description = "PDFBox 3.0 removed `RandomAccessBuffer`, `RandomAccessBufferedFileInputStream` and `RandomAccessFile`, " +
            "and reads PDFs and fonts from a `RandomAccessRead` only. This creates a `RandomAccessReadBuffer` " +
            "for data in memory or in a stream, a `RandomAccessReadWriteBuffer` for an empty buffer, and a " +
            "`RandomAccessReadBufferedFile` for a file opened to be read, and declares a buffer created empty as a " +
            "`RandomAccessReadWriteBuffer`, so that it can still be written to. It drops the `ScratchFile` argument of the " +
            "`PDFParser` constructors, since PDFBox 3.0 no longer uses a scratch file to read, and passes a " +
            "`RandomAccessRead` to `TTFParser.parse`, `OTFParser.parse` and `PDFMergerUtility.addSource` in place of a file or stream. " +
            "Where reading a stream into a buffer could throw an `IOException` the code doesn't handle, the call is left as it is.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(NEW_RANDOM_ACCESS_BUFFER_FROM),
                        new UsesMethod<>(NEW_RANDOM_ACCESS_BUFFER),
                        new UsesMethod<>(NEW_BUFFERED_FILE_INPUT_STREAM),
                        new UsesMethod<>(NEW_RANDOM_ACCESS_FILE),
                        new UsesMethod<>(NEW_PDF_PARSER_WITH_SCRATCH_FILE),
                        new UsesMethod<>(FONT_PARSER_PARSE),
                        new UsesMethod<>(ADD_SOURCE_STREAM)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        J c = super.visitCompilationUnit(cu, ctx);
                        if (c == cu) {
                            return c;
                        }
                        // Remove the imports the changes left unused now, before the imports of the PDFBox 3 types are
                        // added, so that those don't fold into a star import with ones that are about to go
                        for (String type : REPLACED_TYPES) {
                            c = new RemoveImport<ExecutionContext>(type).visitNonNull(c, ctx);
                        }
                        return c;
                    }

                    @Override
                    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations v = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                        // A buffer created empty is written to, which a RandomAccessReadBuffer can't be
                        if (v.getTypeExpression() == null || !TypeUtils.isOfClassType(v.getTypeExpression().getType(), IO + "RandomAccessBuffer") ||
                            !v.getVariables().stream().allMatch(variable -> variable.getInitializer() instanceof J.NewClass &&
                                    TypeUtils.isOfClassType(variable.getInitializer().getType(), IO + "RandomAccessReadWriteBuffer"))) {
                            return v;
                        }
                        JavaType.ShallowClass readWrite = JavaType.ShallowClass.build(IO + "RandomAccessReadWriteBuffer");
                        maybeAddImport(readWrite.getFullyQualifiedName());
                        return v.withTypeExpression(new J.Identifier(Tree.randomId(), v.getTypeExpression().getPrefix(), Markers.EMPTY,
                                emptyList(), readWrite.getClassName(), readWrite, null));
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                        JavaType.Method constructor = n.getConstructorType();
                        if (constructor == null) {
                            return n;
                        }
                        List<Expression> args = n.getArguments();
                        PdfBox3Template template = new PdfBox3Template(getCursor()).code("new ");
                        if (NEW_RANDOM_ACCESS_BUFFER.matches(n)) {
                            template.type(IO + "RandomAccessReadWriteBuffer").code("()");
                        } else if (NEW_RANDOM_ACCESS_BUFFER_FROM.matches(n) || NEW_BUFFERED_FILE_INPUT_STREAM.matches(n)) {
                            JavaType parameterType = constructor.getParameterTypes().get(0);
                            boolean inMemory = parameterType instanceof JavaType.Array ||
                                               "java.io.InputStream".equals(parameterType.toString());
                            template.type(inMemory ? RANDOM_ACCESS_READ_BUFFER : RANDOM_ACCESS_READ_BUFFERED_FILE)
                                    .code("(").expression(args.get(0), parameterType).code(")");
                        } else if (NEW_RANDOM_ACCESS_FILE.matches(n) && isReadMode(args.get(1)) &&
                                   // RandomAccessFile threw FileNotFoundException, RandomAccessReadBufferedFile throws IOException
                                   ioExceptionHandled(getCursor())) {
                            template.type(RANDOM_ACCESS_READ_BUFFERED_FILE)
                                    .code("(").expression(args.get(0), "java.io.File").code(")");
                        } else if (NEW_PDF_PARSER_WITH_SCRATCH_FILE.matches(n)) {
                            template.type("org.apache.pdfbox.pdfparser.PDFParser").code("(");
                            List<JavaType> parameterTypes = constructor.getParameterTypes();
                            for (int i = 0; i < args.size() - 1; i++) {
                                template.code(i == 0 ? "" : ", ").expression(args.get(i), parameterTypes.get(i));
                            }
                            template.code(")");
                        } else {
                            return n;
                        }
                        return template.apply(this, updateCursor(n), n.getCoordinates().replace(), ctx);
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        JavaType.Method methodType = m.getMethodType();
                        if (m.getSelect() == null || methodType == null) {
                            return m;
                        }
                        Expression arg = m.getArguments().get(0);
                        String declaringType = methodType.getDeclaringType().getFullyQualifiedName();
                        PdfBox3Template template = new PdfBox3Template(getCursor())
                                .target(m.getSelect(), declaringType)
                                .code("." + m.getSimpleName() + "(new ");
                        if (FONT_PARSER_PARSE.matches(m)) {
                            JavaType parameterType = methodType.getParameterTypes().get(0);
                            boolean stream = "java.io.InputStream".equals(parameterType.toString());
                            if (!stream && !"java.io.File".equals(parameterType.toString()) &&
                                !"java.lang.String".equals(parameterType.toString())) {
                                return m;
                            }
                            template.type(stream ? RANDOM_ACCESS_READ_BUFFER : RANDOM_ACCESS_READ_BUFFERED_FILE)
                                    .code("(").expression(arg, parameterType).code("))");
                        } else if (ADD_SOURCE_STREAM.matches(m)) {
                            if (NEW_BYTE_ARRAY_INPUT_STREAM.matches(arg)) {
                                // Reading bytes already in memory throws no IOException
                                template.type(RANDOM_ACCESS_READ_BUFFER).code("(")
                                        .expression(((J.NewClass) arg).getArguments().get(0), "byte[]").code("))");
                            } else if (ioExceptionHandled(getCursor())) {
                                template.type(RANDOM_ACCESS_READ_BUFFER).code("(")
                                        .expression(arg, "java.io.InputStream").code("))");
                            } else {
                                return m;
                            }
                        } else {
                            return m;
                        }
                        return template.apply(this, updateCursor(m), m.getCoordinates().replace(), ctx);
                    }

                    private boolean isReadMode(Expression mode) {
                        return mode instanceof J.Literal && "r".equals(((J.Literal) mode).getValue());
                    }
                });
    }
}

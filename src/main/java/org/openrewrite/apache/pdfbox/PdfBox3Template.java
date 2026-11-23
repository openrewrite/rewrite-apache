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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link JavaTemplate} piece by piece against the PDFBox 3 API, collecting the arguments and imports it
 * needs, so that a recipe can translate each argument of a PDFBox 2 call on its own.
 */
final class PdfBox3Template {
    static final String MEMORY_USAGE_SETTING = "org.apache.pdfbox.io.MemoryUsageSetting";
    static final String IO_UTILS = "org.apache.pdfbox.io.IOUtils";
    static final String RANDOM_ACCESS_READ_BUFFER = "org.apache.pdfbox.io.RandomAccessReadBuffer";
    static final String RANDOM_ACCESS_READ_BUFFERED_FILE = "org.apache.pdfbox.io.RandomAccessReadBufferedFile";

    private static final MethodMatcher SETUP_MAIN_MEMORY_ONLY = new MethodMatcher(MEMORY_USAGE_SETTING + " setupMainMemoryOnly()");
    private static final MethodMatcher SETUP_TEMP_FILE_ONLY = new MethodMatcher(MEMORY_USAGE_SETTING + " setupTempFileOnly()");

    private final StringBuilder code = new StringBuilder();
    private final List<Object> parameters = new ArrayList<>();
    private final Set<String> imports = new LinkedHashSet<>();
    private final Cursor cursor;

    PdfBox3Template(Cursor cursor) {
        this.cursor = cursor;
    }

    static JavaParser.Builder<?, ?> parser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx, "pdfbox-3", "pdfbox-io-3", "fontbox-3");
    }

    PdfBox3Template code(String text) {
        code.append(text);
        return this;
    }

    /**
     * Appends a type by its simple name and imports it, or by its fully qualified name where the source file
     * already uses another type of that simple name.
     */
    PdfBox3Template type(String fullyQualifiedName) {
        String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
        if (otherTypeNamed(simpleName, fullyQualifiedName)) {
            code.append(fullyQualifiedName);
        } else {
            code.append(simpleName);
            imports.add(fullyQualifiedName);
        }
        return this;
    }

    PdfBox3Template expression(Expression expression, JavaType parameterType) {
        return expression(expression, TypeUtils.toString(parameterType));
    }

    PdfBox3Template expression(Expression expression, String parameterType) {
        code.append("#{any(").append(parameterType).append(")}");
        parameters.add(expression);
        return this;
    }

    /**
     * Appends an expression as the target of a member access, in parentheses where its operators would otherwise
     * bind more loosely than the access.
     */
    PdfBox3Template target(Expression expression, String type) {
        boolean parenthesize = !(expression instanceof J.Identifier || expression instanceof J.FieldAccess ||
                                 expression instanceof J.MethodInvocation || expression instanceof J.NewClass ||
                                 expression instanceof J.Parentheses || expression instanceof J.Literal ||
                                 expression instanceof J.ArrayAccess);
        if (parenthesize) {
            code.append('(');
        }
        expression(expression, type);
        if (parenthesize) {
            code.append(')');
        }
        return this;
    }

    /**
     * Appends an argument for a {@code float} parameter where PDFBox 2 took a {@code double}, as a {@code float}
     * literal or with a cast, as the removed PDFBox 2 method converted it.
     */
    PdfBox3Template asFloat(Expression expression) {
        JavaType type = expression.getType();
        if (type != JavaType.Primitive.Double && !TypeUtils.isOfClassType(type, "java.lang.Double")) {
            return expression(expression, type == null || type instanceof JavaType.Unknown ? "float" : TypeUtils.toString(type));
        }
        String literal = floatLiteral(expression);
        if (literal != null) {
            return code(literal);
        } else if (type != JavaType.Primitive.Double) {
            // A cast can't unbox and narrow at once
            return operand(expression, "java.lang.Double").code(".floatValue()");
        }
        code.append("(float) ");
        return operand(expression, "double");
    }

    /**
     * Appends {@code value / 255f}, the fraction of 255 that a PDFBox 2 color component in 0..255 stood for.
     */
    PdfBox3Template per255(Expression expression) {
        return operand(expression, "int").code(" / 255f");
    }

    private PdfBox3Template operand(Expression expression, String type) {
        boolean parenthesize = expression instanceof J.Binary || expression instanceof J.Ternary ||
                               expression instanceof J.Assignment || expression instanceof J.AssignmentOperation ||
                               expression instanceof J.InstanceOf || expression instanceof J.TypeCast ||
                               expression instanceof J.Unary;
        if (parenthesize) {
            code.append('(');
        }
        expression(expression, type);
        if (parenthesize) {
            code.append(')');
        }
        return this;
    }

    private static @Nullable String floatLiteral(Expression expression) {
        String sign = "";
        Expression e = expression;
        if (e instanceof J.Unary && ((J.Unary) e).getOperator() == J.Unary.Type.Negative) {
            sign = "-";
            e = ((J.Unary) e).getExpression();
        }
        if (e instanceof J.Literal && ((J.Literal) e).getValueSource() != null &&
            e.getComments().isEmpty() && ((J.Literal) e).getType() == JavaType.Primitive.Double) {
            String source = ((J.Literal) e).getValueSource();
            if (source.endsWith("d") || source.endsWith("D")) {
                source = source.substring(0, source.length() - 1);
            }
            return sign + source + "f";
        }
        return null;
    }

    /**
     * Appends the PDFBox 3 stream cache function that does what a PDFBox 2 {@code MemoryUsageSetting} did.
     * PDFBox 3 keeps {@code MemoryUsageSetting}, whose {@code streamCache} field creates the same {@code ScratchFile}
     * PDFBox 2 created from it. The two settings {@code IOUtils} has a factory for use that factory instead.
     */
    PdfBox3Template streamCache(Expression memoryUsageSetting) {
        if (SETUP_MAIN_MEMORY_ONLY.matches(memoryUsageSetting)) {
            return type(IO_UTILS).code(".createMemoryOnlyStreamCache()");
        }
        if (SETUP_TEMP_FILE_ONLY.matches(memoryUsageSetting)) {
            return type(IO_UTILS).code(".createTempFileOnlyStreamCache()");
        }
        return target(memoryUsageSetting, MEMORY_USAGE_SETTING).code(".streamCache");
    }

    static boolean isNullLiteral(Expression expression) {
        Expression e = expression.unwrap();
        if (e instanceof J.TypeCast) {
            e = ((J.TypeCast) e).getExpression().unwrap();
        }
        return e instanceof J.Literal && ((J.Literal) e).getValue() == null;
    }

    /**
     * Whether code at the cursor may throw an {@code IOException} without a compile error, because a surrounding
     * {@code try} catches it or the method declares it.
     */
    static boolean ioExceptionHandled(Cursor cursor) {
        Object previous = cursor.getValue();
        for (Cursor c = cursor.getParentTreeCursor(); ; c = c.getParentTreeCursor()) {
            Object value = c.getValue();
            if (value instanceof J.Try) {
                J.Try aTry = (J.Try) value;
                if (previous == aTry.getBody() || (aTry.getResources() != null && aTry.getResources().contains(previous))) {
                    for (J.Try.Catch aCatch : aTry.getCatches()) {
                        if (catchesIOException(aCatch.getParameter().getTree().getType())) {
                            return true;
                        }
                    }
                }
            } else if (value instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) value;
                if (method.getThrows() != null) {
                    for (NameTree thrown : method.getThrows()) {
                        if (catchesIOException(thrown.getType())) {
                            return true;
                        }
                    }
                }
                return false;
            } else if (value instanceof J.NewClass) {
                if (previous == ((J.NewClass) value).getBody()) {
                    return false;
                }
            } else if (value instanceof J.Lambda || value instanceof J.ClassDeclaration ||
                       value instanceof J.CompilationUnit) {
                return false;
            }
            if (c.isRoot()) {
                return false;
            }
            previous = value;
        }
    }

    private static boolean catchesIOException(@Nullable JavaType type) {
        if (type instanceof JavaType.MultiCatch) {
            for (JavaType alternative : ((JavaType.MultiCatch) type).getThrowableTypes()) {
                if (catchesIOException(alternative)) {
                    return true;
                }
            }
            return false;
        }
        return TypeUtils.isOfClassType(type, "java.io.IOException") ||
               TypeUtils.isOfClassType(type, "java.lang.Exception") ||
               TypeUtils.isOfClassType(type, "java.lang.Throwable");
    }

    <J2 extends J> J2 apply(JavaVisitor<ExecutionContext> visitor, Cursor at, JavaCoordinates coordinates, ExecutionContext ctx) {
        for (String anImport : imports) {
            visitor.maybeAddImport(anImport, null, false);
        }
        return JavaTemplate.builder(code.toString())
                .imports(imports.toArray(new String[0]))
                .javaParser(parser(ctx))
                .build()
                .apply(at, coordinates, parameters.toArray());
    }

    private boolean otherTypeNamed(String simpleName, String fullyQualifiedName) {
        J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (cu == null) {
            return false;
        }
        for (J.Import anImport : cu.getImports()) {
            if (!anImport.isStatic() && simpleName.equals(anImport.getClassName()) &&
                !fullyQualifiedName.equals(anImport.getTypeName())) {
                return true;
            }
        }
        for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (fq != null && simpleName.equals(fq.getClassName()) &&
                !fullyQualifiedName.equals(fq.getFullyQualifiedName())) {
                return true;
            }
        }
        for (J.ClassDeclaration classDeclaration : cu.getClasses()) {
            if (simpleName.equals(classDeclaration.getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}

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
package org.openrewrite.codehaus.plexus;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MigrateDefaultSecDispatcherConstructor extends Recipe {

    private static final String DEFAULT_SEC_DISPATCHER = "org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher";
    private static final String PLEXUS_CIPHER = "org.sonatype.plexus.components.cipher.PlexusCipher";
    private static final String DEFAULT_PLEXUS_CIPHER = "org.sonatype.plexus.components.cipher.DefaultPlexusCipher";
    private static final MethodMatcher DEFAULT_PLEXUS_CIPHER_CONSTRUCTOR = new MethodMatcher(DEFAULT_PLEXUS_CIPHER + " <constructor>()");

    @Getter
    final String displayName = "Pass the cipher to the constructor of `DefaultSecDispatcher`";

    @Getter
    final String description = "`org.codehaus.plexus:plexus-sec-dispatcher` 2.0 has no constructor of `DefaultSecDispatcher` " +
            "without arguments, and its `_cipher` field is final. This passes the cipher to the constructor that takes " +
            "one instead: the cipher that an anonymous or named subclass assigned to `_cipher`, or else a new " +
            "`DefaultPlexusCipher`, which is what the dispatcher that Maven configures uses. A subclass that assigns " +
            "`_decryptors`, or assigns `_cipher` a value that can't be computed before the constructor runs, is left " +
            "for a person to change.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DEFAULT_SEC_DISPATCHER, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (!TypeUtils.isOfClassType(nc.getClazz() == null ? null : nc.getClazz().getType(), DEFAULT_SEC_DISPATCHER) ||
                    !hasNoArguments(nc.getArguments())) {
                    return nc;
                }
                J.Block body = nc.getBody();
                CipherAssignment assignment = null;
                if (body != null) {
                    List<Statement> statements = body.getStatements();
                    for (int i = 0; i < statements.size(); i++) {
                        Statement statement = statements.get(i);
                        if (statement instanceof J.Block && !((J.Block) statement).isStatic()) {
                            CipherAssignment found = CipherAssignment.find((J.Block) statement, null);
                            if (found == CipherAssignment.UNMOVABLE || found != null && assignment != null) {
                                return nc;
                            }
                            if (found != null) {
                                assignment = found.inInitializer(i);
                            }
                        }
                    }
                }

                J.NewClass replaced = assignment == null ?
                        JavaTemplate.builder("new DefaultSecDispatcher(new DefaultPlexusCipher())")
                                .imports(DEFAULT_SEC_DISPATCHER, DEFAULT_PLEXUS_CIPHER)
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                                .build()
                                .apply(getCursor(), nc.getCoordinates().replace()) :
                        JavaTemplate.builder("new DefaultSecDispatcher(#{any(" + PLEXUS_CIPHER + ")})")
                                .imports(DEFAULT_SEC_DISPATCHER)
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                                .build()
                                .apply(getCursor(), nc.getCoordinates().replace(), assignment.cipher.withPrefix(Space.EMPTY));
                if (assignment == null) {
                    maybeAddImport(DEFAULT_PLEXUS_CIPHER);
                }
                if (body != null) {
                    // keep the anonymous class, whose constructor now takes the cipher
                    JavaType.Method templatedType = replaced.getMethodType();
                    JavaType.Method anonymousType = nc.getMethodType();
                    J.NewClass anonymous = nc.withArguments(replaced.getArguments());
                    if (templatedType != null && anonymousType != null) {
                        anonymous = anonymous.withMethodType(anonymousType
                                .withParameterTypes(templatedType.getParameterTypes())
                                .withParameterNames(templatedType.getParameterNames()));
                    }
                    J.Block newBody = body;
                    if (assignment != null) {
                        CipherAssignment a = assignment;
                        newBody = newBody.withStatements(ListUtils.map(newBody.getStatements(), (i, s) ->
                                i == a.initializerIndex ? a.removeFrom((J.Block) s) : s));
                        // an initializer left empty goes, and so does a body left empty
                        newBody = newBody.withStatements(ListUtils.map(newBody.getStatements(), s ->
                                s instanceof J.Block && ((J.Block) s).getStatements().isEmpty() &&
                                ((J.Block) s).getEnd().getComments().isEmpty() ? null : s));
                        doAfterVisit(new RemovePlexusCipherExceptionCatch().getVisitor());
                    }
                    if (!newBody.getStatements().isEmpty() || !newBody.getEnd().getComments().isEmpty()) {
                        return anonymous.withBody(newBody);
                    }
                }
                return replaced.withPrefix(nc.getPrefix());
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (!md.isConstructor() || md.getBody() == null) {
                    return md;
                }
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosing == null || enclosing.getExtends() == null ||
                    !TypeUtils.isOfClassType(enclosing.getExtends().getType(), DEFAULT_SEC_DISPATCHER)) {
                    return md;
                }
                List<Statement> statements = md.getBody().getStatements();
                J.MethodInvocation superCall = null;
                if (!statements.isEmpty() && statements.get(0) instanceof J.MethodInvocation) {
                    J.MethodInvocation first = (J.MethodInvocation) statements.get(0);
                    if ("this".equals(first.getSimpleName()) ||
                        "super".equals(first.getSimpleName()) && !hasNoArguments(first.getArguments())) {
                        return md;
                    }
                    if ("super".equals(first.getSimpleName())) {
                        superCall = first;
                    }
                }
                CipherAssignment assignment = CipherAssignment.find(md.getBody(), md);
                if (assignment == CipherAssignment.UNMOVABLE) {
                    return md;
                }

                J.MethodDeclaration updated = md;
                if (assignment != null) {
                    updated = updated.withBody(assignment.removeFrom(updated.getBody()));
                    doAfterVisit(new RemovePlexusCipherExceptionCatch().getVisitor());
                }
                if (superCall != null) {
                    J.MethodInvocation sc = superCall;
                    updated = updated.withBody(updated.getBody().withStatements(
                            ListUtils.map(updated.getBody().getStatements(), s -> s == sc ? null : s)));
                }
                updateCursor(updated);
                JavaCoordinates coordinates = updated.getBody().getCoordinates().firstStatement();
                if (assignment == null) {
                    maybeAddImport(DEFAULT_PLEXUS_CIPHER);
                    return JavaTemplate.builder("super(new DefaultPlexusCipher());")
                            .contextSensitive()
                            .imports(DEFAULT_PLEXUS_CIPHER)
                            .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                            .build()
                            .apply(getCursor(), coordinates);
                }
                return JavaTemplate.builder("super(#{any(" + PLEXUS_CIPHER + ")});")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                        .build()
                        .apply(getCursor(), coordinates, assignment.cipher.withPrefix(Space.EMPTY));
            }
        });
    }

    private static boolean hasNoArguments(List<Expression> arguments) {
        return arguments.isEmpty() || arguments.size() == 1 && arguments.get(0) instanceof J.Empty;
    }

    /**
     * An assignment to {@code _cipher} in the body of a constructor or an instance initializer, directly or in a
     * {@code try} directly in it, whose value can be passed to the constructor of {@code DefaultSecDispatcher} instead.
     */
    private static final class CipherAssignment {
        static final CipherAssignment UNMOVABLE = new CipherAssignment(null, null, null, -1);

        final J.@Nullable Assignment assignment;
        final J.@Nullable Try inTry;
        final Expression cipher;
        final int initializerIndex;

        @SuppressWarnings("DataFlowIssue")
        private CipherAssignment(J.@Nullable Assignment assignment, J.@Nullable Try inTry, @Nullable Expression cipher, int initializerIndex) {
            this.assignment = assignment;
            this.inTry = inTry;
            this.cipher = cipher;
            this.initializerIndex = initializerIndex;
        }

        CipherAssignment inInitializer(int index) {
            return new CipherAssignment(assignment, inTry, cipher, index);
        }

        /**
         * @return The one movable assignment, {@link #UNMOVABLE} when the fields are assigned in a way this recipe
         * doesn't move, or {@code null} when {@code _cipher} isn't assigned.
         */
        static @Nullable CipherAssignment find(J.Block block, J.@Nullable MethodDeclaration constructor) {
            if (assignsFieldElsewhere(block)) {
                return UNMOVABLE;
            }
            CipherAssignment found = null;
            for (Statement statement : block.getStatements()) {
                if (isCipherAssignment(statement)) {
                    if (found != null) {
                        return UNMOVABLE;
                    }
                    found = movable((J.Assignment) statement, null, constructor);
                } else if (statement instanceof J.Try) {
                    J.Try t = (J.Try) statement;
                    for (Statement inTry : t.getBody().getStatements()) {
                        if (isCipherAssignment(inTry)) {
                            if (found != null) {
                                return UNMOVABLE;
                            }
                            found = movable((J.Assignment) inTry, t, constructor);
                        }
                    }
                }
            }
            return found;
        }

        /**
         * Whether {@code _decryptors}, which is final in 2.0 too, is assigned, or {@code _cipher} is assigned anywhere
         * but directly in the block or a {@code try} directly in it.
         */
        private static boolean assignsFieldElsewhere(J.Block block) {
            AtomicBoolean found = new AtomicBoolean(false);
            new JavaVisitor<AtomicBoolean>() {
                @Override
                public J visitAssignment(J.Assignment assignment, AtomicBoolean f) {
                    String name = assignedField(assignment.getVariable());
                    if ("_decryptors".equals(name)) {
                        f.set(true);
                    } else if ("_cipher".equals(name)) {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        Cursor grandParentCursor = getCursor().getParentTreeCursor().getParentTreeCursor();
                        boolean direct = parent == block ||
                                         parent instanceof J.Block && grandParentCursor.getValue() instanceof J.Try &&
                                         grandParentCursor.getParentTreeCursor().getValue() == block &&
                                         ((J.Try) grandParentCursor.getValue()).getBody() == parent;
                        if (!direct) {
                            f.set(true);
                        }
                    }
                    return super.visitAssignment(assignment, f);
                }

                @Override
                public J visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean f) {
                    return classDecl;
                }

                @Override
                public J visitNewClass(J.NewClass newClass, AtomicBoolean f) {
                    // the fields of another dispatcher, created in an anonymous class
                    return newClass.getBody() == null ? super.visitNewClass(newClass, f) : newClass;
                }
            }.visit(block, found, new Cursor(null, Cursor.ROOT_VALUE));
            return found.get();
        }

        private static boolean isCipherAssignment(Statement statement) {
            return statement instanceof J.Assignment &&
                   "_cipher".equals(assignedField(((J.Assignment) statement).getVariable()));
        }

        private static @Nullable String assignedField(Expression variable) {
            JavaType.Variable fieldType = null;
            String name = null;
            if (variable instanceof J.Identifier) {
                fieldType = ((J.Identifier) variable).getFieldType();
                name = ((J.Identifier) variable).getSimpleName();
            } else if (variable instanceof J.FieldAccess && ((J.FieldAccess) variable).getTarget() instanceof J.Identifier &&
                       "this".equals(((J.Identifier) ((J.FieldAccess) variable).getTarget()).getSimpleName())) {
                fieldType = ((J.FieldAccess) variable).getName().getFieldType();
                name = ((J.FieldAccess) variable).getSimpleName();
            }
            return fieldType != null && TypeUtils.isOfClassType(fieldType.getOwner(), DEFAULT_SEC_DISPATCHER) ? name : null;
        }

        private static CipherAssignment movable(J.Assignment assignment, J.@Nullable Try inTry, J.@Nullable MethodDeclaration constructor) {
            Expression value = assignment.getAssignment();
            return isMovable(value, inTry != null, constructor) ?
                    new CipherAssignment(assignment, inTry, value, -1) : UNMOVABLE;
        }

        /**
         * Whether the value can be computed before the dispatcher's constructor runs, and, when it was assigned in a
         * {@code try}, without throwing a checked exception.
         */
        private static boolean isMovable(Expression value, boolean inTry, J.@Nullable MethodDeclaration constructor) {
            if (value instanceof J.NewClass) {
                J.NewClass nc = (J.NewClass) value;
                if (nc.getBody() != null || nc.getEnclosing() != null || nc.getMethodType() == null ||
                    inTry && !DEFAULT_PLEXUS_CIPHER_CONSTRUCTOR.matches(nc) && !nc.getMethodType().getThrownExceptions().isEmpty()) {
                    return false;
                }
                for (Expression argument : nc.getArguments()) {
                    if (!(argument instanceof J.Empty) && !isMovable(argument, inTry, constructor)) {
                        return false;
                    }
                }
                return true;
            }
            JavaType.Variable fieldType = null;
            if (value instanceof J.Identifier) {
                fieldType = ((J.Identifier) value).getFieldType();
            } else if (value instanceof J.FieldAccess) {
                fieldType = ((J.FieldAccess) value).getName().getFieldType();
            }
            if (fieldType == null) {
                return false;
            }
            if (fieldType.hasFlags(Flag.Static)) {
                return true;
            }
            // a parameter of the constructor
            return constructor != null && value instanceof J.Identifier &&
                   fieldType.getOwner() instanceof JavaType.Method &&
                   constructor.getParameters().stream().anyMatch(p -> p instanceof J.VariableDeclarations &&
                           ((J.VariableDeclarations) p).getVariables().stream().anyMatch(v ->
                                   v.getSimpleName().equals(((J.Identifier) value).getSimpleName())));
        }

        J.Block removeFrom(J.Block block) {
            if (inTry == null) {
                return block.withStatements(ListUtils.map(block.getStatements(), s -> s == assignment ? null : s));
            }
            return block.withStatements(ListUtils.map(block.getStatements(), s -> s != inTry ? s :
                    inTry.withBody(inTry.getBody().withStatements(ListUtils.map(inTry.getBody().getStatements(),
                            inner -> inner == assignment ? null : inner)))));
        }
    }
}

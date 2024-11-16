/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.codehaus.plexus;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.logging.AddLogger;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicReference;

public class AbstractLogEnabledToSlf4j extends Recipe {

    private static final String ABSTRACT_LOG_ENABLED = "org.codehaus.plexus.logging.AbstractLogEnabled";
    private static final MethodMatcher GET_LOGGER_MATCHER = new MethodMatcher(ABSTRACT_LOG_ENABLED + " getLogger()", true);
    private static final String PLEXUS_LOGGER = "org.codehaus.plexus.logging.Logger";
    private static final MethodMatcher PLEXUS_LOGGER_MATCHER = new MethodMatcher("org.codehaus.plexus.logging.Logger *(..)");

    @Override
    public String getDisplayName() {
        return "Migrate from Plexus `AbstractLogEnabled` to SLF4J";
    }

    @Override
    public String getDescription() {
        return "Introduce a SLF4J `Logger` field and replace calls to `getLogger()` with calls to the field.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(ABSTRACT_LOG_ENABLED, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = classDecl;
                        if (TypeUtils.isAssignableTo(ABSTRACT_LOG_ENABLED, cd.getType())) {

                            // If we directly extend AbstractLogEnabled, remove the extends clause
                            TypeTree anExtends = cd.getExtends();
                            if (anExtends != null && TypeUtils.isOfClassType(anExtends.getType(), ABSTRACT_LOG_ENABLED)) {
                                maybeRemoveImport(ABSTRACT_LOG_ENABLED);
                                cd = cd.withExtends(null);
                            }

                            // Remove local variables named `logger`
                            cd = (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                                @Override
                                public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                                    if (multiVariable.getVariables().stream()
                                            .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                            .anyMatch("logger"::equals)) {
                                        return null;
                                    }
                                    return super.visitVariableDeclarations(multiVariable, ctx);
                                }
                            }.visitNonNull(cd, ctx, getCursor().getParentTreeCursor());

                            // Add a logger field
                            maybeAddImport("org.slf4j.Logger");
                            maybeAddImport("org.slf4j.LoggerFactory");
                            cd = (J.ClassDeclaration) AddLogger.addSlf4jLogger(cd, "logger", ctx)
                                    .visitNonNull(cd, ctx, getCursor().getParentTreeCursor());
                            AtomicReference<J.Identifier> loggerFieldReference = new AtomicReference<>();
                            new JavaIsoVisitor<AtomicReference<J.Identifier>>() {
                                @Override
                                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, AtomicReference<J.Identifier> ref) {
                                    for (J.VariableDeclarations.NamedVariable var : multiVariable.getVariables()) {
                                        if (TypeUtils.isOfClassType(var.getType(), "org.slf4j.Logger")) {
                                            ref.set(var.getName());
                                        }
                                    }
                                    return super.visitVariableDeclarations(multiVariable, ref);
                                }
                            }.visitClassDeclaration(cd, loggerFieldReference);

                            // Replace calls to getLogger() with the logger field
                            cd = (J.ClassDeclaration) new JavaVisitor<ExecutionContext>() {
                                @Override
                                public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                    J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                                    if (GET_LOGGER_MATCHER.matches(mi)) {
                                        return loggerFieldReference.get().withPrefix(mi.getPrefix());
                                    }
                                    if (PLEXUS_LOGGER_MATCHER.matches(mi)) {
                                        return mi.getPadding().withSelect(JRightPadded.build(mi.getSelect()));
                                    }
                                    return mi;
                                }
                            }.visitNonNull(cd, ctx, getCursor().getParentTreeCursor());

                            // Replace `fatal` calls with `error`
                            cd = (J.ClassDeclaration) new ChangeMethodName(PLEXUS_LOGGER + " fatalError(..)", "error", false, false)
                                    .getVisitor().visitNonNull(cd, ctx, getCursor().getParentTreeCursor());
                            cd = (J.ClassDeclaration) new ChangeMethodName(PLEXUS_LOGGER + " isFatalErrorEnabled(..)", "isErrorEnabled", false, false)
                                    .getVisitor().visitNonNull(cd, ctx, getCursor().getParentTreeCursor());

                            // Change any leftover `org.codehaus.plexus.logging.Logger` types to SLF4J Logger
                            maybeRemoveImport(PLEXUS_LOGGER);
                            cd = (J.ClassDeclaration) new ChangeType(PLEXUS_LOGGER, "org.slf4j.Logger", false)
                                    .getVisitor().visitNonNull(cd, ctx, getCursor().getParentTreeCursor());

                        }
                        return super.visitClassDeclaration(cd, ctx);
                    }
                }
        );
    }
}

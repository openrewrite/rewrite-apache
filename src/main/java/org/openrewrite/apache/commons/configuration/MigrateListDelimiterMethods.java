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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static org.openrewrite.apache.commons.configuration.CommonsConfiguration2.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateListDelimiterMethods extends Recipe {

    String displayName = "Replace the Commons Configuration 1.x list delimiter methods";

    String description = "Configuration 2 splits values into lists with a `ListDelimiterHandler`. This replaces " +
            "`setDelimiterParsingDisabled(true)` with a `DisabledListDelimiterHandler`, and " +
            "`setDelimiterParsingDisabled(false)` and `setListDelimiter(char)` with a `DefaultListDelimiterHandler`. " +
            "This recipe matches the 1.x API by the names the move to the `org.apache.commons.configuration2` " +
            "package gives it, and is meant to run as part of `UpgradeApacheCommonsConfiguration_1_2`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + "*", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null || !type.getDeclaringType().getFullyQualifiedName().startsWith(PACKAGE) ||
                    type.getParameterTypes().size() != 1) {
                    return m;
                }
                Expression argument = m.getArguments().get(0);
                String handler;
                if ("setDelimiterParsingDisabled".equals(type.getName()) && type.getParameterTypes().get(0) == JavaType.Primitive.Boolean) {
                    if (J.Literal.isLiteralValue(argument, true)) {
                        handler = "DisabledListDelimiterHandler.INSTANCE";
                    } else if (J.Literal.isLiteralValue(argument, false)) {
                        handler = "new DefaultListDelimiterHandler(',')";
                    } else {
                        handler = "#{any(boolean)} ? DisabledListDelimiterHandler.INSTANCE : new DefaultListDelimiterHandler(',')";
                    }
                } else if ("setListDelimiter".equals(type.getName()) && type.getParameterTypes().get(0) == JavaType.Primitive.Char) {
                    handler = "new DefaultListDelimiterHandler(#{any(char)})";
                } else {
                    return m;
                }
                boolean hasArgument = handler.contains("#{");
                maybeAddImport(DEFAULT_LIST_DELIMITER_HANDLER);
                maybeAddImport(DISABLED_LIST_DELIMITER_HANDLER);
                J.MethodInvocation replaced;
                if (m.getSelect() == null) {
                    replaced = JavaTemplate.builder("setListDelimiterHandler(" + handler + ")")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2"))
                            .imports(DEFAULT_LIST_DELIMITER_HANDLER, DISABLED_LIST_DELIMITER_HANDLER)
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), hasArgument ? new Object[]{argument} : new Object[0]);
                } else {
                    replaced = JavaTemplate.builder("#{any(" + PACKAGE + "AbstractConfiguration)}.setListDelimiterHandler(" + handler + ")")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-configuration2"))
                            .imports(DEFAULT_LIST_DELIMITER_HANDLER, DISABLED_LIST_DELIMITER_HANDLER)
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), hasArgument ? new Object[]{m.getSelect(), argument} : new Object[]{m.getSelect()});
                }
                return replaced;
            }
        });
    }
}

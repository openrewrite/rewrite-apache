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
import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class MigrateSecDispatcherExceptionConstructor extends Recipe {

    private static final String SEC_DISPATCHER_EXCEPTION = "org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException";
    private static final MethodMatcher NO_ARGUMENT_CONSTRUCTOR = new MethodMatcher(SEC_DISPATCHER_EXCEPTION + " <constructor>()");

    @Getter
    final String displayName = "Pass a message to the constructor of `SecDispatcherException`";

    @Getter
    final String description = "`org.codehaus.plexus:plexus-sec-dispatcher` 2.0 has no constructor of " +
            "`SecDispatcherException` without arguments. This passes the `null` message that constructor left the " +
            "exception with.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(NO_ARGUMENT_CONSTRUCTOR), new JavaVisitor<ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // the template writes Java
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (nc.getBody() != null || !NO_ARGUMENT_CONSTRUCTOR.matches(nc)) {
                    return nc;
                }
                // the cast picks the constructor that takes a message over the one that takes a cause
                return JavaTemplate.builder("new SecDispatcherException((String) null)")
                        .imports(SEC_DISPATCHER_EXCEPTION)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(PlexusSecurityStubs.CODEHAUS_2_0))
                        .build()
                        .apply(getCursor(), nc.getCoordinates().replace());
            }
        });
    }
}

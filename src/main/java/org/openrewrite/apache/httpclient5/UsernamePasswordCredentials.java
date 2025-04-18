/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.apache.httpclient5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class UsernamePasswordCredentials extends Recipe {
    private static final String FQN = "org.apache.http.auth.UsernamePasswordCredentials";
    private static final String METHOD_PATTERN = FQN + " <constructor>(String, String)";

    @Override
    public String getDisplayName() {
        return "Migrate `UsernamePasswordCredentials` to httpclient5";
    }

    @Override
    public String getDescription() {
        return "Change the password argument going into `UsernamePasswordCredentials` to be a `char[]`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher methodMatcher = new MethodMatcher(METHOD_PATTERN);
        return Preconditions.check(new UsesMethod<>(methodMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                if (methodMatcher.matches(nc)) {
                    Expression passwordArgument = nc.getArguments().get(1);
                    nc = JavaTemplate.apply("#{any(String)}.toCharArray()",
                            getCursor(), passwordArgument.getCoordinates().replace(), passwordArgument);
                }
                return nc;
            }
        });
    }
}

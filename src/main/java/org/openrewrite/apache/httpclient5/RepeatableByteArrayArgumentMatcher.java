/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.java.template.Matcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class RepeatableByteArrayArgumentMatcher implements Matcher<Expression> {
    /**
     * @param expr an argument to a method invocation
     * @return true if the argument is a simple getter that returns a byte[], or an identifier or field access
     */
    @Override
    public boolean matches(Expression expr) {
        if (expr instanceof J.Identifier || expr instanceof J.FieldAccess) {
            return true;
        }
        if (!(expr instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation castArg = (J.MethodInvocation) expr;
        // Allow simple getters that return a byte[]
        return castArg.getSelect() instanceof J.Identifier &&
                castArg.getSimpleName().startsWith("get") &&
                (castArg.getArguments().isEmpty() || castArg.getArguments().get(0) instanceof J.Empty) &&
                TypeUtils.isAssignableTo("byte[]", castArg.getMethodType());
    }
}

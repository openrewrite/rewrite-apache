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
package org.openrewrite.apache.commons.lang;

import org.openrewrite.java.template.Matcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class RepeatableArgumentMatcher implements Matcher<Expression> {
    /**
     * @param arg an argument to a method invocation
     * @return true if the argument is a simple getter that returns a String, or an identifier or field access
     */
    static boolean isRepeatableArgument(Expression arg) {
        if (arg instanceof J.Literal || arg instanceof J.Identifier || arg instanceof J.FieldAccess) {
            return true;
        }
        // Allow simple getters that return a String
        return arg instanceof J.MethodInvocation &&
                ((J.MethodInvocation) arg).getSelect() instanceof J.Identifier &&
                ((J.MethodInvocation) arg).getSimpleName().startsWith("get") &&
                (((J.MethodInvocation) arg).getArguments().isEmpty() || ((J.MethodInvocation) arg).getArguments().get(0) instanceof J.Empty) &&
                TypeUtils.isAssignableTo("java.lang.String", ((J.MethodInvocation) arg).getMethodType());
    }

    @Override
    public boolean matches(Expression expr) {
        return isRepeatableArgument(expr);
    }
}

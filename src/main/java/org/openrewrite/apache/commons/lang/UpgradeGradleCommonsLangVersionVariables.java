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
package org.openrewrite.apache.commons.lang;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.ChangeExtraProperty;
import org.openrewrite.gradle.DependencyVersionSelector;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.table.MavenMetadataFailures;
import org.openrewrite.maven.tree.GroupArtifact;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpgradeGradleCommonsLangVersionVariables extends ScanningRecipe<Map<String, String>> {

    private static final String OLD_COORDINATES = "commons-lang:commons-lang:";

    transient MavenMetadataFailures metadataFailures = new MavenMetadataFailures(this);

    @Getter
    final String displayName = "Upgrade Gradle version variables of Commons Lang 2 dependencies";

    @Getter
    final String description = "A dependency declared as `\"commons-lang:commons-lang:$version\"` moves to " +
                               "`org.apache.commons:commons-lang3`, but its version variable still holds `2.x`. " +
                               "Set the variable, wherever it is defined, to the latest Commons Lang 3 version. " +
                               "This has to look at the build before any other recipe changes the coordinates.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("apache", "commons"));

    @Override
    public Map<String, String> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, String> acc) {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.GString visitGString(G.GString gString, ExecutionContext ctx) {
                List<J> strings = gString.getStrings();
                if (strings.size() == 2 && strings.get(0) instanceof J.Literal && strings.get(1) instanceof G.GString.Value) {
                    Object prefix = ((J.Literal) strings.get(0)).getValue();
                    J tree = ((G.GString.Value) strings.get(1)).getTree();
                    if (prefix instanceof String && ((String) prefix).endsWith(OLD_COORDINATES) && tree instanceof J.Identifier) {
                        String variable = ((J.Identifier) tree).getSimpleName();
                        if (!acc.containsKey(variable)) {
                            G.CompilationUnit cu = getCursor().firstEnclosing(G.CompilationUnit.class);
                            GradleProject gp = cu == null ? null : cu.getMarkers().findFirst(GradleProject.class).orElse(null);
                            String version = latestCommonsLang3(gp, ctx);
                            if (version != null) {
                                acc.put(variable, version);
                            }
                        }
                    }
                }
                return super.visitGString(gString, ctx);
            }
        };
    }

    private @Nullable String latestCommonsLang3(@Nullable GradleProject gp, ExecutionContext ctx) {
        if (gp == null) {
            return null;
        }
        try {
            return new DependencyVersionSelector(metadataFailures, gp, null)
                    .select(new GroupArtifact("org.apache.commons", "commons-lang3"), "implementation", "3.x", null, ctx);
        } catch (MavenDownloadingException e) {
            return null;
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, String> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree t = tree;
                for (Map.Entry<String, String> variable : acc.entrySet()) {
                    t = new ChangeExtraProperty(variable.getKey(), variable.getValue()).getVisitor().visit(t, ctx);
                }
                return t;
            }
        };
    }
}

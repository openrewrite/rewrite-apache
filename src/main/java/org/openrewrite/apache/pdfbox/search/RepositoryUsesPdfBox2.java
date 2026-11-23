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
package org.openrewrite.apache.pdfbox.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@EqualsAndHashCode(callSuper = false)
@Value
public class RepositoryUsesPdfBox2 extends ScanningRecipe<RepositoryUsesPdfBox2.Accumulator> {

    String displayName = "Repository uses PDFBox 2";

    String description = "Marks every source file of a repository in which a Maven or Gradle module declares a dependency on " +
            "a PDFBox 2.x artifact, such as `org.apache.pdfbox:pdfbox`, no module resolves a PDFBox 1.x artifact, and no library " +
            "it depends on, other than PDFBox and the repository's own modules, asks for PDFBox 2.x. " +
            "The PDFBox 3 migration takes this as its precondition: it migrates code written for PDFBox 2 only, and raising " +
            "PDFBox to 3.x where code still uses PDFBox 1.x, where a library such as Apache Tika 2 is compiled against PDFBox 2, " +
            "or where PDFBox 2.x only comes through another library, would break that code.";

    public static class Accumulator {
        boolean declaresPdfBox2;
        boolean resolvesPdfBox1;
        final Set<String> modules = new HashSet<>();
        final Set<String> librariesNeedingPdfBox2 = new TreeSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    acc.modules.add(maven.getPom().getGroupId() + ":" + maven.getPom().getArtifactId());
                    for (List<ResolvedDependency> dependencies : maven.getDependencies().values()) {
                        for (ResolvedDependency dependency : dependencies) {
                            record(acc, dependency, dependency.isDirect());
                        }
                    }
                });
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gradle -> {
                    acc.modules.add(gradle.getGroup() + ":" + gradle.getName());
                    for (GradleDependencyConfiguration configuration : gradle.getConfigurations()) {
                        for (ResolvedDependency dependency : configuration.getDirectResolved()) {
                            record(acc, dependency, true);
                        }
                        for (ResolvedDependency dependency : configuration.getResolved()) {
                            record(acc, dependency, false);
                        }
                    }
                });
                return tree;
            }
        };
    }

    private static void record(Accumulator acc, ResolvedDependency dependency, boolean direct) {
        if (!isPdfBox(dependency)) {
            for (ResolvedDependency library : dependency.getDependencies()) {
                String requested = library.getRequested().getVersion();
                if (isPdfBox(library) && requested != null && requested.matches("[\\[(]?2\\..*")) {
                    acc.librariesNeedingPdfBox2.add(dependency.getGroupId() + ":" + dependency.getArtifactId());
                }
            }
            return;
        }
        String version = dependency.getVersion();
        if (version.startsWith("1.")) {
            acc.resolvesPdfBox1 = true;
        } else if (direct && version.startsWith("2.")) {
            acc.declaresPdfBox2 = true;
        }
    }

    /**
     * Whether the dependency is a PDFBox artifact, which the migration upgrades. JBIG2 ImageIO and JempBox are
     * released apart from PDFBox, with versions of their own.
     */
    private static boolean isPdfBox(ResolvedDependency dependency) {
        return "org.apache.pdfbox".equals(dependency.getGroupId()) &&
               !"jbig2-imageio".equals(dependency.getArtifactId()) && !"jempbox".equals(dependency.getArtifactId());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        acc.librariesNeedingPdfBox2.removeAll(acc.modules);
        if (!acc.declaresPdfBox2 || acc.resolvesPdfBox1 || !acc.librariesNeedingPdfBox2.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree == null ? null : SearchResult.found(tree, "Uses PDFBox 2");
            }
        };
    }
}

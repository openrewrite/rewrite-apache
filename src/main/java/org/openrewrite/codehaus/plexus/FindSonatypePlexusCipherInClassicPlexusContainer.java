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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.xml.tree.Xml;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindSonatypePlexusCipherInClassicPlexusContainer extends ScanningRecipe<Set<String>> {

    private static final String SONATYPE_PLEXUS = "org.sonatype.plexus";
    private static final List<String> SONATYPE_ARTIFACTS = Arrays.asList("plexus-cipher", "plexus-sec-dispatcher");
    private static final String[] CLASSIC_CONTAINER = {"org.codehaus.plexus", "plexus-container-default"};
    private static final String[][] SISU_PLEXUS = {
            {"org.eclipse.sisu", "org.eclipse.sisu.plexus"},
            {"org.sonatype.sisu", "sisu-inject-plexus"}
    };

    @Option(displayName = "Invert marking",
            description = "If `true`, marks every file of a repository where no module runs a classic Plexus container " +
                    "with the Sonatype artifacts instead, so that the migration can take this recipe as a precondition.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Find the Sonatype Plexus cipher where a classic Plexus container runs";

    String description = "The components of `org.codehaus.plexus:plexus-sec-dispatcher` 2.0 and " +
            "`org.codehaus.plexus:plexus-cipher` 2.0 are made for Sisu. A classic Plexus container, such as the one of " +
            "Maven 2, can't create them, as `DefaultSecDispatcher` has no constructor without arguments and the jar of " +
            "the cipher has no Plexus `components.xml`. A module runs such a container when it resolves " +
            "`org.codehaus.plexus:plexus-container-default` and not the Plexus support of Sisu. In a repository where " +
            "such a module resolves `org.sonatype.plexus:plexus-cipher` or `org.sonatype.plexus:plexus-sec-dispatcher`, " +
            "this marks where the repository declares or manages them.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new TreeSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> classicModules) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                MavenResolutionResult maven = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                if (maven != null) {
                    if (maven.findDependencies(CLASSIC_CONTAINER[0], CLASSIC_CONTAINER[1], null).isEmpty() ||
                        Arrays.stream(SISU_PLEXUS).anyMatch(ga -> !maven.findDependencies(ga[0], ga[1], null).isEmpty())) {
                        return tree;
                    }
                    if (SONATYPE_ARTIFACTS.stream().anyMatch(a -> !maven.findDependencies(SONATYPE_PLEXUS, a, null).isEmpty())) {
                        classicModules.add(maven.getPom().getArtifactId());
                    }
                    return tree;
                }
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gradle -> {
                    if (resolves(gradle, CLASSIC_CONTAINER[0], CLASSIC_CONTAINER[1]) &&
                        Arrays.stream(SISU_PLEXUS).noneMatch(ga -> resolves(gradle, ga[0], ga[1])) &&
                        SONATYPE_ARTIFACTS.stream().anyMatch(a -> resolves(gradle, SONATYPE_PLEXUS, a))) {
                        classicModules.add(gradle.getName());
                    }
                });
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> classicModules) {
        if (Boolean.TRUE.equals(invertMarking)) {
            if (!classicModules.isEmpty()) {
                return TreeVisitor.noop();
            }
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    return tree instanceof SourceFile ? SearchResult.found(tree) : tree;
                }
            };
        }
        if (classicModules.isEmpty()) {
            return TreeVisitor.noop();
        }
        String message = "Not migrated, as " + String.join(", ", classicModules) +
                         (classicModules.size() == 1 ? " runs" : " run") +
                         " a classic Plexus container, which can't create the components of 2.0";
        MavenIsoVisitor<ExecutionContext> maven = new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                for (String artifactId : SONATYPE_ARTIFACTS) {
                    if (isDependencyTag(SONATYPE_PLEXUS, artifactId) || isManagedDependencyTag(SONATYPE_PLEXUS, artifactId)) {
                        return SearchResult.found(t, message);
                    }
                }
                return t;
            }
        };
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                if (maven.isAcceptable((SourceFile) tree, ctx)) {
                    return maven.visit(tree, ctx);
                }
                GradleProject gradle = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
                if (gradle != null && requests(gradle)) {
                    return SearchResult.found(tree, message);
                }
                return tree;
            }
        };
    }

    private static boolean resolves(GradleProject gradle, String groupId, String artifactId) {
        for (GradleDependencyConfiguration configuration : gradle.getConfigurations()) {
            for (ResolvedDependency dependency : configuration.getDirectResolved()) {
                if (dependency.findDependency(groupId, artifactId) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requests(GradleProject gradle) {
        for (GradleDependencyConfiguration configuration : gradle.getConfigurations()) {
            for (Dependency dependency : configuration.getRequested()) {
                if (SONATYPE_PLEXUS.equals(dependency.getGroupId()) && SONATYPE_ARTIFACTS.contains(dependency.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }
}

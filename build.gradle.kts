plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Apache Migration"

recipeDependencies {
    parserClasspath("org.apache.httpcomponents.core5:httpcore5:5.1.+")
    parserClasspath("org.apache.httpcomponents.client5:httpclient5:5.1.+")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:2.19.1:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    implementation("commons-io:commons-io:2.+")
    implementation("org.apache.commons:commons-lang3:3.+")
    implementation("org.apache.maven.shared:maven-shared-utils:3.+")
    implementation("org.codehaus.plexus:plexus-utils:3.+")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("commons-codec:commons-codec:1.+")

    testRuntimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
    testRuntimeOnly("org.apache.httpcomponents.client5:httpclient5:5.2.+")


    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")
}

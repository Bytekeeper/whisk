import com.google.protobuf.gradle.ExecutableLocator
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    val kotlinVersion = "1.3.41"

    kotlin("jvm") version kotlinVersion
    id("com.github.ben-manes.versions") version "0.22.0"
    kotlin("kapt") version kotlinVersion
    antlr
    id("com.google.protobuf") version "0.8.10"
    idea
}

val spek_version = "2.0.7"
val protobufVersion = "3.9.0"

dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    compileOnly("junit:junit:4.12")
    compileOnly("com.puppycrawl.tools:checkstyle:8.25")
    compileOnly("com.pinterest:ktlint:0.35.0")
    implementation(kotlin("reflect"))
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.12.1")
    implementation("org.apache.logging.log4j:log4j-core:2.12.1")
    implementation("org.apache.logging.log4j:log4j-iostreams:2.12.1")
    implementation("org.apache.maven:maven-resolver-provider:3.6.1")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.4.0")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.4.0")
    implementation("org.ow2.asm:asm:7.2")
    testImplementation("junit:junit:4.12")
    implementation(group = "com.google.protobuf", name = "protobuf-java", version = protobufVersion)
    implementation("com.github.javaparser:javaparser-core:3.15.0")

    implementation("com.google.dagger:dagger:2.24")
    kapt("com.google.dagger:dagger-compiler:2.24")
    antlr("org.antlr:antlr4:4.7.2")
    testImplementation("org.assertj:assertj-core:3.13.2")
}

allprojects {
    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
compileKotlin.dependsOn(tasks.generateGrammarSource)

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

kapt {
}

tasks {
    jar {
        manifest {
            attributes("Main-Class" to "org.whisk.MainKt")
        }

        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}

tasks.generateGrammarSource {
    outputDirectory = file("build/generated-src/antlr/main/org/whisk/buildlang")
    arguments = listOf("-visitor")
}

protobuf.protobuf.protoc(delegateClosureOf<ExecutableLocator> {
    artifact = "com.google.protobuf:protoc:$protobufVersion"
})

idea.module


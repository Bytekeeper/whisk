import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    val kotlinVersion = "1.3.31"

    kotlin("jvm") version kotlinVersion
    id("com.github.ben-manes.versions") version "0.21.0"
    kotlin("kapt") version kotlinVersion
    antlr
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(kotlin("compiler-embeddable"))
    implementation("org.apache.logging.log4j:log4j-core:2.11.2")
    implementation("org.apache.maven:maven-resolver-provider:3.6.1")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.3.3")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.3.3")
    implementation("junit:junit:4.12")

    implementation("com.google.dagger:dagger:2.22.1")
    kapt("com.google.dagger:dagger-compiler:2.22.1")
    antlr("org.antlr:antlr4:4.7.2")
    testImplementation("org.assertj:assertj-core:3.11.1")
}

allprojects {
    apply {
        plugin("kotlin")
    }

    repositories {
        mavenCentral()
        jcenter()
    }


}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
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
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    val kotlinVersion = "1.3.31"

    kotlin("jvm") version kotlinVersion
    id("com.github.ben-manes.versions") version "0.21.0"
    kotlin("kapt") version kotlinVersion
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
//    implementation(kotlin("compiler"))
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.3.31")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:1.3.31")
    implementation("org.tomlj:tomlj:1.0.0")
    implementation("org.apache.logging.log4j:log4j-core:2.11.2")
//    testImplementation("org.jetbrains.kotlin:kotlin-annotation-processing:1.3.31")
    implementation("org.apache.maven:maven-resolver-provider:3.6.1")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.3.3")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.3.3")
    implementation("junit:junit:4.12")

    implementation("com.google.dagger:dagger:2.22.1")
    kapt("com.google.dagger:dagger-compiler:2.22.1")
//    testImplementation("com.google.dagger:dagger-compiler:2.22.1")
}

allprojects {
    apply {
        plugin("kotlin")
    }

    repositories {
        mavenCentral()
        jcenter()
    }

    sourceSets["main"].withConvention(KotlinSourceSet::class) {
        kotlin.srcDir(file("src"))
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
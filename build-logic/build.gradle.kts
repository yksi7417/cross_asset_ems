plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.javapoet)
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:6.25.0")
}

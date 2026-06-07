plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.javapoet)
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
}

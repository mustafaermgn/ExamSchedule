plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "androidx.lifecycle:lifecycle-runtime:2.8.0",
                "androidx.lifecycle:lifecycle-runtime-desktop:2.8.0",
                "androidx.lifecycle:lifecycle-common:2.8.0",
                "androidx.lifecycle:lifecycle-common-jvm:2.8.0",
                "androidx.lifecycle:lifecycle-viewmodel:2.8.0",
                "androidx.lifecycle:lifecycle-viewmodel-desktop:2.8.0"
            )
        }
    }
}

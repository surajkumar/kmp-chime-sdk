import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.publishing)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ChimeSDK"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.amazon.chime.sdk)
            implementation(libs.amazon.chime.sdk.media)
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.wannaverse.chimesdk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "chimesdk", version.toString())

    pom {
        name = "kmp-chime-sdk"
        description = "A library to integrate Amazon ChimeSDK"
        inceptionYear = "2025"
        url = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "Wannaverse"
                name = "wannaverse"
                url = "https://github.com/WannaverseOfficial"
            }
        }
        scm {
            url = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
            connection = "scm:git:git://github.com/WannaverseOfficial/kmp-chime-sdk.git"
            developerConnection = "scm:git:ssh://git@github.com/WannaverseOfficial/kmp-chime-sdk.git"
        }
    }
}

tasks.dokkaHtml {
    outputDirectory.set(file("${rootDir}/docs"))
}
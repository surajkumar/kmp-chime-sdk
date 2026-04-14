import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.publishing)
    kotlin("native.cocoapods")
}

group = "com.wannaverse"
version = "0.1.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
        publishLibraryVariantsGroupedByFlavor = true
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "KMP wrapper for Amazon ChimeSDK"
        homepage = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
        version = project.version.toString()
        ios.deploymentTarget = "16.0"

        pod("AmazonChimeSDK") {
            version = "~> 0.25.0"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.amazon.chime.sdk)
            implementation(libs.amazon.chime.sdk.media)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
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
        description = "A library to integrate Amazon ChimeSDK into Kotlin Multiplatform"
        inceptionYear = "2026"
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
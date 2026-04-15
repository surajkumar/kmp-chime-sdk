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
version = "0.2.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
        publishLibraryVariantsGroupedByFlavor = true
    }

    cocoapods {
        summary = "KMP wrapper for Amazon ChimeSDK"
        homepage = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
        version = project.version.toString()
        ios.deploymentTarget = "16.0"

        pod("AmazonChimeSDK") {
            version = "~> 0.25.0"
            linkOnly = true
        }
    }

    val podsDir = "${layout.buildDirectory.asFile.get()}/cocoapods/synthetic/ios/Pods"
    val simSlice = "ios-arm64_x86_64-simulator"
    val devSlice = "ios-arm64"

    iosX64 {
        compilations["main"].cinterops {
            val AmazonChimeSDK by creating {
                defFile(project.file("src/nativeInterop/cinterop/AmazonChimeSDK.def"))
                packageName("cocoapods.AmazonChimeSDK")
                compilerOpts(
                    "-fmodules",
                    "-F", "$podsDir/AmazonChimeSDK/AmazonChimeSDK.xcframework/$simSlice",
                    "-F", "$podsDir/AmazonChimeSDKMedia/AmazonChimeSDKMedia.xcframework/$simSlice"
                )
            }
        }
    }
    iosArm64 {
        compilations["main"].cinterops {
            val AmazonChimeSDK by creating {
                defFile(project.file("src/nativeInterop/cinterop/AmazonChimeSDK.def"))
                packageName("cocoapods.AmazonChimeSDK")
                compilerOpts(
                    "-fmodules",
                    "-F", "$podsDir/AmazonChimeSDK/AmazonChimeSDK.xcframework/$devSlice",
                    "-F", "$podsDir/AmazonChimeSDKMedia/AmazonChimeSDKMedia.xcframework/$devSlice"
                )
            }
        }
    }
    iosSimulatorArm64 {
        compilations["main"].cinterops {
            val AmazonChimeSDK by creating {
                defFile(project.file("src/nativeInterop/cinterop/AmazonChimeSDK.def"))
                packageName("cocoapods.AmazonChimeSDK")
                compilerOpts(
                    "-fmodules",
                    "-F", "$podsDir/AmazonChimeSDK/AmazonChimeSDK.xcframework/$simSlice",
                    "-F", "$podsDir/AmazonChimeSDKMedia/AmazonChimeSDKMedia.xcframework/$simSlice"
                )
            }
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

    if (!project.hasProperty("skipSigning")) {
        signAllPublications()
    }

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

afterEvaluate {
    tasks.findByName("cinteropAmazonChimeSDKIosSimulatorArm64")
        ?.dependsOn("podBuildAmazonChimeSDKIosSimulator")
    tasks.findByName("cinteropAmazonChimeSDKIosX64")
        ?.dependsOn("podBuildAmazonChimeSDKIosSimulator")
    tasks.findByName("cinteropAmazonChimeSDKIosArm64")
        ?.dependsOn("podBuildAmazonChimeSDKIos")
}

tasks.dokkaHtml {
    outputDirectory.set(file("${rootDir}/docs"))
}
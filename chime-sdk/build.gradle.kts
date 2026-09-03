import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.publishing)
    kotlin("native.cocoapods")
}

group = "com.wannaverse"
version = "0.5.16"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.wannaverse.chimesdk"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    cocoapods {
        summary = "KMP wrapper for Amazon ChimeSDK"
        homepage = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
        version = project.version.toString()
        ios.deploymentTarget = "16.0"

        pod("AmazonChimeSDK") {
            version = "~> 0.27.3"
            linkOnly = true
        }
    }

    val podsDir = "${layout.buildDirectory.asFile.get()}/cocoapods/synthetic/ios/Pods"
    val simSlice = "ios-arm64_x86_64-simulator"
    val devSlice = "ios-arm64"

    iosArm64 {
        compilations["main"].cinterops {
            val AmazonChimeSDK = create("AmazonChimeSDK") {
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
            val AmazonChimeSDK = create("AmazonChimeSDK") {
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
            implementation(libs.activity.ktx)

            implementation(libs.chime)
            implementation(libs.chime.media)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        iosMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

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
    tasks.findByName("cinteropAmazonChimeSDKIosArm64")
        ?.dependsOn("podBuildAmazonChimeSDKIos")
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(file("${rootDir}/docs"))
    }
}
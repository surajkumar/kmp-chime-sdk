Pod::Spec.new do |spec|
    spec.name                     = 'chime_sdk'
    spec.version                  = '0.2.22'
    spec.homepage                 = 'https://github.com/WannaverseOfficial/kmp-chime-sdk'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'KMP wrapper for Amazon ChimeSDK'
    spec.vendored_frameworks      = 'build/cocoapods/framework/chime_sdk.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '16.0'
    spec.dependency 'AmazonChimeSDK', '~> 0.25.0'
    if !Dir.exist?('build/cocoapods/framework/chime_sdk.framework') || Dir.empty?('build/cocoapods/framework/chime_sdk.framework')
        raise "
        Kotlin framework 'chime_sdk' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :chime-sdk:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':chime-sdk',
        'PRODUCT_MODULE_NAME' => 'chime_sdk',
    }
    spec.script_phases = [
        {
            :name => 'Build chime_sdk',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end

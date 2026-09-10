pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "agentterm"

// :core is a pure JVM module (terminal engine, gestures, shortcuts) so it can be
// unit-tested locally without an Android SDK. :app (Android UI + PTY + SSH) is
// included when a build environment provides Android tooling (CI, or dev machine
// with ANDROID_HOME). Local Termux dev box => JVM tests only; GitHub Actions => full APK.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null
        || System.getenv("ANDROID_SDK_ROOT") != null
        || System.getenv("CI") != null

include(":core")
if (hasAndroidSdk) {
    include(":app")
}
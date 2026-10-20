// Top-level build file. Versions are declared here and consumed by :app.
//
// AGP 8.7.3 + Kotlin 2.0.20 are pinned deliberately: both are already present in
// the local ~/.gradle cache on this machine, so the first build downloads far
// less. Kotlin 2.0.20 also means the Compose compiler ships as a Kotlin plugin
// (org.jetbrains.kotlin.plugin.compose), not a separate compiler extension —
// required for Kotlin 2.x.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}
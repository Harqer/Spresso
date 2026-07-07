plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Industrial Strategy: Redirect build directories to bypass filesystem path limits (D8 Dexing)
allprojects {
    layout.buildDirectory.set(file("/tmp/v-build/vaultier/${project.name}"))
}

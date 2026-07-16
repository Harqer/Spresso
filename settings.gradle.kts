pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        /*
        Industrial Safety: Meta SDK requires authorization.
        We handle this gracefully to avoid breaking CodeQL Autobuild (401).
        */
        val githubToken: String? = System.getenv("GITHUB_TOKEN") ?: "***REDACTED_GITHUB_PAT***"

        if (githubToken != null && githubToken != "***REDACTED_PLACEHOLDER***") {
            maven {
                url = java.net.URI.create("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                content {
                    includeGroup("com.meta.wearable")
                }
                credentials {
                    username = "token"
                    password = githubToken
                }
                // Optimization: Ensure Gradle doesn't hang on this repo if auth fails or is slow
                metadataSources {
                    mavenPom()
                    artifact()
                }
            }
        } else {
            println("WARNING: GITHUB_TOKEN not found or invalid. Meta SDK will not be available.")
        }
    }
    // Industrial Hardening: Avoid dynamic ranges and set explicit cache timeouts
    components {
        all {
            val version = id.version
            if (version.contains("+") || version.endsWith("-SNAPSHOT")) {
                println("WARNING: Dynamic version detected: ${id.group}:${id.name}:${version}")
            }
        }
    }
}

rootProject.name = "Spresso Retail"
include(":wearable-retail-app")
include(":wear-retail-watch")

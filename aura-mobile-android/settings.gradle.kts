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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        /*
        Industrial Safety: Meta SDK requires authorization.
        We handle this gracefully to avoid breaking CodeQL Autobuild (401).
        */
        val githubToken: String? = System.getenv("GITHUB_TOKEN") ?: "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"

        if (githubToken != null && githubToken != "REPLACE_ME_IN_EXPO_DASHBOARD") {
            maven {
                url = java.net.URI.create("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                content {
                    includeGroup("com.meta.wearable")
                }
                credentials {
                    username = "token"
                    password = githubToken
                }
                // Optimization: Ensure Gradle doesn't hang on this repo if auth fails
                metadataSources {
                    mavenPom()
                    artifact()
                }
            }
        } else {
            println("WARNING: GITHUB_TOKEN not found or invalid. Meta SDK will not be available.")
        }
    }
}

rootProject.name = "Spresso Retail"
include(":wearable-retail-app")
include(":wear-retail-watch")

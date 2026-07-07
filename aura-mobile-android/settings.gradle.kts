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
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            content {
                // ONLY look for Meta Wearables artifacts here to avoid 401 on Google/AndroidX artifacts
                includeGroup("com.meta.wearable")
            }
            credentials {
                // Ensure GITHUB_TOKEN is set in your Infisical vault or environment
                username = "token"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "Vaultier Retail"
include(":wearable-retail-app")
include(":wear-retail-watch")

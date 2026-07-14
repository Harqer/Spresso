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
        
        // Industrial Safety: Meta SDK requires authorization. 
        // We handle this gracefully to avoid breaking CodeQL Autobuild (401).
        val githubToken: String? = System.getenv("GITHUB_TOKEN")

        if (githubToken != null) {
            maven {
                url = java.net.URI.create("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                content {
                    includeGroup("com.meta.wearable")
                }
                credentials {
                    username = "token"
                    password = githubToken
                }
            }
        }
    }
}

rootProject.name = "Spresso Retail"
include(":wearable-retail-app")
include(":wear-retail-watch")

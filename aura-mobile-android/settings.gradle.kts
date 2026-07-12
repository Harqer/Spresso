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
                // Ensure GITHUB_TOKEN is set in your Infisical vault, environment, or local.properties
                username = "token"
                
                // Fallback to local.properties if env var is missing
                var localToken: String? = null
                val localPropsFile = File(rootDir, "local.properties")
                if (localPropsFile.exists()) {
                    val props = java.util.Properties()
                    props.load(java.io.FileInputStream(localPropsFile))
                    localToken = props.getProperty("GITHUB_TOKEN")
                }
                
                password = System.getenv("GITHUB_TOKEN") ?: localToken
            }
        }
    }
}

rootProject.name = "Vaultier Retail"
include(":wearable-retail-app")
include(":wear-retail-watch")

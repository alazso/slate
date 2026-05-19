pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "slate"

val siblingLedger = file("../ledger")
if (siblingLedger.exists()) {
    includeBuild("../ledger")
}

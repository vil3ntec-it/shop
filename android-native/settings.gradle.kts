pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = "TohidNative"
include(":app")

//  core — منطقِ خالصِ مشترک با کامپیوتر (قدم صفرِ ۳.۳)؛ `ir.vil3ntec.tohid:core`
includeBuild("../core")

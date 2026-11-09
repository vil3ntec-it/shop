/*
 *  مدلِ دفترِ نسخهٔ [models] (پوشه‌ای که `regen.sh` از `git show <ref>:` پر می‌کند) + `Harness.kt`.
 *  پوشهٔ ساخت بیرون از مخزن (`COMPAT_BUILD`) تا worktree سنگین نشود.
 */
plugins {
  kotlin("jvm") version "2.2.20"
  kotlin("plugin.serialization") version "2.2.20"
  application
}

val models = providers.gradleProperty("models").get()
System.getenv("COMPAT_BUILD")?.takeIf { it.isNotBlank() }?.let { layout.buildDirectory.set(file(it)) }

sourceSets { main { kotlin.srcDirs("src", models) } }

dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }


application { mainClass.set("ir.vil3ntec.tohid.compat.HarnessKt") }

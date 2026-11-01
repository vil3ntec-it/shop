package af.tohid.shop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * دو یافتهٔ امنیتیِ اپِ WebView (هم‌تراز با `android-native`):
 *
 *  1. پشتیبان: `app_webview` (localStorage ِ صفحه، با توکنِ ورودِ `tohid-shop-auth-v1`) نه در
 *     پشتیبانِ ابری می‌رود، نه در انتقالِ گوشی‌به‌گوشی، نه در پشتیبانِ پیش از اندروید ۱۲؛
 *     و `allowBackup` خاموش است.
 *  2. cleartext: نسخهٔ انتشار فقط HTTPS و فقط گواهی‌های سیستم را می‌پذیرد؛ باز بودنِ http
 *     فقط در `src/debug` است. اپ به http نیازی ندارد چون صفحه https است و CSP ِ
 *     `connect-src` فقط `'self'` و `https://`/`wss://` را می‌پذیرد — همین هم این‌جا سنجیده می‌شود.
 */
class WebViewHardeningTest {

    private fun file(rel: String): String =
        listOf(".", "app", "android/app").map { File(it, rel) }.firstOrNull { it.isFile }?.readText()
            ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

    private fun section(xml: String, tag: String): String =
        xml.substringAfter("<$tag>", "").substringBefore("</$tag>", "")

    /** بی کامنت‌ها، تا متنِ توضیح جای قاعده را نگیرد */
    private fun body(xml: String): String = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private val excludesWebview = Regex("""<exclude\s+domain="root"\s+path="app_webview"\s*/>""")
    private val includesWebview = Regex("""<include\s+domain="root"\s+path="app_webview[^"]*"\s*/>""")

    @Test fun `app_webview و توکن در هیچ راهِ پشتیبانِ اندروید نیستند`() {
        val rules = body(file("src/main/res/xml/data_extraction_rules.xml"))
        val legacy = body(file("src/main/res/xml/backup_rules.xml"))
        for ((where, xml) in listOf(
            "cloud-backup" to section(rules, "cloud-backup"),
            "device-transfer" to section(rules, "device-transfer"),
            "full-backup-content" to section(legacy, "full-backup-content"),
        )) {
            assertTrue("$where: بخش پیدا نشد", xml.isNotBlank())
            assertTrue("$where: app_webview باید بیرون بماند", excludesWebview.containsMatchIn(xml))
            assertFalse("$where: app_webview نباید include شود", includesWebview.containsMatchIn(xml))
        }
    }

    @Test fun `allowBackup خاموش است و هر دو پروندهٔ قاعده وصل‌اند`() {
        val manifest = body(file("src/main/AndroidManifest.xml"))
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertFalse("usesCleartextTraffic نباید قاعدهٔ شبکه را دور بزند", manifest.contains("usesCleartextTraffic=\"true\""))
    }

    @Test fun `انتشار فقط HTTPS و گواهیِ سیستم؛ http فقط در debug`() {
        val release = body(file("src/main/res/xml/network_security_config.xml"))
        assertTrue(release.contains("""<base-config cleartextTrafficPermitted="false">"""))
        assertFalse("انتشار هیچ دامنه‌ای را برای http باز نمی‌کند", release.contains("""cleartextTrafficPermitted="true""""))
        assertFalse("انتشار گواهیِ کاربر را نمی‌پذیرد", release.contains("""src="user""""))
        val debug = body(file("src/debug/res/xml/network_security_config.xml"))
        assertTrue(debug.contains("""cleartextTrafficPermitted="true""""))
    }

    @Test fun `صفحه به http نیاز ندارد: CSP ِ connect-src فقط https و wss و self است`() {
        val html = listOf("../../index.html", "../index.html", "index.html").map { File(it) }.firstOrNull { it.isFile }?.readText()
            ?: error("index.html پیدا نشد (cwd=${File(".").absoluteFile})")
        val csp = Regex("""http-equiv="Content-Security-Policy"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: error("CSP در index.html پیدا نشد")
        val connect = csp.split(';').map { it.trim() }.firstOrNull { it.startsWith("connect-src") }
            ?: error("connect-src در CSP نیست")
        for (src in connect.split(Regex("\\s+")).drop(1)) {
            assertTrue("connect-src: $src", src == "'self'" || src.startsWith("https://") || src.startsWith("wss://") || src == "blob:" || src == "data:")
        }
        val main = file("src/main/java/af/tohid/shop/MainActivity.kt")
        assertFalse("محتوای مختلط نباید باز شود", main.contains("MIXED_CONTENT_ALWAYS_ALLOW") || main.contains("MIXED_CONTENT_COMPATIBILITY_MODE"))
        assertTrue("صفحه از https بار می‌شود", main.contains("web.loadUrl(\"https://\$DOMAIN/index.html\")"))
    }
}

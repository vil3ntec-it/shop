package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.sync.SyncStore

/**
 *  سدِ اشتراک (`VipGate`): قابلیتِ قفل‌شده به صفحهٔ «قفل» ِ نسخهٔ ۳ (`LockedFeature`) می‌رود. صفحهٔ قدیمیِ قیمت‌ها و
 *  اشتراک (بی‌راه از نسخهٔ ۳؛ جایش `ui/v3/account/subscription`) برداشته شد (LOC-06).
 */

/**
 *  قفلِ قابلیت‌ها — روشن.
 *
 *  ── قاعده، از زبانِ صاحب مخزن ──────────────────────────────────────
 *  «کسانی که حساب می‌سازند فقط همان هفت روزِ آزمایشی را داشته باشند، و
 *  کسانی که حساب ندارند قفل باشند — چون نه اشتراکی دارند نه حسابی.»
 *
 *  پس چهار حالت، و هر کدام یک جواب:
 *
 *   • نسخه‌ای که به هیچ سروری بسته نیست → **باز**. آن نسخه فروشی نیست و
 *     قفلش یعنی برنامه‌ای که هیچ‌کس نمی‌تواند استفاده کند.
 *   • حساب ندارد → **قفل**. همان خواسته.
 *   • اشتراک یا دورهٔ آزمایشی دارد (`ACTIVE`/`GRACE`) → **باز**.
 *   • تمام شده یا نامعتبر (`EXPIRED`/`INVALID`) → **قفل**.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  و یک حالتِ پنجم که عمداً **باز** است: حساب دارد ولی هیچ مجوزی از سرور
 *  نگرفته (`NONE`). این یعنی سرور هنوز مجوز صادر نمی‌کند — مثلاً تازه
 *  بالا آمده یا نسخه‌اش قدیمی است. قفل کردنِ اینجا، صاحبِ دکان را به‌خاطرِ
 *  اشکالِ سرورِ خودش از برنامه بیرون می‌اندازد؛ و آن‌وقت راهی هم برای
 *  درست کردنش ندارد، چون خودِ برنامه قفل است. وقتی سرور مجوزِ آزمایشی را
 *  درست صادر کرد، همین یک حالت هم بسته می‌شود.
 */
private const val LOCKING = true

@Composable
fun VipGate(label: String, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  val enforcing = LOCKING && AppConfig.isConfigured(context)
  //  «حساب دارد یا نه» یک بار پرسیده می‌شود، نه با هر بار کشیده شدنِ صفحه
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.tokens(context).signedIn }
  val status = remember(enforcing, LicenseGuard.changes.intValue, SubscriptionPulse.at) { LicenseGuard.status(context, state) }
  val open = when {
    !enforcing -> true
    //  کدِ آفلاینِ همین کامپیوتر — بی اینترنت و حتی پیش از ساختنِ حساب
    LicenseGuard.offline(context, state) != null -> true
    !signedIn -> false
    status.state == License.State.ACTIVE -> true
    status.state == License.State.GRACE -> true
    //  حساب دارد ولی سرور هنوز مجوزی نداده — شرحش سرِ `LOCKING`؛ اما اگر
    //  سرور صریحاً گفته «اشتراکی نیست»، دیگر «هنوز» نیست و قفل است
    status.state == License.State.NONE -> state.serverDenied == null
    else -> false
  }

  if (open) {
    content()
    return
  }

  //  بازسازیِ VILL3N (G7): همان سدِ بالا، ظاهرِ کیتِ نسخهٔ ۳ — بی طلا؛ کلید به مسیرِ دقیق
  ir.vil3ntec.tohid.ui.v3.account.subscription.LockedFeature(label, signedIn, state.serverDenied)
}

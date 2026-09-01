package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  «دکانت کدام است؟» — گامی که اصلاً وجود نداشت.
 *
 *  ── اشکالی که این می‌بندد ─────────────────────────────────────────
 *  سرور بدونِ دکان هیچ داده‌ای نمی‌گیرد و نمی‌دهد: هر دو مسیرِ
 *  همگام‌سازی پشتِ `requireShop` هستند و به حسابِ بی‌دکان
 *  `no_shop` (۴۰۳) می‌دهند. مجوزِ اشتراک هم `reason: "no_shop"`
 *  برمی‌گرداند، یعنی اشتراک هم فعال نمی‌شود.
 *
 *  ولی هیچ‌جای برنامهٔ اندروید دکان ساخته نمی‌شد. `ShopRepository.create`
 *  نوشته شده بود و هیچ‌کس صدایش نمی‌زد. یعنی هر کسی که تازه ثبت‌نام
 *  می‌کرد:
 *
 *      • دفترش هیچ‌وقت روی سرور نمی‌نشست
 *      • روی گوشیِ دوم هیچ‌چیز نمی‌دید
 *      • اشتراک هم برایش فعال نمی‌شد
 *
 *  و هیچ پیامی هم نمی‌گرفت — فقط همگام‌سازی بی‌صدا رد می‌شد. نسخهٔ وب
 *  این گام را داشت؛ اندروید نداشت.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  دو راه، چون دو جور آدم وارد می‌شوند:
 *
 *    • **صاحبِ دکان** یک دکان می‌سازد و مالکش می‌شود.
 *    • **شاگرد** کدی را می‌زند که صاحبِ دکان به او داده و روی همان دکان
 *      می‌نشیند.
 *
 *  چرا دکان خودکار ساخته نمی‌شود: اگر برای هر ثبت‌نام یک دکان می‌ساختیم،
 *  شاگرد دیگر نمی‌توانست به دکانِ صاحبش بپیوندد — سرور پیوستنِ کسی را که
 *  از قبل عضوِ دکانِ دیگری است رد می‌کند. پس همین یک سؤال پرسیده می‌شود.
 */
@Composable
fun ShopSetupScreen(store: ShopStore, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shops = remember(context) { Backend.shop(context) }
  val colors = Shop.colors

  //  نامِ دکان اگر از قبل در تنظیمات گذاشته شده، همان پیشنهاد می‌شود
  val savedName = remember {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
      .getString("store_name", "").orEmpty()
  }

  var name by rememberSaveable { mutableStateOf(savedName) }
  var code by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  /** بعد از ساختن یا پیوستن: دفتر به همین دکان سند می‌خورد و بالا می‌رود */
  suspend fun settle(shopId: String) {
    runCatching { LedgerOwner.shopChanged(context, store, shopId) }
    ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    onDone()
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.bg)
      .verticalScroll(rememberScrollState())
      .imePadding()
      .padding(horizontal = 24.dp, vertical = 36.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TohidMark(Modifier.size(56.dp))
    Spacer(Modifier.height(18.dp))
    Text(
      "دکانت را بسازیم",
      style = MaterialTheme.typography.headlineSmall,
      color = colors.text,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "تا وقتی دکانی ثبت نشده، اطلاعات روی سرور ذخیره نمی‌شود و روی " +
        "گوشی دوم هم دیده نمی‌شود.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
      textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(28.dp))

    /* ---------------------- صاحبِ دکان ---------------------- */
    Text(
      "صاحب دکان هستید؟",
      style = MaterialTheme.typography.labelLarge,
      color = colors.text,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    PillField(
      value = name,
      onValueChange = { name = it; error = null },
      placeholder = "نام دکان — مثلاً «فروشگاه توحید»",
      icon = Icons.Filled.Storefront,
    )
    Spacer(Modifier.height(10.dp))
    Button(
      onClick = {
        busy = true; error = null
        scope.launch {
          shops.create(name.trim().ifBlank { "دکان من" })
            .onSuccess { settle(it.shop?.id.orEmpty()) }
            .onFailure { error = it.userText("دکان ساخته نشد") }
          busy = false
        }
      },
      enabled = !busy,
      modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (busy) "…" else "دکان من را بساز") }

    Spacer(Modifier.height(26.dp))
    HorizontalDivider(color = colors.fieldBorder)
    Spacer(Modifier.height(26.dp))

    /* ---------------------- شاگرد ---------------------- */
    Text(
      "شاگرد دکان هستید؟",
      style = MaterialTheme.typography.labelLarge,
      color = colors.text,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    PillField(
      value = code,
      onValueChange = { code = it.uppercase(); error = null },
      placeholder = ir.vil3ntec.tohid.data.StaffCode.HINT,
      icon = Icons.Filled.Badge,
      ltr = true,
      keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
      onClick = {
        val entered = ir.vil3ntec.tohid.data.StaffCode.clean(code)
        if (!ir.vil3ntec.tohid.data.StaffCode.looksValid(entered)) {
          error = "این کد درست نیست. کد باید مثل ${ir.vil3ntec.tohid.data.StaffCode.HINT} باشد."
          return@OutlinedButton
        }
        busy = true; error = null
        scope.launch {
          //  اینجا کاربر از قبل وارد حسابی است (این صفحه بعد از ورود
          //  می‌آید)، پس همان حساب به دکان می‌پیوندد
          shops.join(entered)
            .onSuccess {
              it.role?.let { role -> ir.vil3ntec.tohid.data.ShopRole.remember(context, role) }
              settle(it.shop?.id.orEmpty())
            }
            .onFailure { error = it.userText("پیوستن به دکان انجام نشد") }
          busy = false
        }
      },
      enabled = !busy && code.isNotBlank(),
      modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("پیوستن به دکان") }

    error?.let {
      Spacer(Modifier.height(14.dp))
      Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger, textAlign = TextAlign.Center)
    }

    Spacer(Modifier.height(22.dp))
    /*
     *  راهِ فرار.
     *
     *  برنامه بدونِ سرور کامل کار می‌کند و کسی که فعلاً نمی‌خواهد
     *  تصمیم بگیرد نباید پشتِ این صفحه گیر کند. دفعهٔ بعد که برنامه باز
     *  شود دوباره پرسیده می‌شود.
     */
    TextButton(onClick = onDone, enabled = !busy) {
      Text("بعداً — فعلاً روی همین گوشی کار می‌کنم", color = colors.muted)
    }
  }
}

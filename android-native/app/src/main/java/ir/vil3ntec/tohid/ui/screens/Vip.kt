package ir.vil3ntec.tohid.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money

/**
 *  اشتراک و قیمت‌ها — همان چیزی که نسخهٔ وب دارد.
 *
 *  نشانِ طلایی بالای داشبورد وضعیت را می‌گوید و با زدنش صفحهٔ قیمت‌ها باز
 *  می‌شود. پلن‌ها و قابلیت‌ها عیناً همان‌هایی هستند که وب نشان می‌دهد، پس
 *  کسی که آنجا قیمت دیده، اینجا همان را می‌بیند.
 *
 *  پرداخت بیرون از برنامه است: هماهنگی از راه واتساپ، همان‌طور که بود.
 */

private const val WHATSAPP = "93792236008"
private const val BUY_MESSAGE = "سلام، می‌خواهم اشتراک برنامه توحید را بخرم."

/** قابلیت‌هایی که همیشه باز است */
private val FREE_FEATURES = listOf(
  "انبار و موجودی",
  "مصارف دکان",
  "خریداری و تأمین‌کننده",
  "گزارش‌ها و سود",
  "دفتر رویدادها",
  "پشتیبان‌گیری",
  "خروجی اکسل",
)

/** قابلیت‌هایی که با اشتراک باز می‌شود */
private val PAID_FEATURES = listOf(
  "فروش (صندوق)",
  "قرض‌داران",
  "اسکنر بارکد",
  "چند کاربر روی یک دکان",
)

private data class Plan(
  val title: String,
  val price: Int,
  val badge: String = "",
  val days: Int,
)

private val PLANS = listOf(
  Plan("ماهانه", 500, days = 30),
  Plan("۶ ماهه", 2500, badge = "پیشنهاد ما", days = 180),
  //  فقط **یک** نشان روی کارتِ میانی. دو نشانِ رقیب («پیشنهاد ما» و
  //  «بیشترین صرفه») یعنی هیچ‌کدام؛ صرفه‌ی سالانه زیرِ کارت‌ها به‌صورت
  //  درصد گفته می‌شود.
  Plan("۱ ساله", 4000, days = 365),
)

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

/* ============================== رنگِ برند ============================== */

/*
 *  ── طلا رفت ────────────────────────────────────────────────────────
 *  این صفحه یک پالتِ جدا داشت: طلاییِ ثابت که از تم نمی‌آمد. کنارِ
 *  بنفش و نعناییِ برنامه، طلا مثلِ چیزی از یک برنامه‌ی دیگر دیده می‌شد.
 *  حالا همان نقش‌ها را رنگِ خودِ برند بازی می‌کند — و چون این رنگ‌ها
 *  بیرونِ کامپوز تعریف می‌شوند، همان مقدارهای پالت اینجا نوشته شده‌اند.
 *  ────────────────────────────────────────────────────────────────────
 */
private val BRAND_PALE = Color(0xFFB9A6FF)
private val BRAND_SOFT = Color(0xFFA78BFF)
private val BRAND = Color(0xFF7C5CFF)
private val BRAND_DEEP = Color(0xFF5B3FE0)
private val BRAND_MINT = Color(0xFF00C39A)
private val BRAND_INK = Color(0xFFFFFFFF)

/** خودِ فلز: روشن، سیر، دوباره روشن — نه یک زردِ تخت */
private val BRAND_SWEEP = Brush.linearGradient(
  listOf(BRAND_SOFT, BRAND, BRAND_PALE, BRAND_DEEP)
)

/**
 *  درخششِ یک‌باره — نه نبضِ همیشگی.
 *
 *  کارت‌ها هاله‌ای داشتند که بی‌وقفه کم‌وزیاد می‌شد. در صفحه‌ای که کارش
 *  «انتخاب کن» است، حرکتِ همیشگی کمک نمی‌کند؛ حواس را می‌برد. حالا
 *  هاله یک بار هنگام آمدنِ صفحه روشن می‌شود و همان‌جا می‌ماند.
 */
@Composable
private fun brandPulse(): Float {
  var lit by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { lit = true }
  val value by animateFloatAsState(
    targetValue = if (lit) 1f else 0f,
    animationSpec = tween(if (Motion.enabled) 900 else 1, easing = EaseInOutSine),
    label = "pulse",
  )
  return value
}

/**
 *  لبهٔ طلاییِ درخشان.
 *
 *  یک خطِ تنها فقط یک خط است؛ درخشش از لایه‌لایه بودن می‌آید — سه هالهٔ
 *  پهن و کم‌رنگِ بیرون از کادر، و بعد خودِ خط. چون هاله بیرونِ کادر
 *  کشیده می‌شود، این باید **پیش از** `clip` بیاید وگرنه بریده می‌شود.
 *
 */

private fun Modifier.brandEdge(radius: Dp, pulse: Float, strong: Boolean = false): Modifier =
  drawBehind {
    val lift = if (strong) 1f else 0.62f
    listOf(8f to 0.05f, 5f to 0.08f, 2.5f to 0.13f).forEach { (grow, alpha) ->
      val g = grow.dp.toPx()
      drawRoundRect(
        color = BRAND.copy(alpha = alpha * lift * (0.55f + pulse * 0.45f)),
        topLeft = Offset(-g, -g),
        size = Size(size.width + g * 2f, size.height + g * 2f),
        cornerRadius = CornerRadius(radius.toPx() + g, radius.toPx() + g),
        style = Stroke(width = g),
      )
    }
    val line = (if (strong) 1.8f else 1.2f) + pulse * 0.4f
    val inset = line / 2f
    drawRoundRect(
      brush = Brush.linearGradient(listOf(BRAND_PALE, BRAND_DEEP, BRAND_SOFT, BRAND_DEEP)),
      topLeft = Offset(inset.dp.toPx(), inset.dp.toPx()),
      size = Size(size.width - line.dp.toPx(), size.height - line.dp.toPx()),
      cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
      style = Stroke(width = line.dp.toPx()),
    )
  }

/** بلندیِ نوارِ نشان — در همهٔ کارت‌ها یکی، حتی آن‌که نشان ندارد */
private val BADGE_BAND = 26.dp

/** بلندیِ نوارِ بالای ستونِ مقایسه — همین‌طور، در هر دو ستون یکی */
private val RIBBON_BAND = 28.dp

/**
 *  نشانِ طلاییِ بالای کارت — «پیشنهاد ما» و «بیشترین صرفه».
 */
@Composable
private fun BrandBadge(text: String) {
  // روی گوشی نشان باید در عرضِ یک‌سومِ صفحه بنشیند: نه تاجِ کنارِ متن، نه
  // فاصلهٔ پهن. «بیشترین صرفه» با آن دو، از کارت بیرون می‌زد.
  val wide = isTablet()
  Row(
    Modifier
      .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
      .background(BRAND_SWEEP)
      .padding(horizontal = if (wide) 11.dp else 7.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (wide) {
      Icon(
        Icons.Filled.WorkspacePremium,
        contentDescription = null,
        tint = BRAND_INK,
        modifier = Modifier.size(12.dp),
      )
    }
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      color = BRAND_INK,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      // اگر روی صفحه‌ای خیلی باریک هم جا نشد، کوتاه شود؛ سرریز شدن روی
      // کارتِ بغلی از سه‌نقطه بدتر است
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
    )
  }
}

/* ============================ صفحهٔ قیمت‌ها ============================ */

/**
 *  صفحهٔ اشتراک — همان چیدمانی که در طرح هست.
 *
 *  ترتیبش عمدی است: اول «هفت روز رایگان» چون اولین سؤالِ هر کسی این است
 *  که «باید همین حالا پول بدهم؟»؛ بعد مقایسهٔ رایگان و VIP تا معلوم شود
 *  دقیقاً چه چیزی باز می‌شود؛ بعد مدت‌ها؛ و آخر راهِ خرید.
 *
 *  فهرستِ قابلیت‌ها در هر دو ستون **کامل** است، نه فقط آنچه هر پلن دارد.
 *  ستونِ رایگان همان چهار قلمِ آخر را با قفل نشان می‌دهد؛ اگر نشانشان
 *  ندهیم، کاربر نمی‌فهمد با اشتراک چه چیزی به‌دست می‌آورد.
 */
@Composable
fun VipScreen(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  val signedIn = !state.accessToken.isNullOrBlank()
  val scope = rememberCoroutineScope()
  // پیش‌فرض روی «پیشنهاد ما» است؛ کسی که تصمیم ندارد، همان را می‌گیرد
  var chosenPlan by rememberSaveable { mutableStateOf(PLANS[1].title) }

  fun buy(planTitle: String) {
    val text = Uri.encode("$BUY_MESSAGE ($planTitle)")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$WHATSAPP?text=$text"))
    runCatching { context.startActivity(intent) }
  }

  fun openWhatsApp() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$WHATSAPP"))
    runCatching { context.startActivity(intent) }
  }

  val colors = Shop.colors

  Box(Modifier.fillMaxSize().background(colors.bg)) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
    ) {
      TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = colors.primary)
        Spacer(Modifier.width(6.dp))
        Text("بازگشت", color = colors.primary)
      }

      /*
       *  یک کارت، نه دو کارتِ متناقض.
       *
       *  ── چه چیزی را می‌بندد ────────────────────────────────────────
       *  بالای این صفحه دو کارت پشتِ سرِ هم می‌نشست: یکی می‌گفت
       *  «اشتراک رو به پایان است — ۷ روز مانده» و همان زیر، دیگری
       *  می‌گفت «۷ روز رایگان — حساب بسازید و امتحان کنید». برای کسی
       *  که حساب دارد و وسطِ دورهٔ آزمایشی‌اش است، این دو با هم یعنی
       *  هیچ: کدام؟ تازه شروع شده یا تمام می‌شود؟
       *
       *  حالا یکی از این دو ساخته می‌شود، نه هر دو: وضعیتِ واقعی اگر
       *  حسابی در کار باشد، و دعوت به دورهٔ آزمایشی اگر نباشد.
       *  ────────────────────────────────────────────────────────────
       */
      val hasStatus = remember {
        val st = runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull()
        st != null && st.state != License.State.NONE
      }
      /*
       *  و اگر مجوزی روی گوشی نیست ولی سرور می‌گوید دورهٔ آزمایشی باز
       *  است، نه کارتِ وضعیت ساخته می‌شد نه چیزی جز «۷ روز رایگان
       *  بگیرید» — یعنی به کسی که همین حالا وسطِ همان هفته است،
       *  پیشنهادِ گرفتنش را می‌داد. حالا حالتِ سومی هست.
       */
      LaunchedEffect(Unit) { SubscriptionPulse.refresh(context) }
      val trialOn = SubscriptionPulse.active && SubscriptionPulse.days > 0
      when {
        hasStatus -> SubscriptionState()
        trialOn -> TrialState(SubscriptionPulse.days, SubscriptionPulse.trial)
        else -> TrialInvite()
      }

      Spacer(Modifier.height(14.dp))

      /*
       *  مقایسه‌ی «رایگان در برابر VIP» برداشته شد.
       *
       *  دو ستونِ روبه‌روی هم، کاربر را وامی‌داشت چهارده قلم را دو به دو
       *  بخواند تا بفهمد چه چیزی ندارد. تنها چیزی که به تصمیم کمک
       *  می‌کند، **تفاوت‌هاست**: چهار قلمی که با اشتراک باز می‌شوند.
       */
      Spacer(Modifier.height(16.dp))
      VipHero()

      Spacer(Modifier.height(16.dp))
      Panel(Modifier.fillMaxWidth()) {
        Text("با اشتراک چه چیزی باز می‌شود", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Spacer(Modifier.height(10.dp))
        BenefitRow(Icons.Filled.PointOfSale, "فروش و صندوق", colors.primary)
        BenefitRow(Icons.Filled.Groups, "قرض‌داران و پیگیریِ وعده", colors.primary)
        BenefitRow(Icons.Filled.QrCodeScanner, "اسکنر بارکد", colors.accent)
        BenefitRow(Icons.Filled.People, "چند کاربر روی یک دکان", colors.accent, last = true)
        Spacer(Modifier.height(8.dp))
        FreeFeaturesRow()
      }

      Spacer(Modifier.height(20.dp))
      Text(
        "مدت اشتراک را انتخاب کنید",
        style = MaterialTheme.typography.titleSmall,
        color = colors.text,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))

      /*
       *  هر سه مدت کنارِ هم، روی گوشی هم.
       *
       *  یک بار روی گوشی زیرِ هم گذاشتمشان چون سه کارت در عرضِ یک گوشی
       *  یعنی هر کدام حدودِ صد نقطه؛ ولی زیرِ هم بودن، مقایسه را از بین
       *  می‌برد: کاربر باید اسکرول می‌کرد تا قیمتِ دوم را کنارِ اولی
       *  به‌یاد بیاورد. جایش خودِ کارت روی صفحهٔ باریک جمع‌وجور می‌شود —
       *  فاصله‌های کمتر، قلمِ کوچک‌تر و «افغانی» زیرِ عدد نه کنارش.
       */
      Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(if (isTablet()) 10.dp else 7.dp),
      ) {
        PLANS.forEach { plan ->
          PlanCard(
            plan = plan,
            selected = chosenPlan == plan.title,
            onClick = { chosenPlan = plan.title },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            stretch = true,
          )
        }
      }

      /* ---------------------- هشدارِ ورود ---------------------- */
      if (!signedIn) {
        Spacer(Modifier.height(16.dp))
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.warningTint)
            .border(1.dp, colors.warning.copy(alpha = 0.55f), RoundedCornerShape(Radius.md))
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(colors.warning),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
              "برای خرید اشتراک اول وارد حساب شوید",
              style = MaterialTheme.typography.labelLarge,
              color = colors.text,
              fontWeight = FontWeight.Bold,
            )
            Text(
              "کار رایگان با برنامه حساب نمی‌خواهد — فقط خرید اشتراک لازم دارد.",
              style = MaterialTheme.typography.labelSmall,
              color = colors.muted,
            )
          }
        }
      }

      /* ---------------------- گرفتنِ اشتراک ---------------------- */
      val picked = PLANS.find { it.title == chosenPlan } ?: PLANS[1]

      //  صرفه‌جویی نسبت به ماهانه — با انتخاب عوض می‌شود
      val monthly = PLANS.first().price
      val saving = if (picked.days > 30 && monthly > 0) {
        val full = monthly * (picked.days / 30)
        if (full > picked.price) ((full - picked.price) * 100 / full) else 0
      } else 0
      if (saving > 0) {
        Spacer(Modifier.height(10.dp))
        Text(
          "${saving.fa()}٪ ارزان‌تر از ماهانه",
          fontSize = 11.sp,
          color = colors.accent,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.height(16.dp))
      BuyButton(
        text = "گرفتن ${picked.title} — ${plain(picked.price)} ؋",
        onClick = {
          /*
           *  درخواستِ خرید، همین‌جا ثبت می‌شود.
           *
           *  تا امروز خرید فقط یک پیامِ واتساپ بود: مدیر باید از روی
           *  پیام می‌فهمید چه کسی چه پلنی خواسته و دستی فعالش می‌کرد.
           *  سرور از اول `purchase-request` را داشت. حالا اگر کاربر
           *  وارد شده باشد، درخواست ثبت می‌شود **و** واتساپ هم باز
           *  می‌شود — پیام برای هماهنگیِ پول لازم است، ولی دیگر تنها
           *  ردِ ماجرا نیست.
           */
          if (signedIn) {
            //  ثبتِ درخواست بی‌صداست: اگر نگرفت، واتساپ همچنان باز
            //  می‌شود و کاربر سرِ راهش نمی‌ماند
            scope.launch { Backend.account(context).requestPurchase(picked.title, "از برنامهٔ اندروید") }
          }
          buy(picked.title)
        },
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "بدون قرارداد. بدون ریسک. هماهنگی و پرداخت از راه واتساپ انجام می‌شود.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )

      /*
       *  پرداخت آنلاین — گفته می‌شود که نیست.
       *
       *  کاربری که دنبالِ دکمهٔ «پرداخت با کارت» می‌گردد و پیدایش
       *  نمی‌کند، فکر می‌کند برنامه ناقص است. یک خط که بگوید «فعلاً
       *  نیست»، همان سؤال را جواب می‌دهد.
       */
      Spacer(Modifier.height(10.dp))
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(colors.surface2.copy(alpha = 0.5f))
          .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          Icons.Filled.CreditCardOff,
          contentDescription = null,
          tint = colors.muted2,
          modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
          "پرداخت آنلاین فعلاً ناموجود است",
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted2,
        )
      }

      /* ---------------------- راه‌های تماس ---------------------- */
      Spacer(Modifier.height(22.dp))
      Text(
        "راه‌های تماس",
        style = MaterialTheme.typography.titleSmall,
        color = colors.text,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(10.dp))
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(colors.surface)
          .border(1.dp, colors.border, RoundedCornerShape(Radius.md))
          .clickable { openWhatsApp() }
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF25D366)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text("واتساپ", style = MaterialTheme.typography.labelLarge, color = colors.text)
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text("0792236008", style = MaterialTheme.typography.labelSmall, color = colors.muted)
          }
        }
      }
    }
  }
}

/**
 *  اشتراکِ همین حالا — چند روز مانده، و از کِی.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  صفحهٔ اشتراک فقط قیمت‌ها را نشان می‌داد. کاربری که اشتراک داشت و
 *  می‌آمد ببیند «چند روز مانده»، هیچ‌جا جوابش را پیدا نمی‌کرد: نه در
 *  این صفحه، نه در تنظیمات. تنها نشانه، عددِ داخلِ زنگ بود که فقط از
 *  هفت روز به پایین می‌آمد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  از یک هفته به پایین کلِ کارت قرمز می‌شود — همان مرزی که نشانِ سربرگ
 *  هم با آن قرمز می‌شود (`SUBSCRIPTION_WARN_DAYS`)، تا دو جا یک حرف بزنند.
 */
/**
 *  دعوت به دورهٔ آزمایشی — فقط برای کسی که هنوز حسابی ندارد.
 *
 *  حرکتش یک «تنفس» است، نه خطِ لغزان: حاشیه و هالهٔ کارت آرام کم‌وزیاد
 *  می‌شوند. چیزی که چشم را می‌گیرد ولی نمی‌آزارد، و در صفحه‌ای که کارِ
 *  اصلی‌اش «انتخاب کن» است، همان اندازه توجه لازم دارد.
 */
@Composable
private fun TrialInvite() {
  val colors = Shop.colors
  val pulse = brandPulse()
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(colors.surface)
      .border(
        (1f + pulse * 0.8f).dp,
        colors.primary.copy(alpha = 0.28f + pulse * 0.34f),
        RoundedCornerShape(Radius.md),
      )
      .padding(16.dp)
  ) {
    Text(
      "۷ روز رایگان",
      style = MaterialTheme.typography.titleSmall,
      color = colors.primary,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      "حساب بسازید و همهٔ قابلیت‌ها را ۷ روز رایگان امتحان کنید. " +
        "اطلاعاتی که ثبت می‌کنید در حساب خودتان می‌ماند.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
  }
}

/**
 *  وضعیتِ دورهٔ آزمایشی — از زبانِ سرور، نه از مجوزِ گوشی.
 *
 *  همان کارتِ دعوت است، اما به‌جای «بگیرید» می‌گوید «داری، و این‌قدر
 *  مانده». زیرِ یک هفته سرخ می‌شود، مثل هر جای دیگرِ برنامه
 *  (`SUBSCRIPTION_WARN_DAYS`).
 */
@Composable
private fun TrialState(days: Int, trial: Boolean) {
  val colors = Shop.colors
  val urgent = days <= SUBSCRIPTION_WARN_DAYS
  val tint = if (urgent) colors.danger else colors.success
  val pulse = brandPulse()
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(if (urgent) colors.dangerTint else colors.surface)
      .border(
        (1f + pulse * 0.8f).dp,
        tint.copy(alpha = 0.30f + pulse * 0.34f),
        RoundedCornerShape(Radius.md),
      )
      .padding(16.dp)
  ) {
    Text(
      if (trial) "دورهٔ آزمایشی" else "اشتراک فعال است",
      style = MaterialTheme.typography.titleSmall,
      color = tint,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      if (days > 0) "${days.fa()} روز مانده" else "امروز آخرین روز است",
      style = MaterialTheme.typography.headlineSmall,
      color = colors.text,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      if (trial) "تا پایانِ این مدت همهٔ قابلیت‌ها باز است؛ برای ادامه، یکی از پلن‌های پایین را بگیرید."
      else "برای ادامه، پیش از تمام شدن تمدید کنید.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
  }
}

@Composable
private fun SubscriptionState() {
  val context = LocalContext.current
  val colors = Shop.colors
  val status = remember {
    runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull()
  } ?: return
  //  «هیچ اشتراکی نبوده» حالت نیست که کارت بخواهد: قیمت‌های پایینِ
  //  همین صفحه خودشان جواب‌اند
  if (status.state == License.State.NONE) return

  val days = status.daysLeft()
  val ends = status.payload?.subscriptionEndsAt ?: 0L
  val expired = status.state == License.State.EXPIRED || status.state == License.State.GRACE
  val urgent = expired || (status.state == License.State.ACTIVE && days <= SUBSCRIPTION_WARN_DAYS)

  val tint = when {
    urgent -> colors.danger
    status.state == License.State.ACTIVE -> colors.success
    else -> colors.warning
  }
  val title = when (status.state) {
    License.State.ACTIVE -> if (urgent) "اشتراک رو به پایان است" else "اشتراک فعال است"
    License.State.GRACE -> "اشتراک تمام شده — مهلتِ تمدید"
    License.State.EXPIRED -> "اشتراک تمام شده"
    License.State.PENDING -> "اشتراک هنوز شروع نشده"
    else -> "مجوزِ اشتراک خوانده نشد"
  }
  val big = when {
    expired -> "تمدید کنید"
    status.state == License.State.ACTIVE && days > 0 -> "${days.fa()} روز مانده"
    status.state == License.State.ACTIVE -> "امروز آخرین روز است"
    else -> "—"
  }

  Spacer(Modifier.height(12.dp))
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(if (urgent) colors.dangerTint else colors.surface)
      .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(Radius.md))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (urgent) Icons.Filled.HourglassBottom else Icons.Filled.WorkspacePremium,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = colors.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(3.dp))
      Text(big, style = MaterialTheme.typography.titleSmall, color = tint, fontWeight = FontWeight.Bold)
      if (ends > 0) {
        Spacer(Modifier.height(3.dp))
        Text(
          (if (expired) "پایان: " else "تا تاریخ ") + formatMillis(ends),
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted,
        )
      }
      status.payload?.planTitle?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(2.dp))
        Text("پلن: $it", style = MaterialTheme.typography.labelSmall, color = colors.muted2)
      }
    }
  }
}

/**
 *  دکمهٔ خرید.
 *
 *  تا حالا دکمهٔ آبیِ همیشگیِ برنامه بود و متنش خوانده نمی‌شد: رنگِ متنِ
 *  آن دکمه یک سرمه‌ایِ تقریباً سیاه است که روی زمینهٔ آبیِ تیره‌اش گم
 *  می‌شود. اینجا زمینه طلاست و متن قهوه‌ایِ تیره — بیشترین اختلافی که
 *  می‌شد گذاشت — و بلندیِ دکمه هم بسته به متن باز می‌شود، پس عنوانِ
 *  بلندِ «گرفتن اشتراک ۱ ساله — 4000 افغانی» بریده نمی‌شود.
 */
@Composable
private fun BuyButton(text: String, onClick: () -> Unit) {
  val pulse = brandPulse()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.97f else 1f,
    animationSpec = Springs.press,
    label = "press",
  )
  val shape = RoundedCornerShape(Radius.sm)

  Box(
    Modifier
      .fillMaxWidth()
      .height(54.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .brandEdge(Radius.sm, pulse, strong = true)
      .clip(shape)
      .background(Brush.linearGradient(listOf(BRAND, BRAND_MINT)))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    /*
     *  متنِ دکمه با انتخاب عوض می‌شود و از پایین بالا می‌آید — تا معلوم
     *  باشد همین دکمه است که تغییر کرده، نه اینکه دکمه‌ی دیگری آمده.
     */
    AnimatedContent(
      targetState = text,
      transitionSpec = {
        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
      },
      label = "buyText",
    ) { label ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Filled.WorkspacePremium,
          contentDescription = null,
          tint = BRAND_INK,
          modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          label,
          fontSize = 15.sp,
          color = BRAND_INK,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}


/**
 *  یک ستونِ مقایسه — رایگان یا VIP.
 *
 *  قابلیتِ بسته با قفل نشان داده می‌شود، نه با نبودن در فهرست: فهرستی که
 *  کوتاه‌تر است فقط «کمتر» به نظر می‌رسد؛ قفل می‌گوید **چه چیزی** کم است.
 */
@Composable
private fun TierCard(
  title: String,
  price: String,
  priceNote: String,
  note: String,
  features: List<Pair<String, Boolean>>,
  highlighted: Boolean,
  footer: String,
  footerTint: Color,
  modifier: Modifier = Modifier,
  ribbon: String = "",
) {
  val colors = Shop.colors
  val pulse = brandPulse()
  //  ستونِ VIP تا وقتی انگشت رویش است نور می‌دهد؛ ستونِ رایگان ساکن است
  val tap = remember { MutableInteractionSource() }
  val touched by tap.collectIsPressedAsState()
  val bodyShape =
    if (ribbon.isNotBlank()) RoundedCornerShape(bottomStart = Radius.md, bottomEnd = Radius.md)
    else RoundedCornerShape(Radius.md)

  Column(modifier) {
    /*
     *  نوارِ نشان همیشه جا می‌گیرد، حتی در ستونی که نشان ندارد.
     *
     *  وگرنه ستونِ نشان‌دار به‌اندازهٔ همان نوار پایین‌تر شروع می‌شد و دو
     *  ستون از بالا با هم تراز نبودند — همان چیزی که در عکس دیده می‌شد.
     */
    if (ribbon.isNotBlank()) {
      Box(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md))
          .height(RIBBON_BAND)
          .background(BRAND_SWEEP),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = BRAND_INK,
            modifier = Modifier.size(13.dp),
          )
          Text(
            ribbon,
            style = MaterialTheme.typography.labelSmall,
            color = BRAND_INK,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
          )
        }
      }
    } else {
      Spacer(Modifier.height(RIBBON_BAND))
    }

    Column(
      Modifier
        .fillMaxWidth()
        .weight(1f)
        .then(if (highlighted) Modifier.brandEdge(Radius.md, pulse, strong = true) else Modifier)
        .then(if (highlighted) Modifier.edgeSparks(touched, BRAND) else Modifier)
        .clip(bodyShape)
        .background(colors.surface)
        .then(
          if (highlighted) Modifier
          else Modifier.border(1.dp, colors.border, bodyShape)
        )
        .then(
          //  زدنِ این ستون جایی نمی‌برد؛ فقط نورش را روشن می‌کند —
          //  مدت را کاربر از کارت‌های پایین انتخاب می‌کند
          if (highlighted) Modifier.clickable(
            interactionSource = tap, indication = null,
          ) {}
          else Modifier
        )
        .padding(14.dp),
    ) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.text,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          price,
          style = MaterialTheme.typography.headlineSmall,
          color = if (highlighted) BRAND_DEEP else colors.primary,
          fontWeight = FontWeight.Bold,
        )
        if (priceNote.isNotBlank()) {
          Spacer(Modifier.width(4.dp))
          Text(priceNote, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
      }
      Text(note, style = MaterialTheme.typography.labelSmall, color = colors.muted)

      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = if (highlighted) BRAND.copy(alpha = 0.45f) else colors.border)
      Spacer(Modifier.height(10.dp))

      features.forEach { (name, on) ->
        Row(
          Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // در ستونِ VIP خودِ تیک‌ها هم طلایی‌اند؛ ستونی که «همه‌چیز» را
          // می‌دهد نباید با همان آبیِ ستونِ رایگان علامت بخورد
          Box(
            Modifier
              .size(18.dp)
              .clip(CircleShape)
              .then(
                if (on && highlighted) Modifier.background(BRAND_SWEEP)
                else Modifier.background(if (on) colors.primary else colors.surface2)
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (on) Icons.Filled.Check else Icons.Filled.Lock,
              contentDescription = null,
              tint = when {
                on && highlighted -> BRAND_INK
                on -> Color.White
                else -> colors.muted2
              },
              modifier = Modifier.size(11.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) colors.text else colors.muted2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // پاورقی همیشه کفِ کارت می‌نشیند، پس در دو ستونِ هم‌قد روبه‌روی هم است
      Spacer(Modifier.height(12.dp))
      Spacer(Modifier.weight(1f))
      Box(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.sm))
          .background(footerTint.copy(alpha = 0.14f))
          .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          footer,
          style = MaterialTheme.typography.labelSmall,
          color = footerTint,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/**
 *  یک مدتِ اشتراک.
 *
 *  «روزی حدود …» عمداً هست: ۴۰۰۰ ؋ در سال بزرگ به نظر می‌رسد،
 *  ۱۱ ؋ در روز نه — و هر دو یک عددند.
 *
 *  کارتِ نشان‌دار طلایی است: لبهٔ درخشان، برقی که روی سطح می‌لغزد، و
 *  نشانِ طلا بالای آن.
 */
@Composable
private fun PlanCard(
  plan: Plan,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /**
   *  آیا این کارت تا کفِ ردیف کشیده شود.
   *
   *  فقط وقتی درست است که ردیف قدِ معلومی داشته باشد — یعنی
   *  `Modifier.height(IntrinsicSize.Min)`. در ستونی که بلندیِ در دسترسش
   *  بی‌نهایت است (هر ستونِ اسکرول‌شونده)، `weight` به بچه‌اش **صفر**
   *  بلندی می‌دهد و کارت اصلاً دیده نمی‌شود.
   */
  stretch: Boolean = false,
) {
  val colors = Shop.colors
  val perDay = plan.price.toDouble() / plan.days
  val golden = plan.badge.isNotBlank()
  val pulse = brandPulse()
  val press = remember { MutableInteractionSource() }
  val touched by press.collectIsPressedAsState()
  val shape = RoundedCornerShape(Radius.md)

  // سه کارت در عرضِ یک گوشی یعنی هر کدام حدودِ صد نقطه؛ با فاصله‌های
  // تبلت، متن‌ها بریده می‌شوند. پس کارت روی صفحهٔ باریک جمع می‌شود.
  val wide = isTablet()

  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    /*
     *  نوارِ نشان همیشه همین بلندی را می‌گیرد، حتی در کارتی که نشان
     *  ندارد. کارتِ «ماهانه» نشان ندارد و بدونِ این فاصله، بالاترش
     *  می‌نشست و با آن دو تای دیگر تراز نبود.
     */
    Box(Modifier.height(BADGE_BAND), contentAlignment = Alignment.BottomCenter) {
      if (golden) BrandBadge(plan.badge)
    }

    Column(
      Modifier
        .fillMaxWidth()
        .then(if (stretch) Modifier.weight(1f) else Modifier)
        //  انتخاب‌شده: کمی بزرگ‌تر، تینتِ بنفش و بوردرِ بنفش — سه نشانه
        //  که با هم، بی‌خواندنِ متن هم معلوم می‌کنند کدام انتخاب شده
        .selectScale(selected)
        .then(if (golden) Modifier.brandEdge(Radius.md, pulse, strong = selected) else Modifier)
        .clip(shape)
        .background(if (selected) colors.primary.copy(alpha = 0.18f) else colors.surface)
        .border(
          if (selected) 1.6.dp else 1.dp,
          if (selected) colors.primary else colors.border,
          shape,
        )
        .clickable(
          interactionSource = press,
          indication = LocalIndication.current,
          onClick = onClick,
        )
        .padding(horizontal = if (wide) 14.dp else 8.dp, vertical = if (wide) 14.dp else 11.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        plan.title,
        style = if (wide) MaterialTheme.typography.titleSmall
        else MaterialTheme.typography.labelLarge,
        color = colors.text,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        plain(plan.price),
        style = if (wide) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.titleSmall,
        color = if (golden) BRAND_DEEP else colors.primary,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
      // «افغانی» روی گوشی زیرِ عدد می‌رود نه کنارش: کنارِ هم بودنشان،
      // عرضی می‌خواهد که در یک‌سومِ صفحهٔ گوشی نیست
      Text(
        "؋",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
        maxLines = 1,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        // روی گوشی «حدود» برداشته می‌شود؛ همان عدد را می‌گوید با جای کمتر
        if (wide) "روزی حدود ${money(perDay)} ؋" else "روزی ${money(perDay)}",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(10.dp))
      if (stretch) Spacer(Modifier.weight(1f))
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.sm))
          .then(
            when {
              selected && golden -> Modifier.background(BRAND_SWEEP)
              selected -> Modifier.background(colors.primaryTint)
              else -> Modifier.background(colors.surface2)
            }
          )
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // تیک فقط روی صفحهٔ پهن؛ در کارتِ باریک، همان چند نقطه فرقِ جا
        // شدن و نشدنِ «انتخاب شد» است
        if (wide) {
          Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = when {
              selected && golden -> BRAND_INK
              selected -> colors.primary
              else -> colors.muted2
            },
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(5.dp))
        }
        Text(
          if (selected) "انتخاب شد" else "انتخاب",
          style = MaterialTheme.typography.labelSmall,
          color = when {
            selected && golden -> BRAND_INK
            selected -> colors.primary
            else -> colors.muted
          },
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun VipGate(label: String, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  val enforcing = LOCKING && AppConfig.isConfigured(context)
  //  «حساب دارد یا نه» یک بار پرسیده می‌شود، نه با هر بار کشیده شدنِ صفحه
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.tokens(context).signedIn }
  val status = remember(enforcing) { LicenseGuard.status(context, state) }
  val open = when {
    !enforcing -> true
    !signedIn -> false
    status.state == License.State.ACTIVE -> true
    status.state == License.State.GRACE -> true
    //  حساب دارد ولی سرور هنوز مجوزی نداده — شرحش سرِ `LOCKING`
    status.state == License.State.NONE -> true
    else -> false
  }

  if (open) {
    content()
    return
  }

  var sheet by remember { mutableStateOf(false) }

  /*
   *  صفحهٔ قفل، وسطِ صفحه.
   *
   *  قبلاً یک ستونِ تمام‌عرض بود با متن‌های چسبیده به یک لبه؛ روی تبلت
   *  همه‌چیز به یک کنار می‌رفت و وسطِ صفحه خالی می‌ماند. حالا یک کارتِ
   *  با پهنای محدود است که وسط می‌ایستد — روی گوشی و تبلت یک‌شکل.
   */
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .background(Shop.colors.surface)
        .border(1.dp, Shop.colors.fieldBorder.copy(alpha = 0.5f), RoundedCornerShape(Radius.lg))
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      /*
       *  قفلِ خاکستریِ نفس‌کشنده رفت.
       *
       *  این صفحه خبرِ بد نمی‌دهد؛ می‌گوید چه چیزی با اشتراک باز
       *  می‌شود. پس همان مربعِ برندِ صفحه‌ی اشتراک، با تاج — نه یک قفلِ
       *  خاکستری که تپش هم دارد.
       */
      Box(
        Modifier
          .size(66.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(Brush.linearGradient(listOf(BRAND, BRAND_MINT))),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.WorkspacePremium,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(Modifier.height(16.dp))
      /*
       *  «حساب نداری» و «اشتراکت تمام شده» دو چیزِ متفاوت‌اند و کارِ
       *  بعدیِ کاربر هم در هر دو فرق دارد. یک متنِ مشترک برای هر دو،
       *  کسی را که فقط باید ثبت‌نام کند سرِ صفحهٔ قیمت‌ها می‌فرستاد.
       */
      Text(
        if (!signedIn) "برای «$label» باید حساب بسازید"
        else "«$label» با اشتراک باز می‌شود",
        style = MaterialTheme.typography.titleMedium,
        color = Shop.colors.text,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        if (!signedIn)
          "ساختنِ حساب مجانی است و هفت روز آزمایشی دارد — با آن همه‌چیز باز می‌شود. " +
            "از کلیدِ حساب در بالای صفحه حساب بسازید. بقیهٔ برنامه هم بدونِ حساب باز " +
            "است: انبار، مصارف، خرید، گزارش‌ها و پشتیبان‌گیری."
        else
          "بقیهٔ برنامه — انبار، مصارف، خرید، گزارش‌ها و پشتیبان‌گیری — باز است و همیشه باز می‌ماند.",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(20.dp))
      //  کلید همان صفحهٔ قیمت‌ها را باز می‌کند؛ ساختنِ حساب از سربرگ است
      //  و متنِ بالا همان را می‌گوید — کلیدی که جای دیگری ببرد، دروغ است
      BrandButton("اشتراک و قیمت‌ها") { sheet = true }
    }
  }
  if (sheet) VipScreen { sheet = false }
}

/**
 *  دکمهٔ طلاییِ اشتراک — با همان جرقه‌ای که کارت‌های اشتراک می‌زنند.
 *
 *  دکمهٔ تختِ قبلی کنارِ آن نشانِ متحرک، مرده به نظر می‌رسید: همان کار را
 *  می‌کرد ولی نمی‌گفت که همان چیز است.
 */
@Composable
private fun BrandButton(text: String, onClick: () -> Unit) {
  val press = remember { MutableInteractionSource() }
  val touched by press.collectIsPressedAsState()
  Row(
    Modifier
      .edgeSparks(touched, BRAND)
      .clip(RoundedCornerShape(26.dp))
      .background(
        Brush.linearGradient(
          listOf(Color(0xFFF6D36B), Color(0xFFE0A92C), Color(0xFFF8E39A), Color(0xFFD9982A))
        )
      )
      .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick)
      .padding(horizontal = 22.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Icon(
      Icons.Filled.WorkspacePremium,
      contentDescription = null,
      tint = Color(0xFF4A3208),
      modifier = Modifier.size(18.dp),
    )
    Text(text, color = Color(0xFF3A2705), fontWeight = FontWeight.Bold)
  }
}

/* ==================== اجزای تازه‌ی صفحه‌ی اشتراک ==================== */

/**
 *  هیرو — یک مربعِ نرمِ گرادینتی با تاجِ سفید، و دو خط زیرش.
 *
 *  جای دو ستونِ مقایسه را می‌گیرد: اول بگو چه چیزی می‌فروشی، بعد
 *  بگو چند.
 */
@Composable
private fun VipHero() {
  val colors = Shop.colors
  Column(
    Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier
        .size(64.dp)
        .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = BRAND, spotColor = BRAND)
        .clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(BRAND, BRAND_MINT))),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.WorkspacePremium,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(30.dp),
      )
    }
    Spacer(Modifier.height(12.dp))
    Text("دکان بدون محدودیت", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
    Spacer(Modifier.height(4.dp))
    Text(
      "فروش، قرض‌داران و بارکد — همه باز",
      fontSize = 12.sp,
      color = colors.muted,
      textAlign = TextAlign.Center,
    )
  }
}

/** یک تفاوت — همان چیزی که با اشتراک باز می‌شود */
@Composable
private fun BenefitRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  tint: Color,
  last: Boolean = false,
) {
  val colors = Shop.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
    Spacer(Modifier.width(10.dp))
    Text(title, fontSize = 13.sp, color = colors.text)
  }
  if (!last) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(colors.border.copy(alpha = colors.border.alpha * 0.5f))
    )
  }
}

/** «۷ امکان رایگان هم مثل قبل باقی است» — جمع‌شده، چون خبرِ تازه‌ای نیست */
@Composable
private fun FreeFeaturesRow() {
  val colors = Shop.colors
  var open by remember { mutableStateOf(false) }
  val turn by animateFloatAsState(
    targetValue = if (open) 180f else 0f,
    animationSpec = Springs.press,
    label = "freeChevron",
  )
  Column(Modifier.fillMaxWidth().animateContentSize(Springs.size)) {
    Row(
      Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "${FREE_FEATURES.size.fa()} امکان رایگان هم مثل قبل باقی است",
        fontSize = 11.5.sp,
        color = colors.muted2,
        modifier = Modifier.weight(1f),
      )
      Icon(
        Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = colors.muted2,
        modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = turn },
      )
    }
    if (open) {
      FREE_FEATURES.forEach {
        Text("· $it", fontSize = 11.5.sp, color = colors.muted, modifier = Modifier.padding(vertical = 3.dp))
      }
    }
  }
}

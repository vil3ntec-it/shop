package ir.vil3ntec.tohid.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import ir.vil3ntec.tohid.ui.theme.Shape
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
  Plan("۱ ساله", 4000, badge = "بیشترین صرفه", days = 365),
)

/**
 *  قفلِ قابلیت‌ها — فعلاً خاموش.
 *
 *  تا وقتی روی برنامه کار می‌شود، همه‌چیز باز است تا هر بخش بدونِ حساب و
 *  اشتراک آزمایش شود. برای برگرداندنِ قفل، همین یک خط `true` شود؛ جای
 *  دیگری دست نمی‌خواهد.
 */
private const val LOCKING = false

/* ============================== طلا ============================== */

/*
 *  رنگِ طلا و درخشش‌هایش — یک جا، برای تمامِ این صفحه.
 *
 *  نشانِ «پیشنهاد ما» تا حالا رنگش را از `warning` می‌گرفت، و آن رنگ در
 *  تمِ روشن یک قهوه‌ایِ سوخته است: روی کارت مثلِ لکه دیده می‌شد، نه مثلِ
 *  نشانِ افتخار. طلا از تم نمی‌آید و نباید بیاید — در تمِ روشن و تاریک
 *  هر دو باید همان طلا باشد، وگرنه دیگر طلا نیست.
 */
private val GOLD_PALE = Color(0xFFFFF3C4)
private val GOLD_SOFT = Color(0xFFFBE08A)
private val GOLD = Color(0xFFF6C93F)
private val GOLD_DEEP = Color(0xFFD79A14)
private val GOLD_INK = Color(0xFF3A2705)

/** خودِ فلز: روشن، سیر، دوباره روشن — نه یک زردِ تخت */
private val GOLD_SWEEP = Brush.linearGradient(
  listOf(GOLD_SOFT, GOLD, GOLD_PALE, GOLD_DEEP)
)

/** نبضِ آرام — همهٔ درخشش‌های صفحه با یک ضربان می‌زنند، نه هرکدام جدا */
@Composable
private fun goldPulse(): Float {
  val motion = rememberInfiniteTransition(label = "goldPulse")
  val value by motion.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2200 else 1, easing = FastOutSlowInEasing),
      RepeatMode.Reverse,
    ),
    label = "pulse",
  )
  return value
}

/** برقی که از یک لبه به لبهٔ دیگر می‌لغزد */
@Composable
private fun goldSweep(period: Int = 2800, delay: Int = 800): Float {
  val motion = rememberInfiniteTransition(label = "goldSweep")
  val value by motion.animateFloat(
    initialValue = -0.5f,
    targetValue = 1.5f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) period else 1, delayMillis = delay, easing = LinearEasing),
      RepeatMode.Restart,
    ),
    label = "sweep",
  )
  return value
}

/**
 *  لبهٔ طلاییِ درخشان.
 *
 *  یک خطِ تنها فقط یک خط است؛ درخشش از لایه‌لایه بودن می‌آید — سه هالهٔ
 *  پهن و کم‌رنگِ بیرون از کادر، و بعد خودِ خط. چون هاله بیرونِ کادر
 *  کشیده می‌شود، این باید **پیش از** `clip` بیاید وگرنه بریده می‌شود.
 */
private fun Modifier.goldEdge(radius: Dp, pulse: Float, strong: Boolean = false): Modifier =
  drawBehind {
    val lift = if (strong) 1f else 0.62f
    listOf(8f to 0.05f, 5f to 0.08f, 2.5f to 0.13f).forEach { (grow, alpha) ->
      val g = grow.dp.toPx()
      drawRoundRect(
        color = GOLD.copy(alpha = alpha * lift * (0.55f + pulse * 0.45f)),
        topLeft = Offset(-g, -g),
        size = Size(size.width + g * 2f, size.height + g * 2f),
        cornerRadius = CornerRadius(radius.toPx() + g, radius.toPx() + g),
        style = Stroke(width = g),
      )
    }
    val line = (if (strong) 1.8f else 1.2f) + pulse * 0.4f
    val inset = line / 2f
    drawRoundRect(
      brush = Brush.linearGradient(listOf(GOLD_PALE, GOLD_DEEP, GOLD_SOFT, GOLD_DEEP)),
      topLeft = Offset(inset.dp.toPx(), inset.dp.toPx()),
      size = Size(size.width - line.dp.toPx(), size.height - line.dp.toPx()),
      cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
      style = Stroke(width = line.dp.toPx()),
    )
  }

/** برقی که روی خودِ سطح می‌لغزد — بعد از `clip` بیاید تا داخل بماند */
private fun Modifier.goldShine(progress: Float, strength: Float = 0.3f): Modifier =
  drawWithContent {
    drawContent()
    if (!Motion.enabled) return@drawWithContent
    val x = size.width * progress
    drawRect(
      brush = Brush.linearGradient(
        colors = listOf(Color.Transparent, GOLD_PALE.copy(alpha = strength), Color.Transparent),
        start = Offset(x - size.width * 0.3f, 0f),
        end = Offset(x + size.width * 0.3f, size.height),
      ),
      size = size,
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
private fun GoldBadge(text: String) {
  val sweep = goldSweep(period = 2200, delay = 400)
  Row(
    Modifier
      .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
      .background(GOLD_SWEEP)
      .goldShine(sweep, strength = 0.55f)
      .padding(horizontal = 11.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      Icons.Filled.WorkspacePremium,
      contentDescription = null,
      tint = GOLD_INK,
      modifier = Modifier.size(12.dp),
    )
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      color = GOLD_INK,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
  }
}

/* ============================ نشانِ طلایی ============================ */

/**
 *  نشانِ بالای داشبورد. وضعیت اشتراک را خودش از روی مجوزِ ذخیره‌شده
 *  می‌سنجد، پس هرجا گذاشته شود درست است.
 */
@Composable
fun VipBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  val status = remember {
    License.status(state.license, state.publicKey, state.deviceUid, System.currentTimeMillis())
  }

  val text = when (status.state) {
    License.State.ACTIVE -> "اشتراک فعال"
    License.State.GRACE -> "مهلت تمدید"
    License.State.EXPIRED -> "ارتقا به VIP"
    else -> "اشتراک و قیمت‌ها"
  }

  // تاجِ متحرک — همان تکانِ ملایمِ نسخهٔ وب
  val motion = rememberInfiniteTransition(label = "vip")
  val bob by motion.animateFloat(
    initialValue = -9f,
    targetValue = 9f,
    animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
    label = "bob",
  )

  // برقِ نوری که روی نشان می‌لغزد — همان انیمیشنِ کارت اشتراکِ وب
  val shine by motion.animateFloat(
    initialValue = -0.6f,
    targetValue = 1.6f,
    animationSpec = infiniteRepeatable(
      tween(2600, delayMillis = 900, easing = LinearEasing),
      RepeatMode.Restart,
    ),
    label = "shine",
  )
  // چهار جرقهٔ ریز که از نشان می‌ریزد
  val sparkle by motion.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
    label = "sparkle",
  )

  Box(modifier) {
    Row(
      Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(
          Brush.linearGradient(
            listOf(Color(0xFFF6D36B), Color(0xFFE0A92C), Color(0xFFF8E39A), Color(0xFFD9982A))
          )
        )
        .drawWithContent {
          drawContent()
          // نوارِ نور، مورب، از یک لبه به لبهٔ دیگر
          val x = size.width * shine
          drawRect(
            brush = Brush.linearGradient(
              colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
              start = Offset(x - size.width * 0.25f, 0f),
              end = Offset(x + size.width * 0.25f, size.height),
            ),
            size = size,
          )
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        Icons.Filled.WorkspacePremium,
        contentDescription = null,
        tint = Color(0xFF4A3208),
        modifier = Modifier.size(16.dp).rotate(bob),
      )
      Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF4A3208),
        fontWeight = FontWeight.Bold,
      )
    }

    Canvas(Modifier.matchParentSize()) {
      // هر جرقه با تأخیرِ خودش می‌افتد و محو می‌شود
      listOf(0.16f, 0.38f, 0.62f, 0.83f).forEachIndexed { i, xRatio ->
        val phase = (sparkle + i * 0.25f) % 1f
        val alpha = when {
          phase < 0.12f -> phase / 0.12f
          phase > 0.7f -> ((1f - phase) / 0.3f).coerceIn(0f, 1f)
          else -> 0.9f
        }
        drawCircle(
          color = Color(0xFFFFD970).copy(alpha = alpha * 0.9f),
          radius = 2.5f + (1f - phase) * 1.5f,
          center = Offset(size.width * xRatio, size.height * (0.7f + phase * 0.7f)),
        )
      }
    }
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

      /* ---------------------- هفت روز رایگان ---------------------- */
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(colors.surface)
          .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
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

      Spacer(Modifier.height(14.dp))

      /*
       *  دو ستونِ مقایسه.
       *
       *  `IntrinsicSize.Min` هست چون دو ستونِ ناهم‌قد، مقایسه را سخت
       *  می‌کنند: چشم باید بالا و پایین برود تا بفهمد کدام قلم روبه‌روی
       *  کدام است. حالا هر دو تا یک جا پایین می‌آیند.
       */
      Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TierCard(
          title = "رایگان",
          price = "۰",
          priceNote = "افغانی",
          note = "همیشه رایگان",
          features = FREE_FEATURES.map { it to true } + PAID_FEATURES.map { it to false },
          highlighted = false,
          footer = "همین حالا فعال است",
          footerTint = colors.success,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        TierCard(
          title = "اشتراک VIP",
          price = "همه‌چیز",
          priceNote = "",
          note = "هر مدتی که بخواهید",
          features = (FREE_FEATURES + PAID_FEATURES).map { it to true },
          highlighted = true,
          ribbon = "پیشنهاد ما",
          footer = "مدت را از پایین انتخاب کنید",
          footerTint = GOLD_DEEP,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
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
       *  مدت‌ها: روی صفحهٔ پهن کنارِ هم، روی گوشی زیرِ هم.
       *
       *  سه کارت در عرضِ یک گوشی یعنی هر کدام کمتر از صد نقطه، و آن‌وقت
       *  «روزی حدود ۱۳٫۹ افغانی» دو خط می‌شود و کارت‌ها ناهم‌قد.
       */
      if (isTablet()) {
        Row(
          Modifier.height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          PLANS.forEach { plan ->
            PlanCard(
              plan = plan,
              selected = chosenPlan == plan.title,
              onClick = { chosenPlan = plan.title },
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }
        }
      } else {
        PLANS.forEachIndexed { index, plan ->
          PlanCard(
            plan = plan,
            selected = chosenPlan == plan.title,
            onClick = { chosenPlan = plan.title },
            modifier = Modifier.fillMaxWidth(),
          )
          if (index < PLANS.lastIndex) Spacer(Modifier.height(10.dp))
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
      Spacer(Modifier.height(16.dp))
      val picked = PLANS.find { it.title == chosenPlan } ?: PLANS[1]
      BuyButton(
        text = "گرفتن اشتراک ${picked.title} — ${plain(picked.price)} افغانی",
        onClick = { buy(picked.title) },
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "بدون قرارداد. بدون ریسک. هماهنگی و پرداخت از راه واتساپ انجام می‌شود.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )

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
  val sweep = goldSweep(period = 2600, delay = 600)
  val pulse = goldPulse()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.97f else 1f,
    animationSpec = tween(120, easing = FastOutSlowInEasing),
    label = "press",
  )
  val shape = RoundedCornerShape(28.dp)

  Row(
    Modifier
      .fillMaxWidth()
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .goldEdge(28.dp, pulse, strong = true)
      .clip(shape)
      .background(GOLD_SWEEP)
      .goldShine(sweep, strength = 0.5f)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 15.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(
      Icons.Filled.WorkspacePremium,
      contentDescription = null,
      tint = GOLD_INK,
      modifier = Modifier.size(19.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text,
      style = MaterialTheme.typography.titleSmall,
      color = GOLD_INK,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
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
  val pulse = goldPulse()
  val sweep = goldSweep(period = 3400, delay = 1200)
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
          .background(GOLD_SWEEP)
          .goldShine(sweep, strength = 0.5f),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = GOLD_INK,
            modifier = Modifier.size(13.dp),
          )
          Text(
            ribbon,
            style = MaterialTheme.typography.labelSmall,
            color = GOLD_INK,
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
        .then(if (highlighted) Modifier.goldEdge(Radius.md, pulse, strong = true) else Modifier)
        .clip(bodyShape)
        .background(colors.surface)
        .then(if (highlighted) Modifier.goldShine(sweep, strength = 0.22f) else Modifier)
        .then(
          if (highlighted) Modifier
          else Modifier.border(1.dp, colors.border, bodyShape)
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
          color = if (highlighted) GOLD_DEEP else colors.primary,
          fontWeight = FontWeight.Bold,
        )
        if (priceNote.isNotBlank()) {
          Spacer(Modifier.width(4.dp))
          Text(priceNote, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
      }
      Text(note, style = MaterialTheme.typography.labelSmall, color = colors.muted)

      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = if (highlighted) GOLD.copy(alpha = 0.45f) else colors.border)
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
                if (on && highlighted) Modifier.background(GOLD_SWEEP)
                else Modifier.background(if (on) colors.primary else colors.surface2)
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (on) Icons.Filled.Check else Icons.Filled.Lock,
              contentDescription = null,
              tint = when {
                on && highlighted -> GOLD_INK
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
 *  «روزی حدود …» عمداً هست: ۴۰۰۰ افغانی در سال بزرگ به نظر می‌رسد،
 *  ۱۱ افغانی در روز نه — و هر دو یک عددند.
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
) {
  val colors = Shop.colors
  val perDay = plan.price.toDouble() / plan.days
  val golden = plan.badge.isNotBlank()
  val pulse = goldPulse()
  val sweep = goldSweep(period = 3000, delay = if (golden) 500 else 1500)
  val shape = RoundedCornerShape(Radius.md)

  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    /*
     *  نوارِ نشان همیشه همین بلندی را می‌گیرد، حتی در کارتی که نشان
     *  ندارد. کارتِ «ماهانه» نشان ندارد و بدونِ این فاصله، بالاترش
     *  می‌نشست و با آن دو تای دیگر تراز نبود.
     */
    Box(Modifier.height(BADGE_BAND), contentAlignment = Alignment.BottomCenter) {
      if (golden) GoldBadge(plan.badge)
    }

    Column(
      Modifier
        .fillMaxWidth()
        .weight(1f)
        .then(if (golden) Modifier.goldEdge(Radius.md, pulse, strong = selected) else Modifier)
        .clip(shape)
        .background(colors.surface)
        .then(if (golden) Modifier.goldShine(sweep, strength = 0.25f) else Modifier)
        .then(
          if (golden) Modifier
          else Modifier.border(
            if (selected) 1.6.dp else 1.dp,
            if (selected) colors.primary else colors.border,
            shape,
          )
        )
        .clickable(onClick = onClick)
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        plan.title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.text,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          plain(plan.price),
          style = MaterialTheme.typography.titleMedium,
          color = if (golden) GOLD_DEEP else colors.primary,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Text("افغانی", style = MaterialTheme.typography.labelSmall, color = colors.muted)
      }
      Spacer(Modifier.height(4.dp))
      Text(
        "روزی حدود ${money(perDay)} افغانی",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(10.dp))
      Spacer(Modifier.weight(1f))
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.sm))
          .then(
            when {
              selected && golden -> Modifier.background(GOLD_SWEEP)
              selected -> Modifier.background(colors.primaryTint)
              else -> Modifier.background(colors.surface2)
            }
          )
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = when {
            selected && golden -> GOLD_INK
            selected -> colors.primary
            else -> colors.muted2
          },
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
          if (selected) "انتخاب شد" else "انتخاب",
          style = MaterialTheme.typography.labelSmall,
          color = when {
            selected && golden -> GOLD_INK
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
  val enforcing = LOCKING && state.serverUrl.isNotBlank()
  val status = remember(enforcing) {
    License.status(state.license, state.publicKey, state.deviceUid, System.currentTimeMillis())
  }
  val open = !enforcing ||
    status.state == License.State.ACTIVE ||
    status.state == License.State.GRACE

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
      // قفلِ نفس‌کشنده — صفحهٔ ساکن، خراب به نظر می‌رسد
      val motion = rememberInfiniteTransition(label = "gate")
      val pulse by motion.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
          tween(if (Motion.enabled) 1600 else 1, easing = LinearEasing),
          RepeatMode.Reverse,
        ),
        label = "pulse",
      )
      Box(
        Modifier
          .size(66.dp)
          .graphicsLayer { scaleX = pulse; scaleY = pulse }
          .clip(RoundedCornerShape(22.dp))
          .background(Shop.colors.warningTint),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Lock,
          contentDescription = null,
          tint = Shop.colors.warning,
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(Modifier.height(16.dp))
      Text(
        "«$label» با اشتراک باز می‌شود",
        style = MaterialTheme.typography.titleMedium,
        color = Shop.colors.text,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "بقیهٔ برنامه — انبار، مصارف، خرید، گزارش‌ها و پشتیبان‌گیری — باز است و همیشه باز می‌ماند.",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(20.dp))
      GoldButton("اشتراک و قیمت‌ها") { sheet = true }
    }
  }
  if (sheet) VipScreen { sheet = false }
}

/**
 *  دکمهٔ طلاییِ اشتراک — با همان برقی که روی نشانِ بالای صفحه می‌لغزد.
 *
 *  دکمهٔ تختِ قبلی کنارِ آن نشانِ متحرک، مرده به نظر می‌رسید: همان کار را
 *  می‌کرد ولی نمی‌گفت که همان چیز است.
 */
@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
  val motion = rememberInfiniteTransition(label = "gold")
  val shine by motion.animateFloat(
    initialValue = -0.6f,
    targetValue = 1.6f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2400 else 1, delayMillis = 700, easing = LinearEasing),
      RepeatMode.Restart,
    ),
    label = "goldShine",
  )
  Row(
    Modifier
      .clip(RoundedCornerShape(26.dp))
      .background(
        Brush.linearGradient(
          listOf(Color(0xFFF6D36B), Color(0xFFE0A92C), Color(0xFFF8E39A), Color(0xFFD9982A))
        )
      )
      .drawWithContent {
        drawContent()
        if (!Motion.enabled) return@drawWithContent
        val x = size.width * shine
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
            start = Offset(x - size.width * 0.25f, 0f),
            end = Offset(x + size.width * 0.25f, size.height),
          ),
          size = size,
        )
      }
      .clickable(onClick = onClick)
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

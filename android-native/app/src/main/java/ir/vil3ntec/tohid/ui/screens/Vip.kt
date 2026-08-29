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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
  Plan("۶ ماهه", 2000, badge = "پیشنهاد ما", days = 180),
  Plan("۱ ساله", 3000, badge = "بیشترین صرفه", days = 365),
)

/**
 *  قفلِ قابلیت‌ها — فعلاً خاموش.
 *
 *  تا وقتی روی برنامه کار می‌شود، همه‌چیز باز است تا هر بخش بدونِ حساب و
 *  اشتراک آزمایش شود. برای برگرداندنِ قفل، همین یک خط `true` شود؛ جای
 *  دیگری دست نمی‌خواهد.
 */
private const val LOCKING = false

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  val status = remember {
    License.status(state.license, state.publicKey, state.deviceUid, System.currentTimeMillis())
  }
  val signedIn = !state.accessToken.isNullOrBlank()
  // پیش‌فرض روی «پیشنهاد ما» است؛ کسی که تصمیم ندارد، همان را می‌گیرد
  var chosenPlan by rememberSaveable { mutableStateOf(PLANS[1].title) }

  fun buy(planTitle: String) {
    val text = Uri.encode("$BUY_MESSAGE ($planTitle)")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$WHATSAPP?text=$text"))
    runCatching { context.startActivity(intent) }
  }

  /*
   *  صفحهٔ کامل، نه شیتِ پایینِ صفحه.
   *
   *  شیت روی صفحهٔ زیرش می‌نشست و بلندیِ محدودی داشت؛ سه کارتِ پلن و
   *  توضیح‌هایشان در آن جا نمی‌شد و کاربر داخلِ یک پنجرهٔ کوچک اسکرول
   *  می‌کرد. اشتراک تصمیمِ کوچکی نیست که در گوشهٔ صفحه گرفته شود.
   */
  Box(Modifier.fillMaxSize().background(Shop.colors.bg)) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
    ) {
      TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Shop.colors.primary)
        Spacer(Modifier.width(6.dp))
        Text("بازگشت", color = Shop.colors.primary)
      }
      Spacer(Modifier.height(4.dp))
      /* ---------------------- سربرگ ---------------------- */
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.lg))
          .background(
            Brush.linearGradient(listOf(Shop.colors.primary, Shop.colors.primaryDark))
          )
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
          )
        }
        Spacer(Modifier.height(12.dp))
        Text(
          "قیمت ساده برای مدیریت دکان",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "رایگان شروع کنید. بدون هزینهٔ پنهان.",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.85f),
        )
      }

      Spacer(Modifier.height(14.dp))

      /* ---------------------- وضعیت کنونی ---------------------- */
      val stateText = when (status.state) {
        License.State.ACTIVE -> "اشتراک شما فعال است"
        License.State.GRACE -> "اشتراک تمام شده — مهلت تمدید"
        License.State.EXPIRED -> "اشتراک پایان یافته"
        License.State.PENDING -> "اشتراک هنوز شروع نشده"
        License.State.INVALID -> "مجوز نامعتبر است"
        License.State.NONE -> if (signedIn) "هنوز اشتراکی ندارید" else "۷ روز رایگان"
      }
      val stateDetail = when (status.state) {
        License.State.NONE ->
          if (signedIn) "برای باز شدن فروش، قرض‌داران و اسکنر، یکی از پلن‌های زیر را بگیرید."
          else "حساب بسازید و همهٔ قابلیت‌ها را ۷ روز رایگان امتحان کنید. اطلاعاتی که ثبت می‌کنید در حساب خودتان می‌ماند."
        else -> "قابلیت‌های پولی با اشتراک باز می‌شوند؛ بقیهٔ برنامه همیشه باز است."
      }
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(Shop.colors.primaryTint)
          .padding(14.dp)
      ) {
        Text(
          stateText,
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.primaryDark,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(stateDetail, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
      }

      Spacer(Modifier.height(16.dp))

      /* ---------------------- دو ستون: رایگان و VIP ---------------------- */
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TierCard(
          title = "رایگان",
          price = "۰",
          note = "همیشه رایگان",
          features = FREE_FEATURES.map { it to true } + PAID_FEATURES.map { it to false },
          highlighted = false,
          modifier = Modifier.weight(1f),
        )
        TierCard(
          title = "اشتراک VIP",
          price = "همه‌چیز",
          note = "هر مدتی که بخواهید",
          features = (FREE_FEATURES + PAID_FEATURES).map { it to true },
          highlighted = true,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(18.dp))
      Text(
        "مدت اشتراک را انتخاب کنید",
        style = MaterialTheme.typography.titleSmall,
        color = Shop.colors.text,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(10.dp))

      PLANS.forEachIndexed { index, plan ->
        PlanCard(
          plan = plan,
          selected = chosenPlan == plan.title,
          onClick = { chosenPlan = plan.title },
        )
        if (index < PLANS.lastIndex) Spacer(Modifier.height(10.dp))
      }

      Spacer(Modifier.height(16.dp))
      val picked = PLANS.find { it.title == chosenPlan } ?: PLANS[1]
      TohidButton(
        text = "گرفتن اشتراک ${picked.title} — ${plain(picked.price)} افغانی",
        onClick = { buy(picked.title) },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "هماهنگی و پرداخت از راه واتساپ انجام می‌شود.",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun TierCard(
  title: String,
  price: String,
  note: String,
  features: List<Pair<String, Boolean>>,
  highlighted: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(
        if (highlighted) 2.dp else 1.dp,
        if (highlighted) Shop.colors.primary else Shop.colors.border,
        RoundedCornerShape(Radius.md),
      )
      .padding(14.dp)
  ) {
    if (highlighted) {
      Box(
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Shop.colors.primary)
          .padding(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Text("پیشنهاد ما", style = MaterialTheme.typography.labelSmall, color = Color.White)
      }
      Spacer(Modifier.height(8.dp))
    }
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    Spacer(Modifier.height(4.dp))
    Text(
      price,
      style = MaterialTheme.typography.headlineSmall,
      color = Shop.colors.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(10.dp))
    features.forEach { (label, on) ->
      Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          if (on) Icons.Filled.Check else Icons.Filled.Lock,
          contentDescription = null,
          tint = if (on) Shop.colors.success else Shop.colors.muted2,
          modifier = Modifier.size(14.dp),
        )
        Text(
          label,
          style = MaterialTheme.typography.labelSmall,
          color = if (on) Shop.colors.text else Shop.colors.muted2,
        )
      }
    }
  }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
  val colors = Shop.colors

  // کارتِ انتخاب‌شده کمی بزرگ‌تر می‌شود و هاله می‌گیرد — همان بازخوردی که
  // می‌گوید «این را انتخاب کردی»، بدونِ آنکه چیزی بنویسیم
  val scale by animateFloatAsState(
    targetValue = if (selected && Motion.enabled) 1.02f else 1f,
    animationSpec = tween(240, easing = FastOutSlowInEasing),
    label = "planScale",
  )
  val borderColor by animateColorAsState(
    targetValue = if (selected) colors.primary else colors.border,
    animationSpec = tween(240),
    label = "planBorder",
  )

  // برقِ ملایمی که فقط روی کارتِ انتخاب‌شده از راست به چپ می‌گذرد
  val shimmer = rememberInfiniteTransition(label = "planShimmer")
  val sweep by shimmer.animateFloat(
    initialValue = -0.6f,
    targetValue = 1.6f,
    animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
    label = "sweep",
  )

  Row(
    Modifier
      .fillMaxWidth()
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .then(if (selected) Modifier.softGlow(Shape.card, colors.glow) else Modifier)
      .clip(Shape.card)
      .background(
        brush = if (selected) {
          Brush.horizontalGradient(
            listOf(colors.primary.copy(alpha = 0.20f), colors.accent.copy(alpha = 0.10f))
          )
        } else {
          Brush.horizontalGradient(listOf(colors.surface, colors.surface))
        }
      )
      .drawBehind {
        if (selected && Motion.enabled) {
          drawRect(
            brush = Brush.horizontalGradient(
              colors = listOf(Color.Transparent, colors.primary.copy(alpha = 0.14f), Color.Transparent),
              startX = sweep * size.width - size.width * 0.35f,
              endX = sweep * size.width + size.width * 0.35f,
            )
          )
        }
      }
      .border(if (selected) 1.4.dp else 0.8.dp, borderColor, Shape.card)
      .clickable(onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // دایرهٔ انتخاب، با تیکی که از وسط باز می‌شود
    val tick by animateFloatAsState(
      targetValue = if (selected) 1f else 0f,
      animationSpec = tween(if (Motion.enabled) 260 else 0, easing = FastOutSlowInEasing),
      label = "tick",
    )
    Box(
      Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(if (selected) colors.primary else colors.surface2)
        .border(1.dp, if (selected) colors.primary else colors.border, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (tick > 0f) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = Color(0xFF04121F),
          modifier = Modifier
            .size(16.dp)
            .graphicsLayer { scaleX = tick; scaleY = tick; alpha = tick },
        )
      }
    }

    Spacer(Modifier.width(14.dp))

    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          plan.title,
          style = MaterialTheme.typography.titleSmall,
          color = colors.text,
          fontWeight = FontWeight.Bold,
        )
        if (plan.badge.isNotBlank()) {
          Spacer(Modifier.width(8.dp))
          TohidBadge(
            text = plan.badge,
            tint = if (selected) colors.primary else colors.accent,
            fill = if (selected) colors.primaryTint else colors.surface2,
          )
        }
      }
      Spacer(Modifier.height(2.dp))
      Text(
        "${plain(plan.days)} روز",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
      )
    }

    // قیمت: عدد بزرگ، واحد کوچک — نگاه اول باید روی رقم بیفتد
    Column(horizontalAlignment = Alignment.End) {
      TohidMoneyText(
        amount = plan.price.toDouble(),
        tint = if (selected) colors.primary else colors.text,
        style = MaterialTheme.typography.headlineSmall,
      )
      val perMonth = plan.price / (plan.days / 30.0)
      if (plan.days > 30) {
        Text(
          "ماهی ${money(perMonth)}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.success,
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

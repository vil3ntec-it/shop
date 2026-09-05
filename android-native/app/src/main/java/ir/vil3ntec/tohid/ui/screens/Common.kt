package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.glassSurface
import ir.vil3ntec.tohid.ui.theme.richGlass

/**
 *  کارت.
 *
 *  همان `.panel` سایت، ولی دیگر «جعبهٔ خط‌کشی‌شده» نیست: سطحی نیمه‌شفاف
 *  با لبهٔ روشنِ بالا که روی زمینهٔ قطبی شناور به نظر می‌رسد.
 *
 *  چون بیشترِ صفحه‌های برنامه از همین استفاده می‌کنند، عوض‌شدنِ همین یک
 *  تابع، ظاهرِ همه‌جا را عوض می‌کند.
 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val colors = Shop.colors
  //  روی گوشیِ ضعیف، سطحِ نیمه‌شفاف روی زمینه‌ی شلوغ خوانا نیست چون مه
  //  ندارد؛ آنجا همان کارت با سطحِ توپر و همان لبه‌ی روشن کشیده می‌شود.
  val tint = if (richGlass()) colors.surface else colors.surfaceSolid
  Column(
    modifier
      .glassSurface(Shape.card, tint, colors.sheen, colors.border, glow = colors.glow)
      .padding(18.dp),
    content = content,
  )
}

/**
 *  کاشیِ عدد — همان .stat-card
 *
 *  `compact` برای وقتی است که چند کاشی در یک ردیف می‌نشینند و سهمِ هرکدام
 *  از عرضِ صفحه کم است: فاصله‌ها و قلم کوچک‌تر می‌شوند و برچسب اجازهٔ دو
 *  خط شدن دارد. با اندازهٔ معمولی، در چهار کاشیِ یک ردیفِ گوشی، عدد از
 *  کاشی بیرون می‌زد.
 */
@Composable
fun StatTile(
  label: String,
  value: String,
  tint: Color = Shop.colors.primary,
  hint: String? = null,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val colors = Shop.colors
  val tint = if (richGlass()) colors.surface else colors.surfaceSolid
  // برچسب، عدد و توضیح هر سه وسطِ کاشی. وقتی چند کاشی کنارِ هم‌اند و
  // متن‌ها به یک لبه چسبیده‌اند، ردیف ناهماهنگ دیده می‌شود — به‌ویژه
  // آنجا که یک برچسب دو خط می‌شود و بقیه یک خط.
  Column(
    modifier
      .glassSurface(Shape.card, tint, colors.sheen, colors.border, glow = colors.glow)
      .padding(if (compact) 10.dp else 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      label,
      style = if (compact) MaterialTheme.typography.labelSmall
      else MaterialTheme.typography.labelMedium,
      color = Shop.colors.muted,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
    Text(
      value,
      style = if (compact) MaterialTheme.typography.titleMedium
      else MaterialTheme.typography.headlineSmall,
      color = tint,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
    if (hint != null) {
      Spacer(Modifier.height(2.dp))
      Text(
        hint,
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = Shop.colors.text,
    modifier = Modifier.padding(bottom = 8.dp),
  )
}

@Composable
fun EmptyNote(text: String) {
  Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 *  صفحه‌هایی که هنوز ساخته نشده‌اند.
 *
 *  عمداً صریح می‌گوید کدام بخش هنوز نیامده. یک صفحهٔ خالی یا نصفه‌کاره،
 *  کاربر را گمراه می‌کند که چیزی خراب است.
 */
@Composable
fun ComingSoon(title: String, detail: String) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
    Text(
      "این بخش در نسخهٔ بعدی می‌آید.",
      style = MaterialTheme.typography.bodyMedium,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      detail,
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted2,
      textAlign = TextAlign.Center,
    )
  }
}

/* ============================ حرکت و انیمیشن ============================ */

/**
 *  ورودِ پلکانیِ ردیف‌ها — همان چیزی که نسخهٔ وب دارد: هر ردیف کمی دیرتر
 *  از ردیفِ بالایی می‌آید، محو و از پایین.
 *
 *  تأخیر سقف دارد؛ وگرنه در فهرستِ صد ردیفی، ردیفِ آخر چند ثانیه بعد
 *  می‌آمد و کاربر فکر می‌کرد برنامه گیر کرده.
 */
@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
  if (!Motion.enabled) { content(); return }
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay((index.coerceAtMost(12) * 40).toLong())
    shown = true
  }
  val progress by animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    label = "entry",
  )
  /*
   *  `Column`، نه `Box`.
   *
   *  جعبه بچه‌هایش را **روی هم** می‌گذارد. صدازننده‌ها معمولاً یک ردیف و
   *  بعد یک `Spacer` می‌دهند تا بینِ ردیف‌ها فاصله بیفتد؛ داخلِ جعبه،
   *  آن فاصله زیرِ خودِ ردیف می‌نشست و کارت‌ها به هم می‌چسبیدند — همان
   *  چیزی که در انبار دیده می‌شد.
   */
  Column(
    Modifier
      .graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 12.dp.toPx()
      }
  ) {
    content()
  }
}

/** فشردنِ ملایمِ دکمه‌ها و کارت‌ها هنگام لمس — مثل :active در وب */
@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.97f else 1f,
    animationSpec = Springs.press,
    label = "press",
  )
  return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 *  بزرگ‌شدنِ چیزی که انتخاب شده — چیپ، پلن، کارت.
 *
 *  فرقش با `pressScale` این است که این یکی «حالت» را نشان می‌دهد نه
 *  «لمس»: تا وقتی انتخاب است، بزرگ می‌ماند.
 */
@Composable
fun Modifier.selectScale(selected: Boolean, scaleTo: Float = 1.03f): Modifier {
  val scale by animateFloatAsState(
    targetValue = if (selected) scaleTo else 1f,
    animationSpec = Springs.press,
    label = "select",
  )
  return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 *  ظاهر شدنِ دکمه‌های شناور — همان fabPop نسخهٔ وب: از ۰٫۶ برابر باز
 *  می‌شود و همان‌جا می‌ایستد.
 */
@Composable
fun Modifier.popIn(delayMillis: Int = 0): Modifier {
  if (!Motion.enabled) return this
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(delayMillis.toLong())
    shown = true
  }
  val scale by animateFloatAsState(
    targetValue = if (shown) 1f else 0.6f,
    animationSpec = tween(340, easing = FastOutSlowInEasing),
    label = "pop",
  )
  return this.graphicsLayer {
    scaleX = scale
    scaleY = scale
    alpha = ((scale - 0.6f) / 0.4f).coerceIn(0f, 1f)
  }
}

/**
 *  ورودِ کادرها — همان modalUp وب: کمی از پایین بالا می‌آید و محو باز
 *  می‌شود. کادری که بی‌مقدمه ظاهر شود، یک‌آن حس می‌دهد صفحه پرید.
 */
@Composable
fun DialogEntry(content: @Composable () -> Unit) {
  if (!Motion.enabled) { content(); return }
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }
  val progress by animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    animationSpec = tween(240, easing = FastOutSlowInEasing),
    label = "modal",
  )
  Box(
    Modifier.graphicsLayer {
      alpha = progress
      translationY = (1f - progress) * 16.dp.toPx()
    }
  ) {
    content()
  }
}

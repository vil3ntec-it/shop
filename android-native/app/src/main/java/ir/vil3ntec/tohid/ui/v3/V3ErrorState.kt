package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStates
import ir.vil3ntec.tohid.core.text.PlainError
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKind
import ir.vil3ntec.tohid.ui.v3.art.V3Illustration
import ir.vil3ntec.tohid.ui.v3.art.V3SceneArt

/*
 *  حالت‌های مشترکِ خالی و خطا (دستهٔ ۶۳، e-rows-63i). یک الگو برای همه: تصویرِ متحرکِ همان زمینه، عنوانِ ساده، یک خطِ
 *  «چه کنید» (اختیاری در خالی، همیشه در خطا) و **یک** کار. متنِ خطا هرگز خام نیست: هر عنوان و خط از `PlainError`
 *  می‌گذرد (نشانی، http، کد، نامِ استثنا و ردِ پشته نه)؛ متنِ فنی ⇒ متنِ پیش‌فرضِ همان گونه از `StrStates`.
 */

/** متنِ پیش‌فرضِ هر گونهٔ خطا */
internal object V3ErrorText {
  fun title(kind: PlainError.Kind): String = when (kind) {
    PlainError.Kind.OFFLINE -> StrStates.offlineTitle
    PlainError.Kind.SERVER -> StrStates.serverTitle
    PlainError.Kind.OTHER -> StrStates.errorTitle
  }

  fun hint(kind: PlainError.Kind): String = when (kind) {
    PlainError.Kind.OFFLINE -> StrStates.offlineHint
    PlainError.Kind.SERVER -> StrStates.serverHint
    PlainError.Kind.OTHER -> StrStates.otherHint
  }

  /** گونهٔ راهنما: از متن؛ وگرنه از تصویر (ابر ⇒ سرور، سیم ⇒ بی اینترنت) */
  fun kind(art: V3ArtKind, vararg raw: String?): PlainError.Kind {
    val k = PlainError.kindOf(*raw)
    return when {
      k != PlainError.Kind.OTHER -> k
      art == V3ArtKind.ERROR_CABLE -> PlainError.Kind.OFFLINE
      art == V3ArtKind.ERROR_CLOUD -> PlainError.Kind.SERVER
      else -> PlainError.Kind.OTHER
    }
  }

  /** پیامِ کوتاهِ خطا (`V3SnackbarHost`): ساده ⇒ همان؛ فنی ⇒ عنوانِ همان گونه */
  fun toast(raw: String): String = PlainError.text(raw) ?: title(PlainError.kindOf(raw))
}

/**
 *  الگوی مشترکِ تمام‌صفحه (دستهٔ ۶۵): کلِ بلوک — صحنهٔ پهنِ ۲:۱، عنوان، خطِ کوتاه و یک کار — در **میانهٔ** جای خالی، هم
 *  افقی هم عمودی (مثلِ صفحهٔ نمونهٔ صاحب‌کار). پهنای صحنه = کمینهٔ (جای خالی − ۳۲dp، ۳۶۰dp). درونِ فهرستِ پیمایشی (بلندیِ
 *  بی‌مرز) همان بلوک بی کشیدگی در جای خودش می‌نشیند.
 */
@Composable
internal fun V3StateLayout(
  art: V3ArtKind,
  title: String,
  modifier: Modifier,
  body: String?,
  action: String?,
  onAction: (() -> Unit)?,
  actionKind: V3ButtonKind = V3ButtonKind.PRIMARY,
) {
  val c = PhoneV3.colors
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag(V3_STATE_BLOCK),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      V3SceneArt(art, Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(2f))
      Spacer(Modifier.height(18.dp))
      Text(title, style = V3Type.headline, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp))
      if (body != null) {
        Spacer(Modifier.height(6.dp))
        Text(body, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 16.dp))
      }
      if (action != null && onAction != null) {
        Spacer(Modifier.height(22.dp))
        V3Button(action, onAction, kind = actionKind)
      }
    }
  }
}

/** برچسبِ آزمون برای بلوکِ حالتِ خالی/خطا (صحنه + متن + کار) — آزمونِ «در میانه است» */
const val V3_STATE_BLOCK = "v3StateBlock"

/**
 *  خطای تمام‌صفحه: صحنهٔ دوستانه (فروشندهٔ دوشاخه به‌دست، باران روی سرور یا «!» ِ سنگی)، عنوانِ ساده، یک خطِ «چه کنید» و یک کار:
 *  «دوباره» ([onRetry]) یا، اگر دوباره معنا ندارد، «پشتیبانی» ([onSupport]). متنِ دوستانهٔ صفحه‌های همگام‌سازی و
 *  سرور (e-rows-63h) همان‌طور که هست نشان داده می‌شود؛ متنِ فنی هرگز.
 *  @param kind گونهٔ خطا؛ null ⇒ از خودِ متن
 */
@Composable
fun V3ErrorState(
  title: String,
  modifier: Modifier = Modifier,
  body: String? = null,
  onRetry: (() -> Unit)? = null,
  onSupport: (() -> Unit)? = null,
  kind: PlainError.Kind? = null,
) {
  val k = kind ?: PlainError.kindOf(title, body)
  val action = when {
    onRetry != null -> StrKit.retry
    onSupport != null -> Str.support
    else -> null
  }
  V3StateLayout(
    art = V3ArtKind.forError(k),
    title = PlainError.text(title) ?: V3ErrorText.title(k),
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    body = PlainError.text(body) ?: V3ErrorText.hint(k),
    action = action,
    onAction = onRetry ?: onSupport,
  )
}

/**
 *  خطای درونِ صفحه (کادرِ خطای `V3Message`): همان کادرِ §۹ با تصویرِ کوچکِ متحرک به‌جای آیکن، متنِ ساده‌شده و یک کارِ
 *  ۴۸dp. عنوانِ فنی ⇒ عنوانِ پیش‌فرض با خطِ «چه کنید».
 */
@Composable
internal fun V3InlineError(
  title: String,
  modifier: Modifier,
  body: String?,
  action: String?,
  onAction: (() -> Unit)?,
) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.tone(c, V3Tone.ERROR)
  val kind = PlainError.kindOf(title, body)
  val plainTitle = PlainError.text(title)
  val shownBody = PlainError.text(body) ?: if (plainTitle == null) V3ErrorText.hint(kind) else null
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier.fillMaxWidth().clip(shape).background(pair.bg).border(1.dp, pair.border, shape)
      .semantics { liveRegion = LiveRegionMode.Polite }
      .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.Top,
  ) {
    V3Illustration(V3ArtKind.forError(kind), size = 40.dp)
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 4.dp)) {
      Text(plainTitle ?: V3ErrorText.title(kind), style = V3Type.body, color = pair.fg)
      if (shownBody != null) Text(shownBody, style = V3Type.footnote, color = pair.fg.copy(alpha = 0.85f))
      if (action != null && onAction != null) {
        Spacer(Modifier.height(8.dp))
        V3Button(action, onAction, kind = V3ButtonKind.SECONDARY, height = 48.dp)
      }
    }
  }
}

package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.PhoneV3DebtColors
import ir.vil3ntec.tohid.ui.theme.shopTypography

/*
 *  قرض‌دارانِ گوشی — r9-debtors.jpg، «همین مدلِ دقیق».
 *
 *  r9 عکسِ همین صفحه پیش از تخت شدنِ صفحه‌های کار است؛ پس این‌جا همان
 *  چیدمان (کارتِ سه‌عددی، کادرِ جستجو با میکروفون، «N مورد» و تراشه‌های
 *  ترتیب، شبکهٔ کارت‌های قرمز/سبز با × و مداد و قرصِ روز) با همان اندازه‌ها
 *  کشیده می‌شود؛ رنگ‌ها از سیستمِ VILL3N و فقط از `PhoneV3Debt` (ui/theme)؛
 *  منطق (جست‌وجو، ترتیب، عددها، روز) فقط از `DebtorListModel`.
 */

/**
 *  اندازهٔ متنِ r9 روی هر دستگاه: همان `shopTypography` با ضریبِ ۱٫۱۲ِ صفحه‌ای
 *  که r9 رویش گرفته شده. پنجره‌ها (افزودن، رسید، …) بیرونِ این‌اند و همان
 *  اندازهٔ همیشه را دارند.
 */
@Composable
fun PhoneDebtorsTheme(content: @Composable () -> Unit) {
  val digits = ir.vil3ntec.tohid.LocaleChoice.digits
  val type = remember(digits) { shopTypography(PhoneV3Debt.typeScale, digits) }
  MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = type, shapes = MaterialTheme.shapes, content = content)
}

/* ------------------------------ زمینه ------------------------------ */

/**
 *  زمینهٔ صفحه — سطحِ صفرِ سیستمِ VILL3N (#F3F6FA، شب #0B1220)، تخت.
 *
 *  r9 روی آرورای بنفش بود؛ از ۱۴۰۵/۰۷/۲۸ رنگ‌ها از سیستمِ VILL3N می‌آید و r9 فقط
 *  شکل است — پس لکه‌های نور رفتند و کارت‌ها با مرزِ دیدنی از زمینه جدا می‌شوند.
 */
@Composable
fun DebtorsBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(modifier.fillMaxSize().background(PhoneV3Debt.colors.page), content = content)
}

/* ------------------------------ سربرگ ------------------------------ */

/**
 *  سربرگ — همان سربرگِ تنظیماتِ گوشی (r5، `V3Header` در PhoneV3Kit ِ شاخهٔ profile):
 *  دکمهٔ گردِ سفیدِ ۴۴dp با سایهٔ نرم و «›» در آغاز، عنوانِ ۱۹sp وسط، بلندیِ ۶۰dp.
 *  تا آن جزء روی شاخهٔ پایه بیاید همین‌جا با همان اندازه‌ها و رنگ‌های `PhoneV3` کشیده
 *  می‌شود؛ بعد از ادغام، این تابع به `V3Header("قرض‌داران", onBack)` بدل می‌شود.
 */
@Composable
fun DebtorsHeader(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
  val v3 = PhoneV3.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
    if (onBack != null) {
      val source = remember { MutableInteractionSource() }
      val pressed by source.collectIsPressedAsState()
      Box(
        Modifier
          .align(Alignment.CenterStart)
          .size(44.dp)
          .shadow(6.dp, CircleShape, clip = false, ambientColor = v3.ink.copy(alpha = 0.10f), spotColor = v3.ink.copy(alpha = 0.10f))
          .pressScale(pressed)
          .clip(CircleShape)
          .background(v3.card)
          .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
          contentDescription = ir.vil3ntec.tohid.core.i18n.StrKit.back,
          tint = v3.icon,
          modifier = Modifier.size(24.dp),
        )
      }
    }
    Text(
      ir.vil3ntec.tohid.core.i18n.Str.debtors,
      style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
      color = v3.ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 52.dp).semantics { heading() },
    )
  }
}

/* ------------------------------ کارتِ سه‌عددی ------------------------------ */

/** «تعداد قرض‌داران» · «جمع تمام قرض‌ها» (قرمز) · «رسیدهای همه» (سبز) — هم‌وزن، بی خطِ جداکننده */
@Composable
fun DebtorsStatsCard(summary: DebtorListModel.Summary, modifier: Modifier = Modifier) {
  val c = PhoneV3Debt.colors
  val shape = RoundedCornerShape(PhoneV3Debt.cardRadius)
  Row(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .background(c.surface)
      .border(1.dp, c.edge, shape)
      .padding(18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StatCell(ir.vil3ntec.tohid.core.i18n.StrMoney.statPeople, plain(summary.people), c.ink, c)
    StatCell(ir.vil3ntec.tohid.core.i18n.StrMoney.statOwed, money(summary.owed), if (summary.owed > 0) c.owe else c.settled, c)
    StatCell(ir.vil3ntec.tohid.core.i18n.StrMoney.statReceived, money(summary.received), c.settled, c)
  }
}

@Composable
private fun RowScope.StatCell(label: String, value: String, tint: Color, c: PhoneV3DebtColors) {
  Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      value,
      style = MaterialTheme.typography.titleSmall,
      color = tint,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

/* ------------------------------ جستجو ------------------------------ */

/**
 *  کادرِ خط‌دارِ بی‌زمینه با ذره‌بین در ابتدا و دایرهٔ سبزآبیِ کم‌رنگِ میکروفون در انتها.
 *
 *  میکروفون همان تشخیصِ گفتارِ گوشی است (`rememberVoiceSearch`)؛ جایی که
 *  نیست (کامپیوتر، گوشیِ بی برنامهٔ گفتار) کشیده نمی‌شود. تا چیزی نوشته
 *  شده، جایش «پاک کردن» است — یک کادر، یک دکمه.
 */
@Composable
fun DebtorsSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3Debt.colors
  //  پیش‌نمایشِ گوشی روی کامپیوتر (`PhoneDebtorsShot.mic`) دایره را همان‌طور که اندروید نشان می‌دهد می‌کشد
  val voice = rememberVoiceSearch(onValueChange) ?: if (PhoneDebtorsShot.mic.value) ({}) else null
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.receivePickHint) },
    //  خانهٔ ۴۸dpِ آیکن‌ها صریح است: گوشی خودش همین را می‌دهد (اندازهٔ لمسیِ کمینه)، ولی
    //  پیش‌نمایشِ کامپیوتر نه — و بی آن ذره‌بین و میکروفون به لبهٔ کادر می‌چسبیدند
    leadingIcon = {
      Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, contentDescription = null) }
    },
    trailingIcon = if (value.isBlank() && voice == null) null else {
      {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
          if (value.isNotBlank()) RoundIcon(Icons.Filled.Close, ir.vil3ntec.tohid.core.i18n.StrKit.clear, Color.Transparent, c.muted) { onValueChange("") }
          else if (voice != null) RoundIcon(Icons.Filled.Mic, ir.vil3ntec.tohid.core.i18n.StrKit.voice, c.accentSoft, c.accentInk) { voice() }
        }
      }
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    shape = RoundedCornerShape(PhoneV3Debt.fieldRadius),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = c.ink,
      unfocusedTextColor = c.ink,
      focusedContainerColor = Color.Transparent,
      unfocusedContainerColor = Color.Transparent,
      focusedBorderColor = c.accentText,
      unfocusedBorderColor = c.fieldEdge,
      focusedLabelColor = c.accentText,
      unfocusedLabelColor = c.muted,
      focusedLeadingIconColor = c.icon,
      unfocusedLeadingIconColor = c.icon,
      cursorColor = c.accentText,
    ),
    modifier = modifier.fillMaxWidth(),
  )
}

/**
 *  فقط برای عکسِ خودکارِ پیش‌نمایشِ گوشی (`DevShot`، صفحه‌های debtors-r9/many/empty)، مثلِ
 *  `PhoneCartShot`: کامپیوتر تشخیصِ گفتار ندارد و میکروفون را پنهان می‌کند، ولی عکس باید همان
 *  گوشی را نشان دهد. در نسخهٔ دستِ کاربر کسی آن را روشن نمی‌کند.
 */
object PhoneDebtorsShot {
  val mic = mutableStateOf(false)
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, fill: Color, tint: Color, onClick: () -> Unit) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  Box(
    Modifier
      .size(PhoneV3Debt.micSize)
      .pressScale(pressed)
      .clip(CircleShape)
      .background(fill)
      .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
  }
}

/* ------------------------------ ترتیب ------------------------------ */

/**
 *  «N مورد» و چهار تراشهٔ ترتیب — انتخاب‌شده رنگِ برندِ پر با ✓ و متنِ روشنِ
 *  پررنگ، بقیه قرصِ سفید. رنگِ تراشه نرم عوض می‌شود و ✓ باز می‌شود؛ روی
 *  گوشیِ باریک ردیف تا لبهٔ صفحه افقی اسکرول می‌شود و تا چیزی پشتِ لبه
 *  مانده، لبهٔ انتها محو می‌شود تا پیدا باشد که ادامه دارد.
 */
@Composable
fun DebtorsSortRow(current: DebtorListModel.Sort, count: Int, onPick: (DebtorListModel.Sort) -> Unit) {
  val c = PhoneV3Debt.colors
  val scroll = rememberScrollState()
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(Modifier.bleed(PhoneV3Debt.gutter)) {
    Row(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(scroll)
        .padding(horizontal = PhoneV3Debt.gutter),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      //  شمارنده تراشه نیست، پس زده نمی‌شود: فقط می‌گوید فهرست چند تاست
      Text(
        ir.vil3ntec.tohid.core.i18n.StrMoney.itemsCount(plain(count)),
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
        modifier = Modifier.padding(end = 2.dp),
      )
      DebtorListModel.Sort.entries.forEach { option ->
        SortChip(option.label, option == current, c) { onPick(option) }
      }
    }
    //  محوِ لبهٔ انتها (چپ در راست‌به‌چپ) — فقط تا وقتی تراشه‌ای پشتِ لبه مانده
    if (scroll.canScrollForward) {
      //  ۱۲dpِ لبه پُر، بعد ۲۸dp محو: تراشهٔ نیمه‌پیدا آشکارا «ادامه دارد» می‌گوید
      val solid = c.page
      val clear = c.page.copy(alpha = 0f)
      val stops = if (rtl) arrayOf(0f to solid, 0.3f to solid, 1f to clear) else arrayOf(0f to clear, 0.7f to solid, 1f to solid)
      Box(
        Modifier
          .align(Alignment.CenterEnd)
          .width(40.dp)
          .height(44.dp)
          .background(Brush.horizontalGradient(*stops))
      )
    }
  }
}

/**
 *  ردیفِ اسکرولی تا لبهٔ صفحه: تراشه‌ها زیرِ لبهٔ گوشی می‌روند، نه زیرِ حاشیهٔ
 *  ۱۶dp — پهنا دو حاشیه بیشتر اندازه گرفته و به همان اندازه به عقب گذاشته می‌شود.
 */
private fun Modifier.bleed(gutter: Dp): Modifier = layout { measurable, constraints ->
  if (!constraints.hasBoundedWidth) return@layout measurable.measure(constraints).let { p -> layout(p.width, p.height) { p.place(0, 0) } }
  val extra = gutter.roundToPx() * 2
  val width = constraints.maxWidth + extra
  val p = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
  layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

@Composable
private fun SortChip(label: String, picked: Boolean, c: PhoneV3DebtColors, onClick: () -> Unit) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val fill by animateColorAsState(if (picked) c.accentActive else c.surface, Springs.effect, label = "chip")
  val edge by animateColorAsState(if (picked) c.accentActive else c.edge, Springs.effect, label = "chip-edge")
  val ink by animateColorAsState(if (picked) c.onAccent else c.muted, Springs.effect, label = "chip-ink")
  val shape = RoundedCornerShape(50)
  Row(
    Modifier
      .pressScale(pressed)
      .clip(shape)
      .background(fill)
      .border(1.dp, edge, shape)
      .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedVisibility(
      visible = picked,
      enter = if (Motion.enabled) fadeIn() + expandHorizontally() else EnterTransition.None,
      exit = if (Motion.enabled) fadeOut() + shrinkHorizontally() else ExitTransition.None,
    ) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = c.onAccent,
        modifier = Modifier.padding(end = 4.dp).size(13.dp),
      )
    }
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = ink,
      fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
      maxLines = 1,
    )
  }
}

/* ------------------------------ کارتِ قرض‌دار ------------------------------ */

/**
 *  کارتِ هر نفر — قرمز یعنی بدهکار است، سبز یعنی حسابش صاف است یا پیشِ ما
 *  موجودی دارد. نام، مبلغِ درشت، شماره (یا «… افغانی بدهکار» / «حساب صاف
 *  است») و قرصِ ساعتِ «N روز» تا وقتی بدهکار است.
 *
 *  × (حذف، با پرسش) بالای کارت و مداد (ویرایش) پایینِ آن است تا انگشت هنگامِ
 *  باز کردنِ حساب رویشان نیفتد. لمسِ طولانی ⇐ منوی همان سه کار.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DebtorTile(
  row: DebtorListModel.Row,
  onOpen: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3Debt.colors
  val tint = if (row.owes) c.owe else c.settled
  //  توپر روی زمینه: لکهٔ نورِ پشتِ کارت متنِ قرمز/سبز را کم‌رنگ نمی‌کند
  val fill = if (row.owes) c.oweTint else c.settledTint
  val shape = RoundedCornerShape(PhoneV3Debt.cardRadius)
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  var menu by remember { mutableStateOf(false) }

  Box(
    modifier
      .fillMaxWidth()
      .pressScale(pressed)
      .clip(shape)
      .background(fill)
      .border(PhoneV3Debt.cardBorder, tint, shape)
      .combinedClickable(
        interactionSource = source,
        indication = LocalIndication.current,
        onLongClick = { menu = true },
        onClick = onOpen,
      )
  ) {
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(text = { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.openAccount) }, onClick = { menu = false; onOpen() })
      DropdownMenuItem(text = { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.edit) }, onClick = { menu = false; onEdit() })
      DropdownMenuItem(text = { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.delete, color = c.owe) }, onClick = { menu = false; onDelete() })
    }
    Column(
      //  پایین جای بیشتری می‌خواهد: مدادِ ویرایش در همان گوشه می‌نشیند
      Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 34.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        row.debtor.name.ifBlank { ir.vil3ntec.tohid.core.i18n.StrMoney.noName },
        style = MaterialTheme.typography.titleSmall,
        color = c.ink,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        money(kotlin.math.abs(row.balance)),
        style = MaterialTheme.typography.titleMedium,
        color = tint,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      //  تا دو خط، وسط‌چین: «۱٬۰۰۰ افغانی بدهکار» در کارتِ ۱۱۴dpِ گوشی کامل دیده می‌شود
      Text(
        DebtorListModel.subtitle(row),
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      DebtorListModel.daysLabel(row)?.let { days ->
        Spacer(Modifier.height(5.dp))
        DaysPill(days, c)
      }
    }

    CornerButton(
      Icons.Filled.Close,
      ir.vil3ntec.tohid.core.i18n.StrMoney.delete,
      onDelete,
      c,
      Modifier.align(Alignment.TopStart).padding(4.dp),
    )
    //  در راست‌به‌چپ، `BottomEnd` همان کنجِ پایین-چپ است
    CornerButton(
      Icons.Filled.Edit,
      ir.vil3ntec.tohid.core.i18n.StrMoney.edit,
      onEdit,
      c,
      Modifier.align(Alignment.BottomEnd).padding(6.dp),
    )
  }
}

/**
 *  قرصِ «N روز» — همیشه خاکستری، مثلِ r9. «بدهکار است» را خطِ قرمزِ کارت می‌گوید؛
 *  قرصِ رنگی کنارش همان حرف را دوباره و بلندتر می‌زد.
 */
@Composable
private fun DaysPill(text: String, c: PhoneV3DebtColors) {
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(c.pill)
      .padding(horizontal = 7.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Icon(Icons.Filled.Schedule, contentDescription = null, tint = c.onPill, modifier = Modifier.size(11.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = c.onPill, fontWeight = FontWeight.Normal, maxLines = 1)
  }
}

/** دکمهٔ ریزِ گوشهٔ کارت — زمینهٔ صفحه با لبهٔ سفید، آیکنِ خاکستری */
@Composable
private fun CornerButton(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
  c: PhoneV3DebtColors,
  modifier: Modifier = Modifier,
) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val shape = RoundedCornerShape(PhoneV3Debt.cornerButtonRadius)
  Box(
    modifier
      .size(PhoneV3Debt.cornerButton)
      .pressScale(pressed)
      .clip(shape)
      .background(c.surface)
      .border(1.dp, c.edge, shape)
      .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = description, tint = c.icon, modifier = Modifier.size(12.dp))
  }
}

/* ------------------------------ خالی و افزودن ------------------------------ */

/** «هنوز قرض‌داری ثبت نشده» / «چیزی پیدا نشد» — روی همان سطحِ سفیدِ کارتِ بالا */
@Composable
fun DebtorsEmptyCard(
  icon: ImageVector,
  title: String,
  description: String,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val c = PhoneV3Debt.colors
  val shape = RoundedCornerShape(PhoneV3Debt.cardRadius)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(c.surface)
      .border(1.dp, c.edge, shape)
      .padding(horizontal = 18.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center)
    if (actionText != null && onAction != null) {
      Spacer(Modifier.height(16.dp))
      val source = remember { MutableInteractionSource() }
      val pressed by source.collectIsPressedAsState()
      Row(
        Modifier
          .pressScale(pressed)
          .clip(RoundedCornerShape(50))
          .background(c.accent)
          .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onAction)
          .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(actionText, style = MaterialTheme.typography.labelLarge, color = c.onAccent, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/**
 *  دکمهٔ گردِ «قرض‌دار تازه» — بیرون از قابِ r9 (پایینِ صفحه)، رنگِ برندِ همان
 *  تراشه. Ctrl+N و فرمانِ `new:` ِ عکس‌برداری هم همین را می‌زنند (`AppNav.addAction`).
 */
@Composable
fun DebtorsAddButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** دفترِ خالی دکمهٔ «قرض‌دار تازه» ِ خودش را دارد؛ آن‌جا دایره کشیده نمی‌شود (Ctrl+N می‌ماند) */
  visible: Boolean = true,
) {
  val c = PhoneV3Debt.colors
  val latest = rememberUpdatedState(onClick)
  DisposableEffect(Unit) {
    val action = { latest.value() }
    ir.vil3ntec.tohid.ui.AppNav.addAction.value = action
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === action) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }
  if (!visible) return
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  //  لایهٔ فشردن ۸dp از دایره بزرگ‌تر است: سایه بیرونِ دایره می‌افتد و لایه (روی کامپیوتر) آن را نمی‌بُرد
  Box(modifier.pressScale(pressed).padding(8.dp)) {
    Box(
      Modifier
        .size(PhoneV3Debt.fabSize)
        .shadow(6.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
        .clip(CircleShape)
        .background(c.accent)
        .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Add, contentDescription = ir.vil3ntec.tohid.core.i18n.StrMoney.newDebtor, tint = c.onAccent, modifier = Modifier.size(26.dp))
    }
  }
}

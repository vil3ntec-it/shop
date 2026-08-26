package af.tohid.shop.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import af.tohid.shop.TohidApp
import af.tohid.shop.data.remote.ApiClient
import af.tohid.shop.data.remote.PlanDto
import af.tohid.shop.data.remote.PlansResponse
import af.tohid.shop.data.remote.WhatsappDto
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

/**
 * دروازه‌ی قابلیت‌های اشتراکی.
 * اگر قابلیت باز باشد محتوا نشان داده می‌شود، وگرنه صفحه‌ی اشتراک.
 *
 * پرداخت داخل برنامه انجام نمی‌شود؛ خرید از راه واتساپ هماهنگ می‌شود.
 */
@Composable
fun VipGate(
    feature: String,
    title: String,
    content: @Composable () -> Unit,
) {
    val app = TohidApp.instance
    var allowed by remember { mutableStateOf(app.entitlement.has(feature)) }

    LaunchedEffect(feature) {
        app.entitlement.refresh()
        allowed = app.entitlement.has(feature)
    }

    if (allowed) content() else SubscriptionScreen(title)
}

/* ================================================================== */
/*  قیمت‌های پیش‌فرض — وقتی سرور در دسترس نیست                        */
/* ================================================================== */

private const val WA_NUMBER = "0792236008"
private const val WA_TEXT = "سلام، می‌خواهم اشتراک برنامه توحید را بخرم."

private fun waUrl(number: String, text: String): String {
    val digits = "93" + number.filter { it.isDigit() }.removePrefix("0")
    return "https://wa.me/$digits?text=" + URLEncoder.encode(text, "UTF-8")
}

private fun approxDays(amount: Int?, unit: String?): Int = when (unit) {
    "day" -> amount ?: 0
    "week" -> (amount ?: 0) * 7
    "month" -> (amount ?: 0) * 30
    "year" -> (amount ?: 0) * 365
    else -> 0
}

private class Spec(val code: String, val title: String, val amount: Int, val unit: String, val price: Int)

/**
 * قیمت‌ها از سرور می‌آیند؛ لینک واتساپ و قیمت روزانه را همین‌جا می‌سازیم
 * تا شماره و متن پیام هم از سرور قابل تغییر باشد.
 */
private fun withLinks(res: PlansResponse, shopId: String, who: String): PlansResponse {
    val number = res.whatsapp.number.ifBlank { WA_NUMBER }
    val text = res.whatsapp.message.ifBlank { WA_TEXT } + identity(shopId, who)
    return res.copy(
        plans = res.plans.map { p ->
            val days = if (p.days > 0) p.days else approxDays(p.amount, p.unit)
            p.copy(
                pricePerDay =
                    if (days > 0 && p.price > 0) Math.round(p.price.toDouble() / days * 10) / 10.0
                    else null,
                whatsappUrl = waUrl(number, "$text (${p.title})"),
            )
        },
        whatsapp = res.whatsapp.copy(number = number, url = waUrl(number, text)),
    )
}

/**
 * شناسه‌ی دکان همراه پیام واتساپ می‌رود تا فروشنده بداند اشتراک را روی
 * کدام دکان فعال کند. کاربر لازم نیست چیزی را دستی کپی کند.
 */
private fun identity(shopId: String, who: String): String {
    if (shopId.isBlank()) return ""
    return "\n\nشناسه دکان: $shopId" + (if (who.isNotBlank()) "\nنام: $who" else "")
}

private fun fallbackPlans(shopId: String, who: String): PlansResponse {
    val specs = listOf(
        Spec("m1", "ماهانه", 1, "month", 500),
        Spec("m6", "۶ ماهه", 6, "month", 2000),
        Spec("y1", "۱ ساله", 1, "year", 3000),
    )
    val badges = mapOf("m6" to "پیشنهاد ما", "y1" to "بیشترین صرفه")
    val plans = specs.map { p ->
        val days = approxDays(p.amount, p.unit)
        PlanDto(
            code = p.code, title = p.title, amount = p.amount, unit = p.unit, price = p.price,
            negotiable = false, badge = badges[p.code].orEmpty(),
            pricePerDay = if (days > 0) Math.round(p.price.toDouble() / days * 10) / 10.0 else null,
            whatsappUrl = waUrl(WA_NUMBER, "$WA_TEXT (${p.title})" + identity(shopId, who)),
        )
    }
    return PlansResponse(
        plans = plans, currency = "افغانی", trialDays = 7,
        whatsapp = WhatsappDto(
            number = WA_NUMBER,
            url = waUrl(WA_NUMBER, WA_TEXT + identity(shopId, who)),
        ),
    )
}

/* ================================================================== */
/*  صفحه‌ی اشتراک                                                     */
/* ================================================================== */

private val FREE_LABELS = listOf(
    "انبار و موجودی", "مصارف دکان", "خریداری و تأمین‌کننده",
    "گزارش‌ها و سود", "دفتر رویدادها", "پشتیبان‌گیری", "خروجی اکسل",
)
private val PAID_LABELS = listOf(
    "فروش (صندوق)", "قرض‌داران", "اسکنر بارکد", "چند کاربر روی یک دکان",
)

@Composable
private fun SubscriptionScreen(title: String) {
    val app = TohidApp.instance
    val uriHandler = LocalUriHandler.current

    var data by remember { mutableStateOf<PlansResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<PlanDto?>(null) }

    LaunchedEffect(Unit) {
        val fromServer = withContext(Dispatchers.IO) {
            runCatching { ApiClient.api(app.session)?.plans() }.getOrNull()
        }
        val shopId = app.session.shopId()
        val who = app.session.userLabel()
        data = fromServer?.takeIf { it.plans.isNotEmpty() }?.let { withLinks(it, shopId, who) }
            ?: fallbackPlans(shopId, who)
        loading = false
    }

    val currency = data?.currency ?: "افغانی"

    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScrollCompat(),
    ) {
        Hero(title)

        Column(Modifier.padding(16.dp)) {
            Notice(app.entitlement.statusText(), Tone.Blue)

            Spacer(Modifier.height(18.dp))
            SectionTitle("رایگان یا اشتراک")
            Spacer(Modifier.height(12.dp))
            TierCard(
                name = "رایگان",
                price = "۰",
                priceSuffix = currency,
                sub = "همیشه رایگان",
                open = FREE_LABELS,
                locked = PAID_LABELS,
                highlighted = false,
                index = 0,
            )
            Spacer(Modifier.height(11.dp))
            TierCard(
                name = "اشتراک VIP",
                price = "همه‌چیز",
                priceSuffix = null,
                sub = "هر مدتی که بخواهید",
                open = FREE_LABELS + PAID_LABELS,
                locked = emptyList(),
                highlighted = true,
                ribbon = "پیشنهاد ما",
                index = 1,
            )

            Spacer(Modifier.height(22.dp))
            SectionTitle("مدت اشتراک را انتخاب کنید")
            Spacer(Modifier.height(12.dp))

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                // سه پلن، سه ستون — همان چیدمان نسخه‌ی وب
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    data?.plans.orEmpty().forEachIndexed { i, plan ->
                        PlanCard(
                            plan = plan,
                            currency = currency,
                            selected = picked?.code == plan.code,
                            index = i,
                            modifier = Modifier.weight(1f),
                        ) { picked = plan }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            CtaBar(picked?.let { "گرفتن اشتراک ${it.title}" } ?: "گرفتن اشتراک") {
                val url = picked?.whatsappUrl?.takeIf { it.isNotBlank() }
                    ?: data?.whatsapp?.url.orEmpty()
                if (url.isNotBlank()) uriHandler.openUri(url)
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("راه‌های تماس")
            Spacer(Modifier.height(12.dp))
            ContactCard(
                icon = TohidIcons.Chat,
                iconBg = Color(0xFFE7F9EE),
                iconFg = Color(0xFF25D366),
                title = "واتساپ",
                body = Format.toFa(data?.whatsapp?.number ?: WA_NUMBER),
            ) {
                data?.whatsapp?.url?.takeIf { it.isNotBlank() }?.let { uriHandler.openUri(it) }
            }
            Spacer(Modifier.height(10.dp))
            ContactCard(
                icon = TohidIcons.Globe,
                iconBg = T.primaryTint,
                iconFg = T.primary,
                title = "پرداخت بیرون از برنامه",
                body = "هماهنگی و پرداخت از راه واتساپ انجام می‌شود.",
            )

            Spacer(Modifier.height(90.dp))
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun Hero(title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(T.primary, T.primaryDark)))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TohidIcons.Crown, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "قیمت ساده برای مدیریت دکان",
            fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
            color = Color.White, textAlign = TextAlign.Center, lineHeight = 34.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "«$title» با اشتراک باز می‌شود. رایگان شروع کنید، بدون هزینه‌ی پنهان.",
            fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center, lineHeight = 23.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = T.text,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TierCard(
    name: String,
    price: String,
    priceSuffix: String?,
    sub: String,
    open: List<String>,
    locked: List<String>,
    highlighted: Boolean,
    index: Int,
    ribbon: String? = null,
) {
    RiseIn(index) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(T.bg)
                .border(
                    if (highlighted) 2.dp else 1.5.dp,
                    if (highlighted) T.primary else T.border,
                    RoundedCornerShape(18.dp),
                ),
        ) {
            if (ribbon != null) {
                Box(
                    Modifier.fillMaxWidth().background(T.primary).padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ribbon, fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
                Text(
                    name, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = T.text,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(price, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = T.primary)
                    if (priceSuffix != null) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            priceSuffix, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = T.primary, modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    sub, fontSize = 11.sp, color = T.muted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                open.forEach { TickRow(it, true) }
                locked.forEach { TickRow(it, false) }
            }
        }
    }
}

@Composable
private fun TickRow(text: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (on) T.primary else T.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (on) TohidIcons.Check else TohidIcons.Lock,
                contentDescription = null,
                tint = if (on) Color.White else T.muted2,
                modifier = Modifier.size(if (on) 10.dp else 11.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = if (on) T.text else T.muted2, lineHeight = 20.sp)
    }
}

@Composable
private fun PlanCard(
    plan: PlanDto,
    currency: String,
    selected: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hasBadge = plan.badge.isNotBlank()
    RiseIn(index, modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) T.primaryTint else T.bg)
                .border(
                    if (selected) 2.dp else 1.5.dp,
                    when {
                        selected -> T.primary
                        hasBadge -> T.warning
                        else -> T.border
                    },
                    RoundedCornerShape(16.dp),
                )
                .clickable { onClick() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasBadge) {
                Box(
                    Modifier.fillMaxWidth().background(T.warning).padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        plan.badge, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                }
            }
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(plan.title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = T.text)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (plan.negotiable) "توافقی" else Format.money(plan.price.toDouble()),
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = T.primary,
                    )
                    if (!plan.negotiable) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            currency, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                            color = T.primary, modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    plan.pricePerDay?.let { "روزی حدود ${Format.number(it)} $currency" }
                        ?: "با ما هماهنگ کنید",
                    fontSize = 10.5.sp, color = T.muted,
                    textAlign = TextAlign.Center, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) T.primary else T.surface)
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        TohidIcons.Check, contentDescription = null,
                        tint = if (selected) Color.White else T.muted,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (selected) "انتخاب شد" else "انتخاب",
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else T.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CtaBar(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(T.primary, T.primaryDark)))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TohidIcons.Gift, contentDescription = null, tint = T.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.weight(1f))
        Text(
            "بدون قرارداد.\nبدون ریسک.",
            fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.End, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ContactCard(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    body: String,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(T.bg)
        .border(1.dp, T.border, RoundedCornerShape(14.dp))
    Row(
        (if (onClick != null) base.clickable { onClick() } else base)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = T.text)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 11.sp, color = T.muted, lineHeight = 19.sp)
        }
    }
}

/** کارت‌ها یکی‌یکی و کمی بعد از هم بالا می‌آیند. */
@Composable
private fun RiseIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 380,
            delayMillis = index * 55,
            easing = LinearOutSlowInEasing,
        ),
        label = "rise",
    )
    Box(
        modifier
            .alpha(progress)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val shift = ((1f - progress) * 14.dp.roundToPx()).toInt()
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, shift)
                }
            }
    ) { content() }
}

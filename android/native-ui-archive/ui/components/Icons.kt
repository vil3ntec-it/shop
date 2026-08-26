package af.tohid.shop.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * آیکون‌های دست‌ساز.
 *
 * هیچ ایموجی در برنامه استفاده نمی‌شود: ایموجی روی هر گوشی شکل و رنگ
 * دیگری دارد، اندازه‌اش با متن جور در نمی‌آید و روی اندروید قدیمی گاهی
 * اصلاً کشیده نمی‌شود. این‌ها همان مسیرهای SVG نسخه‌ی وب‌اند تا دو نسخه
 * دقیقاً یک شکل باشند.
 */
private fun stroked(name: String, pathData: String, width: Float = 1.9f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object TohidIcons {

    val Crown: ImageVector by lazy {
        stroked(
            "Crown",
            "M3 8.5l3.6 2.6L12 4l5.4 7.1L21 8.5 19.2 18H4.8L3 8.5z " +
                "M4.8 20.5h14.4",
        )
    }

    val Lock: ImageVector by lazy {
        stroked(
            "Lock",
            "M6.7 10.5h10.6a1.8 1.8 0 0 1 1.8 1.8v6.4a1.8 1.8 0 0 1-1.8 1.8H6.7" +
                "a1.8 1.8 0 0 1-1.8-1.8v-6.4a1.8 1.8 0 0 1 1.8-1.8z " +
                "M8 10.5V7.8a4 4 0 1 1 8 0v2.7",
        )
    }

    val Check: ImageVector by lazy { stroked("Check", "M4.5 12.6l4.6 4.6L19.5 6.8", 2.8f) }

    val Gift: ImageVector by lazy {
        stroked(
            "Gift",
            "M3.5 11.5h17V20a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 20v-8.5z " +
                "M3.8 7.5h16.4a1.3 1.3 0 0 1 1.3 1.3v1.4a1.3 1.3 0 0 1-1.3 1.3H3.8" +
                "a1.3 1.3 0 0 1-1.3-1.3V8.8a1.3 1.3 0 0 1 1.3-1.3z " +
                "M12 7.5v14 " +
                "M12 7.5S10.6 2.5 8 2.5a2.5 2.5 0 0 0 0 5 " +
                "M12 7.5s1.4-5 4-5a2.5 2.5 0 0 1 0 5",
        )
    }

    val Chat: ImageVector by lazy {
        stroked(
            "Chat",
            "M20.5 12.2c0 4-3.8 7.2-8.5 7.2-1 0-2-.15-2.9-.42L4 20.5l1.6-3.9" +
                "C4.25 15.35 3.5 13.85 3.5 12.2c0-4 3.8-7.2 8.5-7.2s8.5 3.2 8.5 7.2z",
        )
    }

    val Globe: ImageVector by lazy {
        stroked(
            "Globe",
            "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17z " +
                "M3.5 12h17 " +
                "M12 3.5c2.2 2.4 3.3 5.4 3.3 8.5S14.2 18.1 12 20.5 " +
                "M12 3.5C9.8 5.9 8.7 8.9 8.7 12s1.1 6.1 3.3 8.5",
        )
    }
}

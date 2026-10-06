package site.yaft.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import site.yaft.app.track.Category

/** The website's colour tokens (app/globals.css). */
object Yaft {
    val ink = Color(0xFF15161A)
    val ink2 = Color(0xFF3A3D46)
    val ink3 = Color(0xFF5C606B)
    val paper = Color(0xFFFFFFFF)
    val ground = Color(0xFFE6EAEF)
    val groundWash = Color(0xFFF3F5F8)
    val reach = Color(0xFF8A8D95)
    val mapWater = Color(0xFFD3E5EF)
    val danger = Color(0xFFC8262B)
    val dangerTint = Color(0xFFFFE3E1)

    fun hue(c: Category): Color = when (c) {
        Category.DOCKSTART -> Color(0xFFFF8A3D)
        Category.WAKETHIEVE -> Color(0xFFFFC93C)
        Category.WINGFOIL -> Color(0xFF2EC4B6)
        Category.PARAWING -> Color(0xFFB49AF8)
        Category.PARAWING_UPDOWN -> Color(0xFF82A0FF)
        Category.PARAWING_DOWNWIND -> Color(0xFFFF8CC3)
        Category.SUP_DOWNWIND -> Color(0xFF8CD65E)
    }

    /** Every number uses tabular figures. */
    val num = TextStyle(fontFeatureSettings = "tnum")
    val display = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
}

@Composable
fun YaftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Yaft.ink,
            onPrimary = Yaft.paper,
            background = Yaft.ground,
            onBackground = Yaft.ink,
            surface = Yaft.paper,
            onSurface = Yaft.ink,
            onSurfaceVariant = Yaft.ink2,
            outline = Yaft.ink,
            error = Yaft.danger,
        ),
        content = content,
    )
}

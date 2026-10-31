package com.booktracker.ui.theme

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booktracker.R
import kotlin.random.Random

// Add res/font/literata_variable.ttf and res/font/figtree_variable.ttf (both OFL, Google Fonts).
val Literata = FontFamily(
    Font(R.font.literata_variable, FontWeight.Normal),
    Font(R.font.literata_variable, FontWeight.SemiBold),
)
val Figtree = FontFamily(
    Font(R.font.figtree_variable, FontWeight.Normal),
    Font(R.font.figtree_variable, FontWeight.Medium),
    Font(R.font.figtree_variable, FontWeight.SemiBold),
)

private val BoardBlack = Color(0xFF1A1815)
private val PaperLow = Color(0xFF24211C)
private val PaperHigh = Color(0xFF2E2A24)
private val Ink = Color(0xFFEFE7D8)
private val InkMuted = Color(0xFFB3A998)
private val Verdigris = Color(0xFF7FC8B4)
private val OnVerdigris = Color(0xFF10261F)
private val Amber = Color(0xFFF2A65A)
private val Hairline = Color(0x1FEFE7D8)

val PaperColors = darkColorScheme(
    primary = Verdigris,
    onPrimary = OnVerdigris,
    primaryContainer = Color(0xFF2F5249),
    onPrimaryContainer = Color(0xFFD5F3E9),
    secondary = InkMuted,
    onSecondary = BoardBlack,
    secondaryContainer = PaperHigh,
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Color(0xFF2E1A05),
    background = BoardBlack,
    onBackground = Ink,
    surface = BoardBlack,
    onSurface = Ink,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperLow,
    surfaceContainerHigh = PaperHigh,
    outline = Color(0xFF7D7567),
    outlineVariant = Color(0xFF3B362E),
    // Snackbars use these; left unset, they would be Material's default lilac.
    inverseSurface = Color(0xFFEFE7D8),
    inverseOnSurface = Color(0xFF24211C),
    inversePrimary = Color(0xFF2F6B5C),
)

val PaperTypography = Typography(
    displaySmall = TextStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineSmall = TextStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

val PaperShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Sequential ramp for the heatmap, indexed by [com.booktracker.ui.stats.heatLevel]. */
@Immutable
data class ReadingColors(
    val heat: List<Color> = listOf(
        PaperHigh,
        Color(0xFF33665A),
        Color(0xFF4A9483),
        Color(0xFF77C5B1),
        Color(0xFFBFF0E2),
    ),
    val ahead: Color = Verdigris,
    val onPace: Color = Ink,
    val behind: Color = Amber,
    val hairline: Color = Hairline,
)

val LocalReadingColors = staticCompositionLocalOf { ReadingColors() }

@Composable
fun BookTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColors,
        typography = PaperTypography,
        shapes = PaperShapes,
        content = content,
    )
}

/** Fine speckle drawn over a surface, so large dark areas read as paper stock. */
@Composable
fun BoxScope.PaperGrain(seed: Int = 7, alpha: Float = 0.035f) {
    val specks = remember(seed) {
        val random = Random(seed)
        List(1400) { Offset(random.nextFloat(), random.nextFloat()) }
    }
    Canvas(Modifier.matchParentSize()) {
        specks.forEachIndexed { index, speck ->
            drawCircle(
                color = if (index % 2 == 0) Color.White else Color.Black,
                radius = 0.6.dp.toPx(),
                center = Offset(speck.x * size.width, speck.y * size.height),
                alpha = alpha,
            )
        }
    }
}

val DeckFront = PaperLow
val StackSheetNear = Color(0xFF2A2620)
val StackSheetFar = Color(0xFF211E19)

/**
 * A blurred rounded rectangle behind the node. Shadows render on hardware canvases from API 28;
 * below that this draws only [fill], and the cards' hairline edge keeps them defined.
 */
fun Modifier.softShadow(
    color: Color,
    blur: Dp,
    fill: Color,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    corner: Dp = 24.dp,
): Modifier = drawWithCache {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = fill.toArgb()
        setShadowLayer(blur.toPx(), offsetX.toPx(), offsetY.toPx(), color.toArgb())
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                corner.toPx(),
                corner.toPx(),
                paint,
            )
        }
    }
}

/** Raised paper: a dark shadow below right, a faint warm highlight above left, always opaque. */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    color: Color = DeckFront,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .softShadow(Color(0x99000000), 18.dp, color, 8.dp, 10.dp)
            .softShadow(Color(0x0FFFF0D7), 12.dp, color, (-5).dp, (-5).dp)
            .clip(shape)
            .background(color)
            .border(1.dp, Hairline, shape),
    ) {
        PaperGrain()
        content()
    }
}


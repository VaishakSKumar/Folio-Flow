package com.booktracker.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.booktracker.R

// res/font/inter_variable.ttf is Inter (OFL, Google Fonts); each weight is a point on its axis,
// set explicitly because Android otherwise draws every weight at the file's default.
@OptIn(ExperimentalTextApi::class)
private fun interAt(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    interAt(FontWeight.Normal),
    interAt(FontWeight.Medium),
    interAt(FontWeight.SemiBold),
    interAt(FontWeight.Bold),
    interAt(FontWeight.ExtraBold),
)

// Never pure black: depth comes from light on the ground, not from borders.
val Ground = Color(0xFF121212)
val SurfaceTone = Color(0xFF1A1A1A)
val Raised = Color(0xFF1E1E1E)
val Well = Color(0xFF161616)
val Ink = Color(0xFFEDEAE4)
val InkSoft = Color(0xFFA39E96)
val InkFaint = Color(0xFF8F8A83)

/** The accent when there is no cover colour to use. */
val Leather = Color(0xFFB07A52)
val DeepGreen = Color(0xFF4E9A72)
val Alert = Color(0xFFF0A49E)
private val Hairline = Color(0x14EDEAE4)

val RectoColors = darkColorScheme(
    primary = Leather,
    onPrimary = Ground,
    primaryContainer = Color(0xFF3A2A20),
    onPrimaryContainer = Color(0xFFF2DCC8),
    secondary = InkSoft,
    onSecondary = Ground,
    secondaryContainer = Raised,
    onSecondaryContainer = Ink,
    tertiary = Alert,
    onTertiary = Color(0xFF2A1210),
    error = Alert,
    onError = Color(0xFF2A1210),
    background = Ground,
    onBackground = Ink,
    surface = Ground,
    onSurface = Ink,
    surfaceVariant = Raised,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Well,
    surfaceContainerLow = SurfaceTone,
    surfaceContainer = SurfaceTone,
    surfaceContainerHigh = Raised,
    surfaceContainerHighest = Color(0xFF262626),
    outline = InkFaint,
    outlineVariant = Color(0xFF2E2C29),
    // Snackbars use these; left unset, they would be Material's default lilac.
    inverseSurface = Ink,
    inverseOnSurface = SurfaceTone,
    inversePrimary = Color(0xFF7A4A2A),
)

private val Tight = (-0.02).em

val RectoTypography = Typography(
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = Tight),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = Tight),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = Tight),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val RectoShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Sequential ramp for the heatmap, indexed by [com.booktracker.ui.stats.heatLevel]. */
@Immutable
data class ReadingColors(
    val heat: List<Color> = listOf(
        Color(0xFF242322),
        Color(0xFF4A3326),
        Color(0xFF7A5238),
        Leather,
        Color(0xFFE8C29E),
    ),
    val ahead: Color = Color(0xFF7FC9A0),
    val onPace: Color = Ink,
    val behind: Color = Color(0xFFE2A34F),
    val hairline: Color = Hairline,
)

val LocalReadingColors = staticCompositionLocalOf { ReadingColors() }

/** The current book's colour, shared so the deck glow, progress and active icons agree. */
val LocalAccent = compositionLocalOf { Leather }

/**
 * Lightness 60–72% and saturation at most 75%, so any cover colour reads on [Ground] and dark text
 * reads on it.
 */
fun tunedAccent(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(0.75f)
    hsl[2] = hsl[2].coerceIn(0.60f, 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun BookTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RectoColors,
        typography = RectoTypography,
        shapes = RectoShapes,
        content = content,
    )
}

val DeckFront = Color(0xFF202020)
val StackSheetNear = Color(0xFF1C1C1C)
val StackSheetFar = Color(0xFF191919)

/** A blurred rounded rectangle behind the node; hardware shadow layers need API 28, the minSdk. */
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
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
        }
    }
}

/** Extruded from the ground: a dark shadow below right, a faint highlight above left. */
fun Modifier.raised(corner: Dp, fill: Color = Raised, depth: Dp = 6.dp): Modifier = this
    .softShadow(Color(0x99000000), depth * 2.2f, fill, depth, depth, corner)
    .softShadow(Color(0x0BFFFFFF), depth * 1.6f, fill, -depth * 0.6f, -depth * 0.6f, corner)

/** Pressed into the ground: the shadow falls inside the top left edge, the light inside the bottom right. */
fun Modifier.pressedWell(corner: Dp, fill: Color = Well): Modifier = drawWithCache {
    val radius = corner.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
    val shift = 3.dp.toPx()
    val bounds = RectF(0f, 0f, size.width, size.height)
    fun edge(color: Color) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6.dp.toPx()
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val shadow = edge(Color(0xB3000000))
    val light = edge(Color(0x0DFFFFFF))
    onDrawBehind {
        drawPath(outline, fill)
        clipPath(outline) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(shift, shift)
                native.drawRoundRect(bounds, radius, radius, shadow)
                native.restore()
                native.save()
                native.translate(-shift, -shift)
                native.drawRoundRect(bounds, radius, radius, light)
                native.restore()
            }
        }
    }
}

/** A soft circle of light, like a lamp behind the node. It spills past the node's bounds. */
fun Modifier.ambientGlow(color: Color, strength: Float = 0.45f, reach: Float = 0.75f): Modifier = drawBehind {
    val radius = size.maxDimension * reach
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Opaque layered card for the deck, with a hairline of light along its top edge. */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    color: Color = DeckFront,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .softShadow(Color(0xA6000000), 22.dp, color, 10.dp, 12.dp)
            .softShadow(Color(0x0AFFFFFF), 12.dp, color, (-5).dp, (-5).dp)
            .clip(shape)
            .background(color)
            .drawBehind { drawLine(Hairline, Offset(size.width * 0.06f, 0.5f), Offset(size.width * 0.94f, 0.5f), 1.dp.toPx()) },
    ) {
        content()
    }
}

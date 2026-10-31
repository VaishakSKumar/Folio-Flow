package com.booktracker.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.random.Random

/**
 * The covers used when a book has no image. Each has its own [glow], which plays the part Palette
 * plays for an uploaded cover. All are dark enough for the foil text drawn over them.
 */
enum class CoverTexture(val leather: Color, val glow: Color) {
    Saddle(Color(0xFF5A3D22), Color(0xFFE0A45A)),
    Oxblood(Color(0xFF5C1F1E), Color(0xFFD9695E)),
    Forest(Color(0xFF24402F), Color(0xFF5FBF8F)),
    Midnight(Color(0xFF1F3147), Color(0xFF6FA8DC));

    companion object {
        /** Chosen from the title so that a book keeps its leather, even before it is saved. */
        fun forTitle(title: String): CoverTexture = entries[title.hashCode().mod(entries.size)]
    }
}

val CoverFoil = Color(0xFFF3E3BE)

/** Leather-bound boards: pebbled grain, a darker spine and a pressed double border. */
@Composable
fun LeatherCover(texture: CoverTexture, modifier: Modifier = Modifier) {
    val grain = remember(texture) {
        val random = Random(texture.ordinal + 1)
        List(900) { Offset(random.nextFloat(), random.nextFloat()) }
    }
    Canvas(modifier) {
        drawRect(texture.leather)
        grain.forEachIndexed { index, speck ->
            drawCircle(
                color = if (index % 2 == 0) Color.Black else Color.White,
                radius = size.minDimension * 0.008f,
                center = Offset(speck.x * size.width, speck.y * size.height),
                alpha = if (index % 2 == 0) 0.16f else 0.05f,
            )
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                center = center,
                radius = size.maxDimension * 0.7f,
            ),
        )

        val spine = size.width * 0.09f
        drawRect(Color.Black.copy(alpha = 0.3f), size = Size(spine, size.height))
        drawRect(
            Color.White.copy(alpha = 0.08f),
            topLeft = Offset(spine, 0f),
            size = Size(size.width * 0.012f, size.height),
        )

        val inset = size.width * 0.07f
        val line = size.width * 0.012f
        val frameLeft = spine + inset
        val frameSize = Size(size.width - frameLeft - inset, size.height - inset * 2)
        val corner = CornerRadius(size.width * 0.02f)
        drawRoundRect(
            Color.White.copy(alpha = 0.1f),
            topLeft = Offset(frameLeft + line, inset + line),
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(line),
        )
        drawRoundRect(
            Color.Black.copy(alpha = 0.4f),
            topLeft = Offset(frameLeft, inset),
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(line),
        )
    }
}

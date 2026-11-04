package com.booktracker.ui.library

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.booktracker.ui.theme.Leather
import com.booktracker.ui.theme.tunedAccent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The one way covers are drawn, so Read and Archive always match. Pass this as the `cover` slot of
 * ActiveReadHubScreen and StatsHistoryScreen. Books without an image get a leather [CoverTexture].
 */
@Composable
fun BookCover(coverPath: String?, title: String, author: String, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (coverPath != null) {
            AsyncImage(
                model = coverPath,
                contentDescription = "Cover of $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LeatherCover(CoverTexture.forTitle(title), Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = CoverFoil,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    author,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoverFoil,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The colour a book lights the room with: its cover's Palette colour, its leather's colour when it
 * has no image, or leather brown when the image had no usable colour. Always tuned for the dark.
 */
fun coverAccent(coverPath: String?, title: String, glowColor: Int?): Color = when {
    coverPath == null -> tunedAccent(CoverTexture.forTitle(title).glow)
    glowColor != null -> tunedAccent(Color(glowColor))
    else -> Leather
}

/** Photo picker access can expire, so saved covers are copied into the app's own storage. */
suspend fun saveCoverImage(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
    val folder = File(context.filesDir, "covers").apply { mkdirs() }
    val target = File(folder, "${UUID.randomUUID()}.jpg")
    val input = checkNotNull(context.contentResolver.openInputStream(source)) { "Cover unavailable" }
    input.use { from -> target.outputStream().use { to -> from.copyTo(to) } }
    target.absolutePath
}

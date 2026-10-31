package com.booktracker.ui.library

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_COVER_BYTES = 5L * 1024 * 1024
private val SUPPORTED_COVER_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif",
)
private val COVER_EXTENSIONS = listOf(".JPG", ".PNG", ".WEBP", ".HEIC")

/** Null means the image can be used. The picker can return any image, so this runs on every pick. */
suspend fun coverProblem(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    if (resolver.getType(uri) !in SUPPORTED_COVER_TYPES) {
        return@withContext "That file type isn't supported. Choose a JPG, PNG, WEBP or HEIC image."
    }
    val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
    if (size != null && size > MAX_COVER_BYTES) {
        return@withContext "That image is over 5MB. Choose a smaller one."
    }
    null
}

/**
 * The cover's most vibrant colour, tuned to read as light on a dark surface. Grey covers stay grey
 * rather than gaining an invented hue. Returns null if the image can't be decoded.
 */
suspend fun extractGlowColor(path: String): Int? = withContext(Dispatchers.Default) {
    val options = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
    val palette = Palette.from(bitmap).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch
        ?: return@withContext null
    val hsl = swatch.hsl.copyOf()
    if (hsl[1] >= 0.12f) hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.45f, 0.65f)
    ColorUtils.HSLToColor(hsl)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverUploadSheet(
    hasCover: Boolean,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                problem = coverProblem(context, uri)
                if (problem == null) onPicked(uri)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Book cover", style = MaterialTheme.typography.headlineSmall)
            CoverDropArea(
                label = if (hasCover) "Change cover image" else "Add cover image",
                onClick = {
                    problem = null
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Text(
                "Supported formats: JPG, PNG, WEBP, HEIC (Max 5MB)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            problem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (hasCover) {
                TextButton(onClick = onRemove) { Text("Remove cover") }
            }
        }
    }
}

@Composable
private fun CoverDropArea(label: String, onClick: () -> Unit) {
    val dash = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COVER_EXTENSIONS.forEach { extension ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        extension,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

package com.booktracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons on a 24 grid with a 1.75 stroke and round ends. Icon() tints them. */
object RectoIcons {
    val Hub by lazy { line("Hub", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Vault by lazy {
        line("Vault", "M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    }
    val Stats by lazy { line("Stats", "M5 20v-8M11 20V5M17 20v-5M3 20h18") }
    val Challenge by lazy { line("Challenge", "M5 21V4h11l-2 4 2 4H5") }
    val Add by lazy { line("Add", "M12 5v14M5 12h14", width = 2f) }
    val Remove by lazy { line("Remove", "M5 12h14", width = 2f) }
    val Done by lazy { line("Done", "M5 12.5l4.5 4.5L19 7", width = 2f) }
    val Notes by lazy { line("Notes", "M5 4h10l4 4v12H5zM15 4v4h4M8 12h8M8 16h5") }
    val Delete by lazy { line("Delete", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }
    val Favourite by lazy { line("Favourite", Heart) }
    val FavouriteFilled by lazy { line("FavouriteFilled", Heart, filled = true) }
    val Upload by lazy { line("Upload", "M12 16V4M7 9l5-5 5 5M5 20h14") }
    val Close by lazy { line("Close", "M6 6l12 12M18 6L6 18", width = 2f) }
    val Info by lazy { line("Info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5M12 8h.01") }
    val Streak by lazy { line("Streak", "M12 3c1 3 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-6 1-9z") }
    val Book by lazy { line("Book", "M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5zM5 19.5A1.5 1.5 0 0 0 6.5 21H19") }
    val Deck by lazy { line("Deck", "M8 3h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM3 8v11a2 2 0 0 0 2 2h10") }
    val Grid by lazy { line("Grid", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z") }
    val Timeline by lazy { line("Timeline", "M4 6h16M4 12h16M4 18h10") }
    val Back by lazy { line("Back", "M15 5l-7 7 7 7", width = 2f) }

    private const val Heart = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"

    private fun line(name: String, path: String, width: Float = 1.75f, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}

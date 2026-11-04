package com.booktracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.booktracker.ui.challenges.ChallengesScreen
import com.booktracker.ui.challenges.HabitPlanScreen
import com.booktracker.ui.hub.ActiveBooksViewModel
import com.booktracker.ui.hub.ActiveReadHubScreen
import com.booktracker.ui.hub.HubActions
import com.booktracker.ui.hub.HubUiState
import com.booktracker.ui.hub.accent
import com.booktracker.ui.library.AddBookScreen
import com.booktracker.ui.library.BookCover
import com.booktracker.ui.library.BookDetailActions
import com.booktracker.ui.library.BookDetailScreen
import com.booktracker.ui.library.BookDetailTab
import com.booktracker.ui.stats.ArchiveActions
import com.booktracker.ui.stats.BackupExportedScreen
import com.booktracker.ui.stats.BackupHost
import com.booktracker.ui.stats.BackupViewModel
import com.booktracker.ui.stats.ReadingInsightsViewModel
import com.booktracker.ui.stats.StatsHistoryScreen
import com.booktracker.ui.stats.StatsTab
import com.booktracker.ui.stats.StatsUiState
import com.booktracker.ui.theme.InkFaint
import com.booktracker.ui.theme.Leather
import com.booktracker.ui.theme.LocalAccent
import com.booktracker.ui.theme.RectoIcons
import com.booktracker.ui.theme.SurfaceTone
import com.booktracker.ui.theme.pressedWell
import com.booktracker.ui.theme.raised

private object Routes {
    const val READ = "read"
    const val ARCHIVE = "archive"
    const val CHALLENGES = "challenges"
    const val STATS = "stats"
    const val ADD_BOOK = "add-book"
    const val HABIT_PLAN = "habit-plan"
    const val BACKUP_EXPORTED = "backup-exported"
    const val BOOK = "book/{id}/{tab}"
    fun book(id: Long, tab: BookDetailTab) = "book/$id/${tab.name}"
}

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Read(Routes.READ, "Hub", RectoIcons.Hub),
    Archive(Routes.ARCHIVE, "Vault", RectoIcons.Vault),
    Challenges(Routes.CHALLENGES, "Challenges", RectoIcons.Challenge),
    Stats(Routes.STATS, "Stats", RectoIcons.Stats),
}

private const val FADE_MILLIS = 220

@Composable
fun FolioFlowNavigation(
    books: ActiveBooksViewModel,
    insightsModel: ReadingInsightsViewModel,
    backup: BackupViewModel,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The keyboard belongs to the screen whose field was tapped; it never follows to another one.
    LaunchedEffect(route) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val activeBooks by books.books.collectAsStateWithLifecycle()
    val lastBookId by books.lastBookId.collectAsStateWithLifecycle()
    val frontBook = activeBooks.firstOrNull { it.id == lastBookId } ?: activeBooks.firstOrNull()
    val accent by animateColorAsState(frontBook?.accent() ?: Leather, tween(400), label = "accent")
    CompositionLocalProvider(LocalAccent provides accent) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (Destination.entries.any { it.route == route }) {
                    BottomBar(route, navController)
                }
            },
        ) { padding ->
            Routes(navController, books, insightsModel, backup, padding)
        }
    }
}

@Composable
private fun Routes(
    navController: NavHostController,
    books: ActiveBooksViewModel,
    insightsModel: ReadingInsightsViewModel,
    backup: BackupViewModel,
    padding: PaddingValues,
) {
        NavHost(
            navController = navController,
            startDestination = Routes.READ,
            enterTransition = { fadeIn(tween(FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(FADE_MILLIS)) },
            popEnterTransition = { fadeIn(tween(FADE_MILLIS)) },
            popExitTransition = { fadeOut(tween(FADE_MILLIS)) },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.READ) { ReadRoute(books, insightsModel, navController) }
            composable(Routes.ARCHIVE) { InsightsRoute(StatsTab.Archive, books, insightsModel, backup, navController) }
            composable(Routes.STATS) { InsightsRoute(StatsTab.Stats, books, insightsModel, backup, navController) }
            composable(Routes.BACKUP_EXPORTED) {
                val state by backup.state.collectAsStateWithLifecycle()
                state.exported?.let { exported ->
                    BackupExportedScreen(exported, onDone = { navController.popBackStack() })
                }
            }
            composable(Routes.CHALLENGES) {
                val insights by insightsModel.insights.collectAsStateWithLifecycle()
                ChallengesScreen(
                    state = insights.challenges,
                    onOpenHabitPlan = { navController.navigate(Routes.HABIT_PLAN) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable(Routes.HABIT_PLAN) {
                val goals by insightsModel.goals.collectAsStateWithLifecycle()
                val today by insightsModel.today.collectAsStateWithLifecycle()
                HabitPlanScreen(
                    goals = goals,
                    today = today,
                    onTargetChange = insightsModel::setTarget,
                    onToggleRestDay = insightsModel::toggleRestDay,
                    onBusyChange = insightsModel::setBusy,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ADD_BOOK) {
                AddBookScreen(
                    onSave = books::addBook,
                    onAdded = { navController.popBackStack(Routes.READ, inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.BOOK,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("tab") { type = NavType.StringType },
                ),
            ) { entry ->
                val arguments = checkNotNull(entry.arguments)
                BookRoute(
                    bookId = arguments.getLong("id"),
                    tab = BookDetailTab.valueOf(checkNotNull(arguments.getString("tab"))),
                    books = books,
                    onBack = { navController.popBackStack() },
                )
            }
        }
}

/** A raised bar floating over the ground; the current screen's key is pressed in and lit. */
@Composable
private fun BottomBar(route: String?, navController: NavHostController) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(72.dp)
            .raised(24.dp, SurfaceTone, depth = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            val selected = route == destination.route
            val tint = if (selected) accent else InkFaint
            Column(
                Modifier
                    .size(width = 76.dp, height = 56.dp)
                    .then(if (selected) Modifier.pressedWell(16.dp) else Modifier)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(destination.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

@Composable
private fun ReadRoute(
    books: ActiveBooksViewModel,
    insightsModel: ReadingInsightsViewModel,
    navController: NavHostController,
) {
    val activeBooks by books.books.collectAsStateWithLifecycle()
    val lastBookId by books.lastBookId.collectAsStateWithLifecycle()
    val insights by insightsModel.insights.collectAsStateWithLifecycle()
    val finished by books.finishedBooks.collectAsStateWithLifecycle()
    ActiveReadHubScreen(
        state = HubUiState(
            books = activeBooks,
            lastBookId = lastBookId,
            pacing = insights.pacing,
            challengeLine = insights.challengeLine,
            streak = insights.challenges.streak,
            booksRead = finished.size,
        ),
        actions = HubActions(
            onOpenChallenges = { navController.navigate(Routes.CHALLENGES) },
            onSetPage = books::setPage,
            onActiveBookChange = books::showBook,
            onAddBook = { navController.navigate(Routes.ADD_BOOK) },
            onCompleteBook = books::completeBook,
            onRestoreBook = books::restoreBook,
            onDeleteBook = books::deleteBook,
            onOpenNotes = { navController.navigate(Routes.book(it, BookDetailTab.Notes)) },
        ),
        cover = { book, modifier -> BookCover(book.coverPath, book.title, book.author, modifier) },
    )
}

@Composable
private fun InsightsRoute(
    tab: StatsTab,
    books: ActiveBooksViewModel,
    insightsModel: ReadingInsightsViewModel,
    backup: BackupViewModel,
    navController: NavHostController,
) {
    val insights by insightsModel.insights.collectAsStateWithLifecycle()
    val finished by books.finishedBooks.collectAsStateWithLifecycle()
    BackupHost(
        model = backup,
        onExported = { navController.navigate(Routes.BACKUP_EXPORTED) },
        onDataReplaced = books::reloadLastBook,
    ) { backupCard ->
        StatsHistoryScreen(
            state = StatsUiState(
                averages = insights.averages,
                days = insights.days,
                targetPerDay = insights.goals.targetPerDay,
                currentStreak = insights.challenges.streak,
                bestStreak = insights.bestStreak,
                finished = finished,
            ),
            tab = tab,
            cover = { book, modifier -> BookCover(book.coverPath, book.title, book.author, modifier) },
            archiveActions = ArchiveActions(
                onOpenBook = { navController.navigate(Routes.book(it, BookDetailTab.Details)) },
                onToggleFavorite = books::toggleFavorite,
                onDeleteBook = books::deleteBook,
            ),
            backupCard = backupCard,
            modifier = Modifier.statusBarsPadding(),
        )
    }
}

@Composable
private fun BookRoute(
    bookId: Long,
    tab: BookDetailTab,
    books: ActiveBooksViewModel,
    onBack: () -> Unit,
) {
    val detailFlow = remember(bookId) { books.bookDetail(bookId) }
    val notesFlow = remember(bookId) { books.notes(bookId) }
    val detail by detailFlow.collectAsStateWithLifecycle(null)
    val notes by notesFlow.collectAsStateWithLifecycle(emptyList())
    val book = detail ?: return Box(Modifier.fillMaxSize())
    BookDetailScreen(
        book = book,
        notes = notes,
        initialTab = tab,
        actions = BookDetailActions(
            onBack = onBack,
            onRestore = { books.restoreBook(bookId) },
            onSaveNote = { noteId, kind, text -> books.saveNote(noteId, bookId, kind, text) },
            onDeleteNote = books::deleteNote,
        ),
        cover = { modifier -> BookCover(book.coverPath, book.title, book.author, modifier) },
    )
}

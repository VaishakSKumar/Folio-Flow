package com.booktracker.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.booktracker.ui.library.AddBookScreen
import com.booktracker.ui.library.BookCover
import com.booktracker.ui.library.BookDetailActions
import com.booktracker.ui.library.BookDetailScreen
import com.booktracker.ui.library.BookDetailTab
import com.booktracker.ui.stats.ArchiveActions
import com.booktracker.ui.stats.ReadingInsightsViewModel
import com.booktracker.ui.stats.StatsHistoryScreen
import com.booktracker.ui.stats.StatsTab
import com.booktracker.ui.stats.StatsUiState

private object Routes {
    const val READ = "read"
    const val ARCHIVE = "archive"
    const val CHALLENGES = "challenges"
    const val STATS = "stats"
    const val ADD_BOOK = "add-book"
    const val HABIT_PLAN = "habit-plan"
    const val BOOK = "book/{id}/{tab}"
    fun book(id: Long, tab: BookDetailTab) = "book/$id/${tab.name}"
}

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Read(Routes.READ, "Read", Icons.Filled.Home),
    Archive(Routes.ARCHIVE, "Archive", Icons.Filled.List),
    Challenges(Routes.CHALLENGES, "Challenges", Icons.Filled.Star),
    Stats(Routes.STATS, "Stats", Icons.Filled.DateRange),
}

private const val FADE_MILLIS = 220

@Composable
fun FolioFlowNavigation(books: ActiveBooksViewModel, insightsModel: ReadingInsightsViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (Destination.entries.any { it.route == route }) {
                BottomBar(route, navController)
            }
        },
    ) { padding ->
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
            composable(Routes.ARCHIVE) { InsightsRoute(StatsTab.Archive, books, insightsModel, navController) }
            composable(Routes.STATS) { InsightsRoute(StatsTab.Stats, books, insightsModel, navController) }
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
}

@Composable
private fun BottomBar(route: String?, navController: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                selected = route == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
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
    ActiveReadHubScreen(
        state = HubUiState(
            books = activeBooks,
            lastBookId = lastBookId,
            pacing = insights.pacing,
            challengeLine = insights.challengeLine,
            streak = insights.challenges.streak,
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
    navController: NavHostController,
) {
    val insights by insightsModel.insights.collectAsStateWithLifecycle()
    val finished by books.finishedBooks.collectAsStateWithLifecycle()
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
        modifier = Modifier.statusBarsPadding(),
    )
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

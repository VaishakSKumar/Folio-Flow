package com.booktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.booktracker.ui.FolioFlowNavigation
import com.booktracker.ui.hub.ActiveBooksViewModel
import com.booktracker.ui.stats.ReadingInsightsViewModel
import com.booktracker.ui.theme.BookTrackerTheme

class MainActivity : ComponentActivity() {
    private val factory = viewModelFactory {
        initializer {
            val app = application as FolioFlowApp
            ActiveBooksViewModel(app.repository, app.settings)
        }
        initializer {
            val app = application as FolioFlowApp
            ReadingInsightsViewModel(app.repository, app.settings)
        }
    }
    private val books by viewModels<ActiveBooksViewModel> { factory }
    private val insights by viewModels<ReadingInsightsViewModel> { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LifecycleResumeEffect(Unit) {
                insights.refreshDay()
                onPauseOrDispose { }
            }
            BookTrackerTheme {
                FolioFlowNavigation(books, insights)
            }
        }
    }
}

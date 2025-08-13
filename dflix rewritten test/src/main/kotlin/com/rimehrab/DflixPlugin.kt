package com.rimehrab

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DflixPlugin : Plugin() {
    override fun load(context: Context) {
        // Register all main providers here for better maintainability.
        registerMainAPI(DflixMoviesProvider())
    }
}
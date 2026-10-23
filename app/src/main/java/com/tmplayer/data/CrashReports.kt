package com.tmplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Crash reporting is not part of this build: the Sentry dependency is gone, so every entry point
 * is an inert stand-in that keeps the call sites compiling until they are removed with the rest
 * of the settings screens.
 */
internal object CrashReports {
    val available: Boolean = false

    private val _pending = MutableStateFlow<Throwable?>(null)
    val pending: StateFlow<Throwable?> = _pending.asStateFlow()

    fun watch(context: Context) = Unit
    fun start(context: Context, enabled: Boolean) = Unit
    fun stop() = Unit
    fun review(context: Context, enabled: Boolean) = Unit
    fun sendPending(context: Context) = Unit
    fun discardPending() = Unit
}

package com.naveenapps.expensemanager.core.repository

import kotlinx.coroutines.flow.Flow

interface FeedbackRepository {

    suspend fun setTransactionCreated(created: Boolean)

    suspend fun setFeedbackDialogShown(shown: Boolean)

    fun shouldShowFeedbackDialog(): Flow<Boolean>

    /**
     * True if the app crashed the last time it ran. Backed by Crashlytics' own per-launch flag,
     * so it resets itself naturally on the next clean run — no extra persisted state needed.
     * Used to suppress the review prompt right after a crash, since asking someone who just hit
     * a fatal error to leave a good review is bad timing.
     */
    fun didCrashOnPreviousExecution(): Boolean
}
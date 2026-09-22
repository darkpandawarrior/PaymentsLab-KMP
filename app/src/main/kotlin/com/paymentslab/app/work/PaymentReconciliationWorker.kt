package com.paymentslab.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paymentslab.core.orchestration.PaymentOrchestrator
import com.siddharth.kmp.common.AppLog
import java.util.concurrent.TimeUnit

/**
 * Completes the process-death recovery story. The orchestrator writes a pending-payment row to Room
 * *before* the SDK launches; if the app is killed mid-payment (OEM battery kill, user swipe, low
 * memory during a bank's 3DS WebView), nothing in the UI is guaranteed to run again. This worker is
 * the safety net: it asks the server for the true state of every unresolved payment and settles the
 * journal — whether or not the user ever reopens the relevant screen.
 *
 * Scheduled two ways (see [enqueue]): a one-time run on app start with an exponential backoff, and a
 * periodic sweep — both gated on network connectivity, since reconciliation needs the backend.
 */
class PaymentReconciliationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val orchestrator: PaymentOrchestrator,
) : CoroutineWorker(appContext, params) {
    // WorkManager's boundary: anything escaping doWork() kills the worker process instead of
    // scheduling a retry, so this catch has to be total. recoverPending() fans out over every
    // unresolved order and can surface any backend or storage failure; all of them mean the same
    // thing here -- try again with backoff.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result =
        try {
            val recovered = orchestrator.recoverPending()
            AppLog.i("reconciled ${recovered.size} pending payment(s)", tag = TAG)
            Result.success()
        } catch (t: Throwable) {
            AppLog.w("reconciliation failed; WorkManager will retry with backoff", t, tag = TAG)
            Result.retry()
        }

    companion object {
        private const val TAG = "PaymentReconciliationWorker"
        private const val ONE_TIME_NAME = "payment_reconciliation_once"
        private const val PERIODIC_NAME = "payment_reconciliation_periodic"

        private val networkConstraint =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        /**
         * Enqueue both a prompt one-time reconciliation (catches the just-crashed case on next
         * launch) and a periodic sweep (catches payments left pending across sessions).
         */
        fun enqueue(context: Context) {
            val wm = WorkManager.getInstance(context)

            val oneTime =
                OneTimeWorkRequestBuilder<PaymentReconciliationWorker>()
                    .setConstraints(networkConstraint)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            wm.enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, oneTime)

            val periodic =
                PeriodicWorkRequestBuilder<PaymentReconciliationWorker>(6, TimeUnit.HOURS)
                    .setConstraints(networkConstraint)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic)
        }
    }
}

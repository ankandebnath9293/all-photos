package com.ankandebnath.allphotos.work

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ankandebnath.allphotos.capture.ShotTracker
import com.ankandebnath.allphotos.data.Prefs
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Runs the automatic clean-up rules. */
class CleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!Prefs.get(applicationContext).now.onboarded) return Result.success()
        runCatching { Cleaner.run(applicationContext) }
        return Result.success()
    }
}

/** Builds the search and similar-photos index. */
class IndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!Prefs.get(applicationContext).now.onboarded) return Result.success()
        val heavy = inputData.getBoolean(KEY_HEAVY, false)
        runCatching { Indexer.run(applicationContext, heavy) { isStopped } }
        return Result.success()
    }

    companion object {
        const val KEY_HEAVY = "heavy"
    }
}

/** Wakes up when a new photo is saved, so screenshots get their notification even if the app is closed. */
class ShotWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { ShotTracker.process(applicationContext) }
        // Re-arm after this run finishes; the trigger only fires once per request.
        Work.watchShots(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

object Work {
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        scheduleHeavy(ctx)
        watchShots(ctx)
    }

    fun scheduleHeavy(ctx: Context, onlyCharging: Boolean = Prefs.get(ctx).now.indexOnlyCharging) {
        val c = Constraints.Builder().setRequiresBatteryNotLow(true).apply {
            if (onlyCharging) setRequiresCharging(true)
        }.build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "index-heavy",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                .setConstraints(c)
                .setInputData(workDataOf(IndexWorker.KEY_HEAVY to true))
                .build(),
        )
    }

    /** Screenshots and fingerprints, soon, without waiting for the charger. */
    fun indexSoon(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "index-light",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>()
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Reads text and labels for every photo now, plugged in or not. */
    fun indexAllNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "index-now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf(IndexWorker.KEY_HEAVY to true))
                .build(),
        )
    }

    fun cleanupNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "cleanup-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CleanupWorker>().build(),
        )
    }

    fun watchShots(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val c = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofMillis(500))
            .setTriggerContentMaxDelay(Duration.ofSeconds(3))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "shot-watch",
            policy,
            OneTimeWorkRequestBuilder<ShotWorker>().setConstraints(c).build(),
        )
    }
}

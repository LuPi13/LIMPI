package com.github.lupi13.limpi.platform

import com.github.lupi13.limpi.ops.AutomaticBackups
import org.bukkit.plugin.Plugin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/** Owns a separate read-only SQLite connection for each backup; no Bukkit calls on the worker. */
class BackupScheduler(plugin: Plugin) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "LIMPI-backup").apply { isDaemon = true }
    }

    init {
        val logger = plugin.logger
        try {
            val interval = plugin.config.get("BackupIntervalMinutes") ?: 60
            val keep = plugin.config.get("BackupKeep") ?: 24
            require(interval is Int && interval >= 0) { "BackupIntervalMinutes must be a nonnegative integer" }
            require(keep is Int && keep > 0) { "BackupKeep must be a positive integer" }
            if (interval > 0) {
                val folder = plugin.dataFolder.toPath()
                val backups = AutomaticBackups(folder.resolve("limpi.sqlite3"), folder.resolve("backups"), keep)
                worker.scheduleWithFixedDelay({
                    try { logger.info("Database backup: ${backups.run().fileName}") }
                    catch (failure: Exception) { logger.log(Level.WARNING, "Automatic database backup failed", failure) }
                }, 0, interval.toLong(), TimeUnit.MINUTES)
            } else worker.shutdown()
        } catch (failure: Exception) {
            worker.shutdown()
            logger.log(Level.WARNING, "Automatic database backups disabled: invalid configuration", failure)
        }
    }

    override fun close() {
        // An in-flight backup can finish on its own read-only connection after the live store closes.
        worker.shutdown()
    }
}

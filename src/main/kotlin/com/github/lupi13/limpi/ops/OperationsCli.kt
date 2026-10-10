package com.github.lupi13.limpi.ops

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

object OperationsCli {
    @JvmStatic fun main(args: Array<String>): Unit { exitProcess(run(args, ::println, System.err::println)) }
    fun run(args: Array<String>, output: (String) -> Unit, error: (String) -> Unit): Int {
        try {
            val command = args.firstOrNull() ?: "help"
            if (command == "help") {
                require(args.size <= 1)
                output("LIMPI operations (Java 25, no Paper server required)")
                output("check DB | backup DB NEW_DB | export DB NEW_YAML | export-check YAML | restore-dry-run YAML | restore-copy YAML NEW_DB")
                output("Outputs must be new files in an existing directory. Restore never replaces a server database.")
                return 0
            }
            require(args.size == if (command in setOf("backup", "export", "restore-copy")) 3 else 2) { "Invalid arguments; use help" }
            val source = Path.of(args[1])
            val snapshot = when (command) {
                "check", "backup", "export" -> ReadOnlyDatabase(source).use { it.snapshot() }
                "export-check", "restore-dry-run", "restore-copy" -> DatabaseSnapshot.import(Files.readString(source))
                else -> throw IllegalArgumentException("Unknown command; use help")
            }
            when (command) {
                "backup" -> OperationsFiles.backup(source, Path.of(args[2]))
                "export" -> OperationsFiles.export(snapshot, Path.of(args[2]))
                "restore-copy" -> OperationsFiles.restore(snapshot, Path.of(args[2]))
            }
            output("documents=${snapshot.documents.size} transactions=${snapshot.transactions.size} sha256=${snapshot.fingerprint()}")
            val report = SnapshotCheck.inspect(snapshot)
            output("players=${report.players} balanceSum=${report.balanceSum} pending=${report.pending} errors=${report.errors.size} warnings=${report.warnings.size}")
            report.warnings.forEach { output("WARN $it") }; report.errors.forEach { error("ERROR $it") }
            // A forensic backup/export is useful even when logical documents are invalid.
            return if (report.errors.isEmpty()) 0 else 2
        } catch (failure: Exception) {
            error("Operation failed: ${failure.javaClass.simpleName}; source/output unchanged or new output unpublished. Use help and verify paths, schema and values.")
            return 2
        }
    }
}

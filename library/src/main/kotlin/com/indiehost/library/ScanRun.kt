package com.indiehost.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One completed or failed SAF scan, kept for diagnostics and staleness checks. */
data class ScanRun(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    val imported: Int,
    val skipped: Int,
    val error: String? = null,
)

@Entity(tableName = "scan_runs")
data class ScanRunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    val imported: Int,
    val skipped: Int,
    val error: String?,
)

fun ScanRun.toEntity(): ScanRunEntity = ScanRunEntity(id, startedAt, finishedAt, imported, skipped, error)
fun ScanRunEntity.toScanRun(): ScanRun = ScanRun(id, startedAt, finishedAt, imported, skipped, error)

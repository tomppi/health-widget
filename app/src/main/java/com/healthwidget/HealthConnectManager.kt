package com.healthwidget

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

data class SleepStageData(
    val startMs: Long,
    val endMs: Long,
    val type: Int
)

data class HealthData(
    val heartRates: List<Int> = emptyList(),
    val sleepStages: List<SleepStageData> = emptyList(),
    val sleepDurationMinutes: Long? = null,
    val timestamp: Instant = Instant.now()
)

class HealthConnectManager(private val context: Context) {

    val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun getLatestHealthData(): HealthData {
        val now = Instant.now()
        val since = now.minus(24, ChronoUnit.HOURS)
        val timeRange = TimeRangeFilter.between(since, now)

        val heartRates: List<Int> = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 20
                )
            ).records
                .flatMap { it.samples }
                .distinctBy { it.time }
                .sortedByDescending { it.time }
                .take(5)
                .map { it.beatsPerMinute.toInt() }
                .reversed()
        }.getOrElse { emptyList() }

        val session = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 10
                )
            ).records
                .maxByOrNull { it.endTime }
        }.getOrNull()

        val rawStages: List<SleepStageData> = session?.stages?.map { stage ->
            SleepStageData(
                startMs = ChronoUnit.MILLIS.between(session.startTime, stage.startTime),
                endMs   = ChronoUnit.MILLIS.between(session.startTime, stage.endTime),
                type    = stage.stage
            )
        } ?: emptyList()

        // Fill gaps between recorded stages with estimated REM sleep.
        // The KSIX Ring does not report REM data — gaps in the recording
        // typically correspond to transitions where REM is most likely.
        val sleepStages = fillGapsWithRem(rawStages)

        val sleepDurationMinutes: Long? = session?.let {
            ChronoUnit.MINUTES.between(it.startTime, it.endTime)
        }

        return HealthData(
            heartRates           = heartRates,
            sleepStages          = sleepStages,
            sleepDurationMinutes = sleepDurationMinutes
        )
    }

    /**
     * Finds gaps between sleep stages longer than 2 minutes and fills them
     * with [SleepSessionRecord.STAGE_TYPE_REM]. The KSIX Ring only records
     * light and deep sleep — REM is estimated from the gaps between them.
     */
    private fun fillGapsWithRem(stages: List<SleepStageData>): List<SleepStageData> {
        if (stages.size < 2) return stages

        val sorted = stages.sortedBy { it.startMs }
        val result = mutableListOf<SleepStageData>()

        sorted.forEachIndexed { i, stage ->
            if (i > 0) {
                val gapStart = sorted[i - 1].endMs
                val gapEnd   = stage.startMs
                val gapMs    = gapEnd - gapStart

                // Only fill gaps longer than 2 minutes to ignore tiny overlaps
                if (gapMs > 2 * 60 * 1000L) {
                    result.add(
                        SleepStageData(
                            startMs = gapStart,
                            endMs   = gapEnd,
                            type    = SleepSessionRecord.STAGE_TYPE_REM
                        )
                    )
                }
            }
            result.add(stage)
        }

        return result
    }
}

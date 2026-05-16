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

        // The KSIX Ring writes cumulative records that all start at midnight.
        // Each new record is a superset of the previous one, so flattening all
        // records gives duplicate samples. Fix: flatten → deduplicate by timestamp
        // → sort → take the 5 most recent unique readings.
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
                .distinctBy { it.time }           // remove duplicates across overlapping records
                .sortedByDescending { it.time }   // most recent first
                .take(5)
                .map { it.beatsPerMinute.toInt() }
                .reversed()                        // oldest → newest for left-to-right chart
        }.getOrElse { emptyList() }

        // Same pattern for sleep: pick the session with the latest end time
        // (the most complete record) and read its stages
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

        val sleepStages: List<SleepStageData> = session?.stages?.map { stage ->
            SleepStageData(
                startMs = ChronoUnit.MILLIS.between(session.startTime, stage.startTime),
                endMs   = ChronoUnit.MILLIS.between(session.startTime, stage.endTime),
                type    = stage.stage
            )
        } ?: emptyList()

        val sleepDurationMinutes: Long? = session?.let {
            ChronoUnit.MINUTES.between(it.startTime, it.endTime)
        }

        return HealthData(
            heartRates           = heartRates,
            sleepStages          = sleepStages,
            sleepDurationMinutes = sleepDurationMinutes
        )
    }
}

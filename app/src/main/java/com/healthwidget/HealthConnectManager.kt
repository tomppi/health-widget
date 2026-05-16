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

        // The KSIX Ring writes all records starting at midnight (same startTime),
        // so sorting by startTime descending doesn't find the newest one.
        // Instead: read several records and pick the one with the latest endTime.
        val heartRates: List<Int> = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 10
                )
            ).records
                .maxByOrNull { it.endTime }   // ← pick record with latest end time
                ?.samples
                ?.sortedByDescending { it.time }
                ?.take(5)
                ?.map { it.beatsPerMinute.toInt() }
                ?.reversed()
                ?: emptyList()
        }.getOrElse { emptyList() }

        // Same fix for sleep — pick session with latest endTime
        val session = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange,
                    ascendingOrder = false,
                    pageSize = 10
                )
            ).records
                .maxByOrNull { it.endTime }   // ← pick record with latest end time
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

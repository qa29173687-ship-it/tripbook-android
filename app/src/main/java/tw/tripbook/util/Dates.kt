package tw.tripbook.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Dates {
    private val zone = ZoneId.of("Asia/Taipei")
    private val storeFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val showFmt = DateTimeFormatter.ofPattern("yyyy/M/d")

    fun todayIso(): String = LocalDate.now(zone).format(storeFmt)

    fun toDisplay(iso: String): String {
        if (iso.isBlank()) return "請選擇日期"
        return runCatching { LocalDate.parse(iso, storeFmt).format(showFmt) }.getOrDefault(iso)
    }

    fun millisToIso(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(storeFmt)

    fun isoToMillisStartOfDay(iso: String): Long? =
        runCatching {
            LocalDate.parse(iso, storeFmt).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()

    fun parse(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso, storeFmt) }.getOrNull()

    fun toMillisNoon(iso: String): Long? =
        runCatching {
            LocalDate.parse(iso, storeFmt).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()

    /** Auto expense date inside trip range. */
    fun defaultExpenseIso(tripStart: String, tripEnd: String): String {
        val start = parse(tripStart) ?: return todayIso()
        val end = parse(tripEnd) ?: start
        val today = LocalDate.now(zone)
        val pick = when {
            !today.isBefore(start) && !today.isAfter(end) -> today
            today.isAfter(end) -> end
            else -> start
        }
        return pick.format(storeFmt)
    }

    fun clampIsoToRange(iso: String, tripStart: String, tripEnd: String): String {
        val d = parse(iso) ?: return defaultExpenseIso(tripStart, tripEnd)
        val start = parse(tripStart) ?: return iso
        val end = parse(tripEnd) ?: start
        return when {
            d.isBefore(start) -> start.format(storeFmt)
            d.isAfter(end) -> end.format(storeFmt)
            else -> d.format(storeFmt)
        }
    }
}

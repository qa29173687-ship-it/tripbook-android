package tw.tripbook.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Dates {
    private val zone = ZoneId.of("Asia/Taipei")
    private val storeFmt = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
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
}

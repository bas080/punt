package com.bas080.notificationreminders.utils

import android.content.Context
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SnoozeParser {

    fun canonicalizeSingleSnoozeChoice(raw: String): String? {
        if (raw.isEmpty()) return "1h"

        when {
            raw == "15m" || raw == "15 mins" || raw == "15 minutes" || raw == "15min" -> return "15m"
            raw == "1h" || raw == "1 hour" || raw == "1 hr" || raw == "1hour" -> return "1h"
            raw == "4h" || raw == "4 hours" || raw == "4 hrs" || raw == "4hour" -> return "4h"
            raw == "24h" || raw == "1 day" || raw == "24 hours" || raw == "24 hrs" || raw == "1day" -> return "24h"
            raw == "1w" || raw == "1 week" || raw == "1week" || raw == "w" -> return "1w"
        }

        val weekdayResult = parseWeekdaySnooze(raw, System.currentTimeMillis())
        if (weekdayResult != null) {
            return weekdayResult.second
        }

        val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(raw)
        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toInt()
            val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
            val amPm = amPmMatch.groupValues[3]
            if (hour in 1..12 && min in 0..59) {
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                return String.format(Locale.US, "%02d:%02d", hour, min)
            }
        }

        val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(raw)
        if (timeColonMatch != null) {
            val hour = timeColonMatch.groupValues[1].toInt()
            val min = timeColonMatch.groupValues[2].toInt()
            if (hour in 0..23 && min in 0..59) {
                return String.format(Locale.US, "%02d:%02d", hour, min)
            }
        }

        if (raw.length in 3..4 && raw.all { it.isDigit() }) {
            val hour = if (raw.length == 4) raw.substring(0, 2).toInt() else raw.substring(0, 1).toInt()
            val min = if (raw.length == 4) raw.substring(2, 4).toInt() else raw.substring(1, 3).toInt()
            if (hour in 0..23 && min in 0..59) {
                return String.format(Locale.US, "%02d:%02d", hour, min)
            }
        }

        val numberMatch = Regex("^(\\d+)\\s*([mhdw]?)$").find(raw)
        if (numberMatch != null) {
            val num = numberMatch.groupValues[1].toLongOrNull() ?: return null
            val unit = numberMatch.groupValues[2].ifEmpty { "h" }
            if (num <= 0) return null
            return "$num$unit"
        }

        return null
    }

    fun canonicalizeSnoozeChoice(input: String?): String? {
        val raw = input?.trim()?.lowercase() ?: ""
        if (raw.isEmpty()) return "1h"

        val single = canonicalizeSingleSnoozeChoice(raw)
        if (single != null) return single

        val rawTokens = raw.split("\\s+".toRegex())
        if (rawTokens.size >= 2) {
            val canonicalParts = mutableListOf<String>()
            var currentMillis = System.currentTimeMillis()
            var idx = 0
            while (idx < rawTokens.size) {
                var parsed = false
                if (idx + 1 < rawTokens.size) {
                    val combined = "${rawTokens[idx]} ${rawTokens[idx + 1]}"
                    val res = parseSingleSnoozeDuration(combined, currentMillis)
                    if (res != null) {
                        canonicalParts.add(canonicalizeSingleSnoozeChoice(combined) ?: combined)
                        currentMillis += res.first
                        idx += 2
                        parsed = true
                    }
                }
                if (!parsed) {
                    val token = rawTokens[idx]
                    val res = parseSingleSnoozeDuration(token, currentMillis)
                    if (res != null) {
                        canonicalParts.add(canonicalizeSingleSnoozeChoice(token) ?: token)
                        currentMillis += res.first
                        idx += 1
                    } else {
                        return null
                    }
                }
            }
            if (canonicalParts.isNotEmpty()) {
                return canonicalParts.joinToString(" ")
            }
        }

        return null
    }

    fun getSnoozeCustomHint(context: Context): String {
        val df = android.text.format.DateFormat.getDateFormat(context)
        val sampleCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 25)
        }
        val dateSample = try { df.format(sampleCal.time) } catch (_: Exception) { "10/25/2026" }
        return "e.g. 15m, 18:00, Mon, or $dateSample"
    }

    fun parseSingleSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String>? {
        val raw = input?.trim()?.lowercase() ?: ""
        if (raw.isEmpty()) return Pair(3600_000L, "1 hour")

        when {
            raw == "15m" || raw == "15 mins" || raw == "15 minutes" || raw == "15min" -> return Pair(15 * 60_000L, "15 minutes")
            raw == "1h" || raw == "1 hour" || raw == "1 hr" || raw == "1hour" -> return Pair(3600_000L, "1 hour")
            raw == "4h" || raw == "4 hours" || raw == "4 hrs" || raw == "4hour" -> return Pair(4 * 3600_000L, "4 hours")
            raw == "24h" || raw == "1 day" || raw == "24 hours" || raw == "24 hrs" || raw == "1day" -> return Pair(24 * 3600_000L, "24 hours")
            raw == "1w" || raw == "1 week" || raw == "1week" || raw == "w" -> return Pair(7 * 24 * 3600_000L, "1 week")
        }

        val weekdayResult = parseWeekdaySnooze(raw, nowMillis)
        if (weekdayResult != null) {
            return weekdayResult.first
        }

        val dateResult = parseDateSnooze(raw, nowMillis)
        if (dateResult != null) {
            return dateResult
        }

        val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(raw)
        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toInt()
            val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
            val amPm = amPmMatch.groupValues[3]
            if (hour in 1..12 && min in 0..59) {
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
            }
            return null
        }

        val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(raw)
        if (timeColonMatch != null) {
            val hour = timeColonMatch.groupValues[1].toInt()
            val min = timeColonMatch.groupValues[2].toInt()
            if (hour in 0..23 && min in 0..59) {
                return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
            }
            return null
        }

        if (raw.length in 3..4 && raw.all { it.isDigit() }) {
            val hour = if (raw.length == 4) raw.substring(0, 2).toInt() else raw.substring(0, 1).toInt()
            val min = if (raw.length == 4) raw.substring(2, 4).toInt() else raw.substring(1, 3).toInt()
            if (hour in 0..23 && min in 0..59) {
                return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
            }
            return null
        }

        val numberMatch = Regex("^(\\d+)\\s*([mhdw]?)$").find(raw)
        if (numberMatch != null) {
            val num = numberMatch.groupValues[1].toLongOrNull() ?: return null
            val unit = numberMatch.groupValues[2].ifEmpty { "h" }
            if (num <= 0) return null

            val multiplier = when (unit) {
                "m" -> 60_000L
                "h" -> 3600_000L
                "d" -> 24 * 3600_000L
                "w" -> 7 * 24 * 3600_000L
                else -> 3600_000L
            }
            val snoozeMs = num * multiplier
            val unitStr = when (unit) {
                "m" -> if (num == 1L) "minute" else "minutes"
                "h" -> if (num == 1L) "hour" else "hours"
                "d" -> if (num == 1L) "day" else "days"
                "w" -> if (num == 1L) "week" else "weeks"
                else -> "hours"
            }
            return Pair(snoozeMs, "$num $unitStr")
        }

        return null
    }

    fun parseSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String>? {
        val raw = input?.trim()?.lowercase() ?: ""
        if (raw.isEmpty()) {
            return Pair(60 * 60 * 1000L, "1 hour")
        }

        val single = parseSingleSnoozeDuration(raw, nowMillis)
        if (single != null) return single

        val rawTokens = raw.split("\\s+".toRegex())
        if (rawTokens.size >= 2) {
            var currentMillis = nowMillis
            var idx = 0
            while (idx < rawTokens.size) {
                var parsed = false
                if (idx + 1 < rawTokens.size) {
                    val combined = "${rawTokens[idx]} ${rawTokens[idx + 1]}"
                    val res = parseSingleSnoozeDuration(combined, currentMillis)
                    if (res != null) {
                        currentMillis += res.first
                        idx += 2
                        parsed = true
                    }
                }
                if (!parsed) {
                    val token = rawTokens[idx]
                    val res = parseSingleSnoozeDuration(token, currentMillis)
                    if (res != null) {
                        currentMillis += res.first
                        idx += 1
                    } else {
                        return null
                    }
                }
            }

            val totalSnoozeMs = currentMillis - nowMillis
            if (totalSnoozeMs <= 0) return null
            val durationLabel = com.bas080.notificationreminders.MainActivity.formatSnoozeUntil(currentMillis, nowMillis)
            return Pair(totalSnoozeMs, durationLabel)
        }

        return null
    }

    private fun parseWeekdaySnooze(raw: String, nowMillis: Long): Pair<Pair<Long, String>, String>? {
        val weekdayRegex = Regex("\\b(mon|monday|tue|tues|tuesday|wed|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday)\\b")
        val match = weekdayRegex.find(raw) ?: return null
        val weekdayStr = match.value

        val (dayOfWeek, fullDisplayName, shortAbbr) = when (weekdayStr) {
            "mon", "monday" -> Triple(Calendar.MONDAY, "Monday", "mon")
            "tue", "tues", "tuesday" -> Triple(Calendar.TUESDAY, "Tuesday", "tue")
            "wed", "wednesday" -> Triple(Calendar.WEDNESDAY, "Wednesday", "wed")
            "thu", "thur", "thurs", "thursday" -> Triple(Calendar.THURSDAY, "Thursday", "thu")
            "fri", "friday" -> Triple(Calendar.FRIDAY, "Friday", "fri")
            "sat", "saturday" -> Triple(Calendar.SATURDAY, "Saturday", "sat")
            "sun", "sunday" -> Triple(Calendar.SUNDAY, "Sunday", "sun")
            else -> return null
        }

        val timePart = raw.replace(weekdayStr, "").trim()
        var targetHour = 9
        var targetMin = 0
        var timeSpecified = false

        if (timePart.isNotEmpty()) {
            val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(timePart)
            if (amPmMatch != null) {
                var hour = amPmMatch.groupValues[1].toInt()
                val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
                val amPm = amPmMatch.groupValues[3]
                if (hour in 1..12 && min in 0..59) {
                    if (amPm == "pm" && hour < 12) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    targetHour = hour
                    targetMin = min
                    timeSpecified = true
                } else return null
            } else {
                val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(timePart)
                if (timeColonMatch != null) {
                    val hour = timeColonMatch.groupValues[1].toInt()
                    val min = timeColonMatch.groupValues[2].toInt()
                    if (hour in 0..23 && min in 0..59) {
                        targetHour = hour
                        targetMin = min
                        timeSpecified = true
                    } else return null
                } else if (timePart.length in 3..4 && timePart.all { it.isDigit() }) {
                    val hour = if (timePart.length == 4) timePart.substring(0, 2).toInt() else timePart.substring(0, 1).toInt()
                    val min = if (timePart.length == 4) timePart.substring(2, 4).toInt() else timePart.substring(1, 3).toInt()
                    if (hour in 0..23 && min in 0..59) {
                        targetHour = hour
                        targetMin = min
                        timeSpecified = true
                    } else return null
                } else if (timePart.all { it.isDigit() }) {
                    val hour = timePart.toInt()
                    if (hour in 0..23) {
                        targetHour = hour
                        targetMin = 0
                        timeSpecified = true
                    } else return null
                } else {
                    return null
                }
            }
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDayOfWeek = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.DAY_OF_WEEK)
        var daysDiff = dayOfWeek - currentDayOfWeek
        if (daysDiff < 0) {
            daysDiff += 7
        } else if (daysDiff == 0 && cal.timeInMillis <= nowMillis) {
            daysDiff = 7
        }

        if (daysDiff > 0) {
            cal.add(Calendar.DAY_OF_YEAR, daysDiff)
        }

        val snoozeMs = cal.timeInMillis - nowMillis
        val timeFormatted = String.format(Locale.US, "%02d:%02d", targetHour, targetMin)
        val durationLabel = "$fullDisplayName at $timeFormatted"
        val canonicalChoice = if (timeSpecified) "$shortAbbr $timeFormatted" else shortAbbr

        return Pair(Pair(snoozeMs, durationLabel), canonicalChoice)
    }

    private fun parseDateSnooze(raw: String, nowMillis: Long): Pair<Long, String>? {
        val tokens = raw.trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return null

        val datePart = tokens[0]
        val timePart = if (tokens.size > 1) tokens.subList(1, tokens.size).joinToString(" ") else ""

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = nowCal.get(Calendar.YEAR)

        var parsedYear: Int? = null
        var parsedMonth: Int? = null
        var parsedDay: Int? = null
        var noYearSpecified = false

        val flexibleDateMatch = Regex("^(\\d{1,4})[/.\\-](\\d{1,4})(?:[/.\\-](\\d{1,4}))?$").find(datePart)
        if (flexibleDateMatch != null) {
            val num1 = flexibleDateMatch.groupValues[1].toInt()
            val num2 = flexibleDateMatch.groupValues[2].toInt()
            val num3Str = flexibleDateMatch.groupValues[3]

            if (num3Str.isNotEmpty()) {
                val num3 = num3Str.toInt()
                if (num1 > 31) {
                    parsedYear = num1
                    parsedMonth = num2
                    parsedDay = num3
                } else {
                    parsedYear = if (num3 < 100) 2000 + num3 else num3
                    if (num2 > 12) {
                        parsedMonth = num1
                        parsedDay = num2
                    } else if (num1 > 12) {
                        parsedDay = num1
                        parsedMonth = num2
                    } else {
                        parsedMonth = num1
                        parsedDay = num2
                    }
                }
            } else {
                noYearSpecified = true
                parsedYear = currentYear
                if (num2 > 12) {
                    parsedMonth = num1
                    parsedDay = num2
                } else if (num1 > 12) {
                    parsedDay = num1
                    parsedMonth = num2
                } else {
                    parsedMonth = num1
                    parsedDay = num2
                }
            }
        } else {
            val dateFormats = listOfNotNull(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("M/d/yyyy", Locale.getDefault()),
                SimpleDateFormat("d/M/yyyy", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yy", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
                DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()),
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            )

            var parsedDate: Date? = null
            for (df in dateFormats) {
                try {
                    df.isLenient = false
                    parsedDate = df.parse(datePart)
                    if (parsedDate != null) break
                } catch (_: Exception) {}
            }

            if (parsedDate != null) {
                val calTemp = Calendar.getInstance().apply { time = parsedDate }
                parsedYear = calTemp.get(Calendar.YEAR)
                parsedMonth = calTemp.get(Calendar.MONTH) + 1
                parsedDay = calTemp.get(Calendar.DAY_OF_MONTH)
            }
        }

        if (parsedYear == null || parsedMonth == null || parsedDay == null) return null
        if (parsedMonth !in 1..12 || parsedDay !in 1..31) return null

        var targetHour = 9
        var targetMin = 0

        if (timePart.isNotEmpty()) {
            val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(timePart)
            if (amPmMatch != null) {
                var hour = amPmMatch.groupValues[1].toInt()
                val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
                val amPm = amPmMatch.groupValues[3]
                if (hour in 1..12 && min in 0..59) {
                    if (amPm == "pm" && hour < 12) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    targetHour = hour
                    targetMin = min
                } else return null
            } else {
                val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(timePart)
                if (timeColonMatch != null) {
                    val hour = timeColonMatch.groupValues[1].toInt()
                    val min = timeColonMatch.groupValues[2].toInt()
                    if (hour in 0..23 && min in 0..59) {
                        targetHour = hour
                        targetMin = min
                    } else return null
                } else if (timePart.length in 3..4 && timePart.all { it.isDigit() }) {
                    val hour = if (timePart.length == 4) timePart.substring(0, 2).toInt() else timePart.substring(0, 1).toInt()
                    val min = if (timePart.length == 4) timePart.substring(2, 4).toInt() else timePart.substring(1, 3).toInt()
                    if (hour in 0..23 && min in 0..59) {
                        targetHour = hour
                        targetMin = min
                    } else return null
                } else if (timePart.all { it.isDigit() }) {
                    val hour = timePart.toInt()
                    if (hour in 0..23) {
                        targetHour = hour
                        targetMin = 0
                    } else return null
                } else return null
            }
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.YEAR, parsedYear)
            set(Calendar.MONTH, parsedMonth - 1)
            set(Calendar.DAY_OF_MONTH, parsedDay)
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (noYearSpecified && cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.YEAR, 1)
        }

        val snoozeMs = cal.timeInMillis - nowMillis
        if (snoozeMs <= 0) return null

        val durationLabel = com.bas080.notificationreminders.MainActivity.formatSnoozeUntil(cal.timeInMillis, nowMillis)
        return Pair(snoozeMs, durationLabel)
    }

    private fun calculateAbsoluteTimeSnooze(targetHour: Int, targetMin: Int, nowMillis: Long): Pair<Long, String> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var targetTime = cal.timeInMillis
        var dayLabel = "today"
        if (targetTime <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            targetTime = cal.timeInMillis
            dayLabel = "tomorrow"
        }
        val snoozeMs = targetTime - nowMillis
        val timeFormatted = String.format(Locale.US, "%02d:%02d", targetHour, targetMin)
        return Pair(snoozeMs, "$dayLabel at $timeFormatted")
    }
}

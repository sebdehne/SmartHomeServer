package com.dehnes.smarthome

import com.dehnes.smarthome.utils.timestampSecondsSince2000
import org.junit.jupiter.api.Test
import java.time.Instant

class DS3231_Test {

    @Test
    fun test() {
        check(Instant.parse("2000-01-01T00:00:00Z"), "0-1-1T0:0:0")
        check(Instant.parse("2000-12-31T23:59:59Z"), "0-12-31T23:59:59")
        check(Instant.parse("2001-01-01T00:00:00Z"), "1-1-1T0:0:0")
        check(Instant.parse("2001-12-31T23:59:59Z"), "1-12-31T23:59:59")
        check(Instant.parse("2002-01-01T00:00:00Z"), "2-1-1T0:0:0")
        check(Instant.parse("2002-12-31T23:59:59Z"), "2-12-31T23:59:59")
        check(Instant.parse("2003-01-01T00:00:00Z"), "3-1-1T0:0:0")
        check(Instant.parse("2003-12-31T23:59:59Z"), "3-12-31T23:59:59")
        check(Instant.parse("2004-01-01T00:00:00Z"), "4-1-1T0:0:0")
        check(Instant.parse("2004-12-31T23:59:59Z"), "4-12-31T23:59:59")
        check(Instant.parse("2005-01-01T00:00:00Z"), "5-1-1T0:0:0")
    }

    fun check(time: Instant, expected: String) {
        val converted = calc(time)
        if (converted != expected) {
            error("Expected $expected but was $converted")
        }
    }

    val days4Years = 3 * 365 + 366

    fun calc(time: Instant): String {
        val secondsSince2000 = time.timestampSecondsSince2000()
        var days = secondsSince2000 / 86400
        var remainingSeconds = secondsSince2000 % 86400
        var hour = remainingSeconds / 3600
        remainingSeconds = remainingSeconds % 3600
        var minutes = remainingSeconds / 60
        var seconds = remainingSeconds % 60

        var fourYearsPeriodes = days / days4Years
        days = days % days4Years
        val daysInYears = listOf(
            366,
            365,
            365,
        )
        var year = fourYearsPeriodes * 4
        for (i in daysInYears) {
            if (days >= i) {
                year += 1;
                days -= i;
            } else {
                break
            }
        }

        var month = 1
        var daysInMonth = 0
        while (true) {
            if (month == 4 || month == 6 || month == 9 || month == 11) {
                daysInMonth = 30;
            } else if (month == 2) {
                if (year % 4 != 0L) {
                    daysInMonth = 28;
                } else {
                    daysInMonth = 29;
                }
            } else {
                daysInMonth = 31;
            }

            if (days >= daysInMonth) {
                days -= daysInMonth;
                month++;
            } else {
                break;
            }
        }
        var date = days + 1;

        return "$year-${month}-${date}T$hour:$minutes:$seconds"
    }
}
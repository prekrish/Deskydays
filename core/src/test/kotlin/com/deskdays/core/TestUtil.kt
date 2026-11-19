package com.deskdays.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val SYD: ZoneId = ZoneId.of("Australia/Sydney")

/** "2026-11-19 09:12" in Sydney time. */
fun at(s: String): Instant = LocalDateTime.parse(s.replace(' ', 'T')).atZone(SYD).toInstant()

fun day(s: String): LocalDate = LocalDate.parse(s)

fun enter(s: String, src: EventSource = EventSource.GEOFENCE) = PresenceEvent(at(s), EventType.ENTER, src)
fun exit(s: String, src: EventSource = EventSource.GEOFENCE) = PresenceEvent(at(s), EventType.EXIT, src)

/** A full office day (09:00–15:00 by default) on [date]. */
fun officeDay(date: String, from: String = "09:00", to: String = "15:00") =
    listOf(enter("$date $from"), exit("$date $to"))

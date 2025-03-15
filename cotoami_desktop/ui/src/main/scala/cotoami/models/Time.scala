package cotoami.models

import scala.scalajs.js

import java.time._
import java.time.format.DateTimeFormatter

case class Time(
    zone: ZoneId = ZoneId.of("UTC")
) {
  def setZoneOffsetInSeconds(seconds: Int): Time =
    copy(zone = ZoneOffset.ofTotalSeconds(seconds))

  def toDateTime(instant: Instant): LocalDateTime =
    LocalDateTime.ofInstant(instant, zone)

  def formatDateTime(instant: Instant): String = {
    toDateTime(instant).format(Time.DateTimeFormat)
  }

  def formatDate(instant: Instant): String = {
    toDateTime(instant).format(Time.DateFormat)
  }

  def display(instant: Instant): String = {
    val now = LocalDateTime.now(zone)
    val dateTime = toDateTime(instant)
    if (dateTime.toLocalDate() == now.toLocalDate()) {
      dateTime.format(Time.SameDayFormat)
    } else if (dateTime.getYear() == now.getYear()) {
      dateTime.format(Time.SameYearFormat)
    } else {
      dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
  }
}

object Time {
  val DateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val SameYearFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
  val SameDayFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

  def toInstant(date: js.Date): Instant =
    Instant.ofEpochMilli(date.getTime().toLong)

  def toUtcDateTime(instant: Instant): LocalDateTime =
    LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

  def toUtcDateTime(date: js.Date): LocalDateTime =
    toUtcDateTime(toInstant(date))
}

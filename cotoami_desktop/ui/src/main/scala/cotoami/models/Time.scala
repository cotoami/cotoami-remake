package cotoami.models

import java.time._
import java.time.format.DateTimeFormatter

case class Time(
    zone: ZoneId = ZoneId.of("UTC")
) {
  def setZoneOffsetInSeconds(seconds: Int): Time =
    this.copy(zone = ZoneOffset.ofTotalSeconds(seconds))

  def toDateTime(instant: Instant): LocalDateTime =
    LocalDateTime.ofInstant(instant, this.zone)

  def formatDateTime(instant: Instant): String = {
    this.toDateTime(instant).format(Time.DefaultDateTimeFormatter)
  }

  def display(instant: Instant): String = {
    val now = LocalDateTime.now(this.zone)
    val dateTime = this.toDateTime(instant)
    if (dateTime.toLocalDate() == now.toLocalDate()) {
      dateTime.format(Time.SameDayFormatter)
    } else if (dateTime.getYear() == now.getYear()) {
      dateTime.format(Time.SameYearFormatter)
    } else {
      dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
  }
}

object Time {
  val DefaultDateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val SameYearFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
  val SameDayFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
}

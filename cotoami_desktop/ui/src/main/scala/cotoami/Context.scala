package cotoami

import java.time._
import java.time.format.DateTimeFormatter

case class Context(
    zone: ZoneId = ZoneId.of("UTC")
) {
  def toDateTime(instant: Instant): LocalDateTime =
    LocalDateTime.ofInstant(instant, this.zone)

  def formatDateTime(instant: Instant): String = {
    this.toDateTime(instant).format(Context.DefaultDateTimeFormatter)
  }

  def display(instant: Instant): String = {
    val now = LocalDateTime.now(this.zone)
    val dateTime = this.toDateTime(instant)
    if (dateTime.toLocalDate() == now.toLocalDate()) {
      dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
    } else if (dateTime.getYear() == now.getYear()) {
      dateTime.format(Context.SameYearFormatter)
    } else {
      dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
  }
}

object Context {
  val DefaultDateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val SameYearFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
}

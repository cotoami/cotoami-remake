package cotoami.models

import scala.util.{Failure, Success}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import java.time.Instant
import java.time.format.DateTimeFormatter
import cats.effect.IO

import marubinotto.fui.Cmd
import cotoami.libs.exifr

case class DateTimeRange(
    startUtcIso: String,
    endUtcIso: Option[String] = None
) {
  lazy val start: Instant = parseUtcIso(startUtcIso)
  lazy val end: Option[Instant] = endUtcIso.map(parseUtcIso)
}

object DateTimeRange {
  val DateTimeTag = "DateTimeOriginal"

  def fromJsDate(date: js.Date): DateTimeRange = {
    DateTimeRange(
      Time.toUtcDateTime(date).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
  }

  def fromExif(
      file: dom.Blob
  ): Cmd.One[Either[Throwable, Option[DateTimeRange]]] =
    Cmd(IO.async { cb =>
      IO {
        exifr.parse(file, js.Array(DateTimeTag)).onComplete {
          case Success(values) => {
            val timeRange = values.toOption.flatMap(
              _.get(DateTimeTag).flatMap {
                case date: js.Date => Some(DateTimeRange.fromJsDate(date))
                case _             => None
              }
            )
            cb(Right(Some(Right(timeRange))))
          }
          case Failure(t) => cb(Right(Some(Left(t))))
        }
        None // no finalizer on cancellation
      }
    })
}

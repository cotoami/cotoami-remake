package cotoami.utils

import scala.collection.immutable.Queue
import scala.scalajs.js

case class Log(
    entries: Queue[Log.Entry] = Queue.empty,
    maxSize: Int = 100
) {
  def lastEntry(): Option[Log.Entry] = entries.lastOption

  def debug(message: String, details: Option[String] = None): Log =
    log(Log.Debug, message, details)
  def info(message: String, details: Option[String] = None): Log =
    log(Log.Info, message, details)
  def warn(message: String, details: Option[String] = None): Log =
    log(Log.Warn, message, details)
  def error(message: String, details: Option[String] = None): Log =
    log(Log.Error, message, details)

  def log(
      level: Log.Level,
      message: String,
      details: Option[String] = None
  ): Log = {
    addEntry(Log.Entry(level, message, details))
  }

  def addEntry(entry: Log.Entry): Log = {
    var entries = this.entries.enqueue(entry)
    while (entries.size > maxSize) {
      entries = entries.dequeue._2
    }
    copy(entries = entries)
  }
}

object Log {
  case class Entry(
      level: Level,
      message: String,
      details: Option[String] = None,
      timestamp: js.Date = new js.Date()
  )

  sealed trait Level {
    val name: String
    val value: Int
    val icon: String
  }

  val levels = Map(
    Debug.name -> Debug,
    Info.name -> Info,
    Warn.name -> Warn,
    Error.name -> Error
  )

  object Debug extends Level {
    override val name = "debug"
    override val value = 0
    override val icon = "manufacturing"
  }

  object Info extends Level {
    override val name = "info"
    override val value = 1
    override val icon = "info_i"
  }

  object Warn extends Level {
    override val name = "warn"
    override val value = 2
    override val icon = "warning"
  }

  object Error extends Level {
    override val name = "error"
    override val value = 3
    override val icon = "priority_high"
  }
}

package cotoami

import scala.collection.immutable.Queue

case class Log(
    entries: Queue[Log.Entry] = Queue.empty,
    maxSize: Int = 100
) {
  def lastEntry(): Option[Log.Entry] = this.entries.lastOption

  def debug(message: String): Log = this.addEntry(Log.Debug, message)
  def info(message: String): Log = this.addEntry(Log.Info, message)
  def warn(message: String): Log = this.addEntry(Log.Warn, message)
  def error(message: String): Log = this.addEntry(Log.Error, message)

  def addEntry(level: Log.Level, message: String): Log = {
    var entries = this.entries.enqueue(Log.Entry(level, message))
    while (entries.size > this.maxSize) {
      entries = entries.dequeue._2
    }
    this.copy(entries = entries)
  }
}

object Log {
  sealed trait Level {
    val name: String
    val value: Int
    val icon: String
  }

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

  case class Entry(level: Level, message: String)
}

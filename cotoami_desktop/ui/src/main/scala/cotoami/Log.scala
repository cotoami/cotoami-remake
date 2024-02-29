package cotoami

import scala.collection.immutable.Queue

case class Log(
    entries: Queue[Log.Entry] = Queue.empty,
    maxSize: Int = 100
) {
  def lastEntry(): Option[Log.Entry] = this.entries.lastOption

  def debug(message: String): Log = this.addEntry("debug", message)
  def info(message: String): Log = this.addEntry("info", message)
  def error(message: String): Log = this.addEntry("error", message)

  def addEntry(level: String, message: String): Log = {
    var entries = this.entries.enqueue(Log.Entry(level, message))
    while (entries.size > this.maxSize) {
      entries = entries.dequeue._2
    }
    this.copy(entries = entries)
  }
}

object Log {
  case class Entry(level: String, message: String)
}

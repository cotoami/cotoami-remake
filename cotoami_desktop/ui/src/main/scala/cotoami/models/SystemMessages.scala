package cotoami.models

import scala.collection.immutable.Queue
import scala.scalajs.js

case class SystemMessages(
    entries: Queue[SystemMessages.Entry] = Queue.empty,
    maxSize: Int = 100
) {
  def info(message: String, details: Option[String] = None): SystemMessages =
    add(SystemMessages.Info, message, details)

  def error(message: String, details: Option[String] = None): SystemMessages =
    add(SystemMessages.Error, message, details)

  def add(
      category: SystemMessages.Category,
      message: String,
      details: Option[String] = None
  ): SystemMessages = {
    add(SystemMessages.Entry(category, message, details))
  }

  def add(entry: SystemMessages.Entry): SystemMessages = {
    var entries = this.entries.enqueue(entry)
    while (entries.size > maxSize) {
      entries = entries.dequeue._2
    }
    copy(entries = entries)
  }

  def lastEntry: Option[SystemMessages.Entry] = entries.lastOption
}

object SystemMessages {
  case class Entry(
      category: Category,
      message: String,
      details: Option[String] = None,
      timestamp: js.Date = new js.Date()
  )

  sealed trait Category {
    val name: String
    val icon: String
  }

  object Info extends Category {
    override val name = "info"
    override val icon = "info_i"
  }

  object Error extends Category {
    override val name = "error"
    override val icon = "error"
  }
}

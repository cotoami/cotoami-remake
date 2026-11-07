package marubinotto.fui

import cats.effect.IO
import org.scalajs.dom

/** Unhandled failures in framework-managed work. Cancellation is not an error. */
enum RuntimeError {
  case Command(cause: Throwable)
  case Subscription(id: String, cause: Throwable)
  case UpdateLoop(cause: Throwable)
}

object RuntimeError {
  /** Default reporter. Custom reporters must terminate and should not cancel themselves. */
  def log(error: RuntimeError): IO[Unit] = IO {
    val (location, cause) = error match {
      case Command(cause) => ("command", cause)
      case Subscription(id, cause) => (s"subscription '$id'", cause)
      case UpdateLoop(cause) => ("update loop", cause)
    }
    dom.console.error(s"Scalafui $location failed", cause.toString,
      cause.getStackTrace.mkString("\n"))
  }
}

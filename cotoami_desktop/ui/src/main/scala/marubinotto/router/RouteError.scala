package marubinotto.router

/** Failures produced while rendering a route as a URL. */
sealed abstract class RouteError(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

object RouteError {
  final case class PathArgumentRejected()
      extends RouteError("Could not encode route argument")
  final case class QueryParameterRejected(name: String)
      extends RouteError(s"Could not encode query parameter: $name")
  final case class EncodingFailed(underlying: Throwable)
      extends RouteError("Route encoding failed", underlying)
}

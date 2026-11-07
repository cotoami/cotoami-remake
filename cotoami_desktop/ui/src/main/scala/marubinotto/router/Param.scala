package marubinotto.router

/** A required, named query parameter, decoded using its Codec. */
final case class Param[A](name: String)

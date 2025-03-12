package cotoami.models

case class CotoMarker(
    location: Geolocation,
    cotos: Seq[Coto],
    nodeIconUrls: Set[String],
    inFocus: Boolean
) {
  def addCoto(
      coto: Coto,
      nodeIconUrl: String,
      inFocus: Boolean
  ): CotoMarker =
    copy(
      cotos = cotos :+ coto,
      nodeIconUrls = nodeIconUrls + nodeIconUrl,
      inFocus = inFocus || this.inFocus
    )

  def containsCotonomas: Boolean = cotos.exists(_.isCotonoma)

  def label: Option[String] = cotos match {
    case Seq()     => None
    case Seq(coto) => coto.nameAsCotonoma
    case cotos =>
      cotos.flatMap(_.nameAsCotonoma) match {
        case Seq() => None
        case names => Some(names.mkString(" / "))
      }
  }
}

package cotoami.models

import cotoami.backend.Coto

trait CotoContent {
  def content: Option[String]
  def summary: Option[String]
  def mediaContent: Option[(String, String)]
  def geolocation: Option[Geolocation]
  def isCotonoma: Boolean

  def nameAsCotonoma: Option[String] =
    if (this.isCotonoma)
      this.summary.orElse(this.content)
    else
      None

  lazy val abbreviate: Option[String] =
    this.summary.orElse(
      this.content.map(content => {
        val text = Coto.stripMarkdown.processSync(content).toString()
        if (text.size > Cotonoma.NameMaxLength)
          s"${text.substring(0, Cotonoma.NameMaxLength)}…"
        else
          text
      })
    )
}

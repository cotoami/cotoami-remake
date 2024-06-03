package cotoami.backend

import scala.scalajs.js
import java.time.Instant
import cotoami.utils.Validation

case class ServerNode(json: ServerNodeJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  def urlPrefix: String = this.json.url_prefix
  def disabled: Boolean = this.json.disabled
}

object ServerNode {
  val UrlMaxLength = 1500

  def validateUrl(url: String): Seq[Validation.Error] = {
    val fieldName = "url"
    Seq(
      Validation.nonBlank(fieldName, url),
      Validation.length(fieldName, url, 1, UrlMaxLength),
      Validation.httpUrl(fieldName, url)
    ).flatten
  }
}

@js.native
trait ServerNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val url_prefix: String = js.native
  val disabled: Boolean = js.native
}

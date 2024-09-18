package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Cotonoma, Geolocation, Id}

@js.native
trait CotoJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val posted_in_id: Nullable[String] = js.native
  val posted_by_id: String = js.native
  val content: Nullable[String] = js.native
  val summary: Nullable[String] = js.native
  val media_content: Nullable[String] = js.native
  val media_type: Nullable[String] = js.native
  val is_cotonoma: Boolean = js.native
  val longitude: Nullable[Double] = js.native
  val latitude: Nullable[Double] = js.native
  val repost_of_id: Nullable[String] = js.native
  val reposted_in_ids: Nullable[js.Array[String]] = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val outgoing_links: Int = js.native
}

object CotoJson {
  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, CotoJson]] =
    Commands.send(
      Commands.PostCoto(content, summary, mediaContent, location, postTo)
    )
}

package cotoami.backend

import scala.scalajs.js

import fui.{Browser, Cmd}
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id}

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
  val datetime_start: Nullable[String] = js.native
  val datetime_end: Nullable[String] = js.native
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
  ): Cmd.One[Either[ErrorJson, CotoJson]] =
    Commands.send(
      Commands.PostCoto(content, summary, mediaContent, location, postTo)
    )
}

object CotoBackend {
  def toModel(json: CotoJson, posted: Boolean = false): Coto =
    Coto(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      postedInId = Nullable.toOption(json.posted_in_id).map(Id(_)),
      postedById = Id(json.posted_by_id),
      content = Nullable.toOption(json.content),
      summary = Nullable.toOption(json.summary),
      mediaContent = (
        Nullable.toOption(json.media_content),
        Nullable.toOption(json.media_type)
      ) match {
        case (Some(content), Some(mediaType)) =>
          Some((Browser.decodeBase64(content, mediaType), mediaType))
        case _ => None
      },
      geolocation = (
        Nullable.toOption(json.longitude),
        Nullable.toOption(json.latitude)
      ) match {
        case (Some(longitude), Some(latitude)) =>
          Some(Geolocation.fromLngLat((longitude, latitude)))
        case _ => None
      },
      dateTimeRange = Nullable.toOption(json.datetime_start).map(start =>
        DateTimeRange(start, Nullable.toOption(json.datetime_end))
      ),
      isCotonoma = json.is_cotonoma,
      repostOfId = Nullable.toOption(json.repost_of_id).map(Id(_)),
      repostedInIds = Nullable.toOption(json.reposted_in_ids)
        .map(_.map(Id[Cotonoma](_)).toSeq),
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at,
      outgoingLinks = json.outgoing_links,
      posted = posted
    )

  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, Coto]] =
    CotoJson.post(content, summary, mediaContent, location, postTo)
      .map(_.map(CotoBackend.toModel(_)))
}

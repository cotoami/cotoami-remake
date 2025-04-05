package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.{Browser, Cmd}
import marubinotto.facade.Nullable

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
}

object CotoJson {
  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, CotoJson]] =
    Commands.send(
      Commands.PostCoto(
        content,
        summary,
        mediaContent,
        location,
        timeRange,
        postTo
      )
    )

  def edit(
      id: Id[Coto],
      content: Option[String],
      summary: Option[Option[String]],
      mediaContent: Option[Option[(String, String)]],
      location: Option[Option[Geolocation]],
      timeRange: Option[Option[DateTimeRange]]
  ): Cmd.One[Either[ErrorJson, CotoJson]] =
    Commands.send(
      Commands.EditCoto(
        id,
        content,
        summary,
        mediaContent,
        location,
        timeRange
      )
    )

  def promote(
      id: Id[Coto]
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.Promote(id))

  def delete(id: Id[Coto]): Cmd.One[Either[ErrorJson, String]] =
    Commands.send(Commands.DeleteCoto(id))

  def repost(
      id: Id[Coto],
      dest: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotoJson, CotoJson]]] =
    Commands.send(Commands.Repost(id, dest))
}

object CotoBackend {
  def toModel(json: CotoJson): Coto =
    Coto(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      postedInId = Nullable.toOption(json.posted_in_id).map(Id(_)),
      postedById = Id(json.posted_by_id),
      content = Nullable.toOption(json.content),
      summary = Nullable.toOption(json.summary),
      mediaBlob = (
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
      updatedAtUtcIso = json.updated_at
    )

  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, Coto]] =
    CotoJson.post(content, summary, mediaContent, location, timeRange, postTo)
      .map(_.map(CotoBackend.toModel))

  def edit(
      id: Id[Coto],
      content: Option[String],
      summary: Option[Option[String]],
      mediaContent: Option[Option[(String, String)]],
      location: Option[Option[Geolocation]],
      timeRange: Option[Option[DateTimeRange]]
  ): Cmd.One[Either[ErrorJson, Coto]] =
    CotoJson.edit(id, content, summary, mediaContent, location, timeRange)
      .map(_.map(CotoBackend.toModel))

  def promote(
      id: Id[Coto]
  ): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotoJson.promote(id).map(
      _.map(pair =>
        (CotonomaBackend.toModel(pair._1), CotoBackend.toModel(pair._2))
      )
    )

  def delete(id: Id[Coto]): Cmd.One[Either[ErrorJson, Id[Coto]]] =
    CotoJson.delete(id).map(_.map(Id(_)))

  def repost(
      id: Id[Coto],
      dest: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, (Coto, Coto)]] =
    CotoJson.repost(id, dest).map(
      _.map(pair =>
        (CotoBackend.toModel(pair._1), CotoBackend.toModel(pair._2))
      )
    )
}

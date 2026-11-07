package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import marubinotto.fui.Browser
import marubinotto.facade.Nullable

import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Id, Ito}

case class CotoInput(
    content: String,
    summary: Option[String],
    mediaContent: Option[(String, String)],
    location: Option[Geolocation],
    timeRange: Option[DateTimeRange]
) {
  def toJson = jso(
    content = content,
    summary = summary.getOrElse(null),
    media_content = mediaContent.map(js.Tuple2.fromScalaTuple2).getOrElse(null),
    geolocation = location.map(geolocationJson).getOrElse(null),
    datetime_range = timeRange.map(dateTimeRangeJson).getOrElse(null)
  )
}

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
      input: CotoInput,
      postTo: Id[Cotonoma]
  ): IO[Either[ErrorJson, CotoJson]] =
    Commands.send(Commands.PostCoto(input, postTo))

  def edit(
      id: Id[Coto],
      content: Option[String],
      summary: Option[Option[String]],
      mediaContent: Option[Option[(String, String)]],
      location: Option[Option[Geolocation]],
      timeRange: Option[Option[DateTimeRange]]
  ): IO[Either[ErrorJson, CotoJson]] =
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
  ): IO[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.Promote(id))

  def delete(id: Id[Coto]): IO[Either[ErrorJson, String]] =
    Commands.send(Commands.DeleteCoto(id))

  def repost(
      id: Id[Coto],
      dest: Id[Cotonoma]
  ): IO[Either[ErrorJson, js.Tuple2[CotoJson, CotoJson]]] =
    Commands.send(Commands.Repost(id, dest))

  def postSubcoto(
      sourceCotoId: Id[Coto],
      input: CotoInput,
      postTo: Id[Cotonoma],
      order: Option[Int]
  ): IO[Either[ErrorJson, js.Tuple2[CotoJson, ItoJson]]] =
    Commands.send(Commands.PostSubcoto(sourceCotoId, input, postTo, order))
}

object CotoBackend {
  def toModel(json: CotoJson): IO[Coto] = {
    val media = for {
      content <- Nullable.toOption(json.media_content)
      mediaType <- Nullable.toOption(json.media_type)
    } yield (content, mediaType)
    media.traverse { case (content, mediaType) =>
      Browser.decodeBase64(content, mediaType).map(blob => (blob, mediaType))
    }.map { mediaBlob =>
      Coto(
        id = Id(json.uuid),
        nodeId = Id(json.node_id),
        postedInId = Nullable.toOption(json.posted_in_id).map(Id(_)),
        postedById = Id(json.posted_by_id),
        content = Nullable.toOption(json.content),
        summary = Nullable.toOption(json.summary),
        mediaBlob = mediaBlob,
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
    }
  }

  def post(
      input: CotoInput,
      postTo: Id[Cotonoma]
  ): IO[Either[ErrorJson, Coto]] =
    CotoJson.post(input, postTo)
      .flatMap(_.traverse(CotoBackend.toModel))

  def edit(
      id: Id[Coto],
      content: Option[String],
      summary: Option[Option[String]],
      mediaContent: Option[Option[(String, String)]],
      location: Option[Option[Geolocation]],
      timeRange: Option[Option[DateTimeRange]]
  ): IO[Either[ErrorJson, Coto]] =
    CotoJson.edit(id, content, summary, mediaContent, location, timeRange)
      .flatMap(_.traverse(CotoBackend.toModel))

  def promote(
      id: Id[Coto]
  ): IO[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotoJson.promote(id).flatMap(_.traverse(CotonomaBackend.decodePair))

  def delete(id: Id[Coto]): IO[Either[ErrorJson, Id[Coto]]] =
    CotoJson.delete(id).map(_.map(Id(_)))

  def repost(
      id: Id[Coto],
      dest: Id[Cotonoma]
  ): IO[Either[ErrorJson, (Coto, Coto)]] =
    CotoJson.repost(id, dest).flatMap(
      _.traverse(pair => (toModel(pair._1), toModel(pair._2)).tupled)
    )

  def postSubcoto(
      sourceCotoId: Id[Coto],
      input: CotoInput,
      postTo: Id[Cotonoma],
      order: Option[Int]
  ): IO[Either[ErrorJson, (Coto, Ito)]] =
    CotoJson.postSubcoto(sourceCotoId, input, postTo, order).flatMap(
      _.traverse(pair =>
        toModel(pair._1).map(coto => (coto, ItoBackend.toModel(pair._2)))
      )
    )
}

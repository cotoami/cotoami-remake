package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.models.{Coto, Id, Link}

@js.native
trait LinkJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val created_by_id: String = js.native
  val source_coto_id: String = js.native
  val target_coto_id: String = js.native
  val linking_phrase: Nullable[String] = js.native
  val details: Nullable[String] = js.native
  val order: Int = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
}

object LinkJson {
  def connect(
      sourceId: Id[Coto],
      targetId: Id[Coto],
      linkingPhrase: Option[String],
      details: Option[String],
      order: Option[Int]
  ): Cmd.One[Either[ErrorJson, LinkJson]] =
    Commands.send(
      Commands.Connect(
        sourceId,
        targetId,
        linkingPhrase,
        details,
        order
      )
    )
}

object LinkBackend {
  def toModel(json: LinkJson): Link =
    Link(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      createdById = Id(json.created_by_id),
      sourceCotoId = Id(json.source_coto_id),
      targetCotoId = Id(json.target_coto_id),
      linkingPhrase = Nullable.toOption(json.linking_phrase),
      details = Nullable.toOption(json.details),
      order = json.order,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at
    )

  def connect(
      sourceId: Id[Coto],
      targetId: Id[Coto],
      linkingPhrase: Option[String],
      details: Option[String],
      order: Option[Int]
  ): Cmd.One[Either[ErrorJson, Link]] =
    LinkJson.connect(
      sourceId,
      targetId,
      linkingPhrase,
      details,
      order
    )
      .map(_.map(LinkBackend.toModel(_)))
}

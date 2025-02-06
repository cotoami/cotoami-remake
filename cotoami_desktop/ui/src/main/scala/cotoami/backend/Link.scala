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
  def fetch(
      id: Id[Link]
  ): Cmd.One[Either[ErrorJson, LinkJson]] =
    Commands.send(Commands.Link(id))

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

  def edit(
      id: Id[Link],
      linkingPhrase: Option[Option[String]],
      details: Option[Option[String]]
  ): Cmd.One[Either[ErrorJson, LinkJson]] =
    Commands.send(Commands.EditLink(id, linkingPhrase, details))

  def disconnect(id: Id[Link]): Cmd.One[Either[ErrorJson, String]] =
    Commands.send(Commands.Disconnect(id))

  def fetchOutgoingLinks(
      cotoId: Id[Coto]
  ): Cmd.One[Either[ErrorJson, js.Array[LinkJson]]] =
    Commands.send(Commands.OutgoingLinks(cotoId))

  def changeOrder(
      id: Id[Link],
      newOrder: Int
  ): Cmd.One[Either[ErrorJson, LinkJson]] =
    Commands.send(Commands.ChangeLinkOrder(id, newOrder))
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

  def fetch(
      id: Id[Link]
  ): Cmd.One[Either[ErrorJson, Link]] =
    LinkJson.fetch(id).map(_.map(toModel))

  def edit(
      id: Id[Link],
      linkingPhrase: Option[Option[String]],
      details: Option[Option[String]]
  ): Cmd.One[Either[ErrorJson, Link]] =
    LinkJson.edit(id, linkingPhrase, details).map(_.map(toModel))

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
    ).map(_.map(toModel))

  def disconnect(id: Id[Link]): Cmd.One[Either[ErrorJson, Id[Link]]] =
    LinkJson.disconnect(id).map(_.map(Id(_)))

  def fetchOutgoingLinks(
      cotoId: Id[Coto]
  ): Cmd.One[Either[ErrorJson, js.Array[Link]]] =
    LinkJson.fetchOutgoingLinks(cotoId).map(_.map(_.map(toModel)))

  def changeOrder(
      id: Id[Link],
      newOrder: Int
  ): Cmd.One[Either[ErrorJson, Link]] =
    LinkJson.changeOrder(id, newOrder).map(_.map(toModel))
}

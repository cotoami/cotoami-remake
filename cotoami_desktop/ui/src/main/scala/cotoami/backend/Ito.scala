package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

import cotoami.models.{Coto, Id, Ito}

@js.native
trait ItoJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val created_by_id: String = js.native
  val source_coto_id: String = js.native
  val target_coto_id: String = js.native
  val description: Nullable[String] = js.native
  val details: Nullable[String] = js.native
  val order: Int = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
}

object ItoJson {
  def fetch(id: Id[Ito]): Cmd.One[Either[ErrorJson, ItoJson]] =
    Commands.send(Commands.Ito(id))

  def create(
      sourceId: Id[Coto],
      targetId: Id[Coto],
      description: Option[String],
      details: Option[String],
      order: Option[Int]
  ): Cmd.One[Either[ErrorJson, ItoJson]] =
    Commands.send(
      Commands.CreateIto(
        sourceId,
        targetId,
        description,
        details,
        order
      )
    )

  def edit(
      id: Id[Ito],
      description: Option[Option[String]],
      details: Option[Option[String]]
  ): Cmd.One[Either[ErrorJson, ItoJson]] =
    Commands.send(Commands.EditIto(id, description, details))

  def delete(id: Id[Ito]): Cmd.One[Either[ErrorJson, String]] =
    Commands.send(Commands.DeleteIto(id))

  def fetchOutgoingItos(
      cotoId: Id[Coto]
  ): Cmd.One[Either[ErrorJson, js.Array[ItoJson]]] =
    Commands.send(Commands.OutgoingItos(cotoId))

  def changeOrder(
      id: Id[Ito],
      newOrder: Int
  ): Cmd.One[Either[ErrorJson, ItoJson]] =
    Commands.send(Commands.ChangeItoOrder(id, newOrder))
}

object ItoBackend {
  def toModel(json: ItoJson): Ito =
    Ito(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      createdById = Id(json.created_by_id),
      sourceCotoId = Id(json.source_coto_id),
      targetCotoId = Id(json.target_coto_id),
      description = Nullable.toOption(json.description),
      details = Nullable.toOption(json.details),
      order = json.order,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at
    )

  def fetch(
      id: Id[Ito]
  ): Cmd.One[Either[ErrorJson, Ito]] =
    ItoJson.fetch(id).map(_.map(toModel))

  def edit(
      id: Id[Ito],
      description: Option[Option[String]],
      details: Option[Option[String]]
  ): Cmd.One[Either[ErrorJson, Ito]] =
    ItoJson.edit(id, description, details).map(_.map(toModel))

  def create(
      sourceId: Id[Coto],
      targetId: Id[Coto],
      description: Option[String],
      details: Option[String],
      order: Option[Int]
  ): Cmd.One[Either[ErrorJson, Ito]] =
    ItoJson.create(
      sourceId,
      targetId,
      description,
      details,
      order
    ).map(_.map(toModel))

  def delete(id: Id[Ito]): Cmd.One[Either[ErrorJson, Id[Ito]]] =
    ItoJson.delete(id).map(_.map(Id(_)))

  def fetchOutgoingItos(
      cotoId: Id[Coto]
  ): Cmd.One[Either[ErrorJson, js.Array[Ito]]] =
    ItoJson.fetchOutgoingItos(cotoId).map(_.map(_.map(toModel)))

  def changeOrder(
      id: Id[Ito],
      newOrder: Int
  ): Cmd.One[Either[ErrorJson, Ito]] =
    ItoJson.changeOrder(id, newOrder).map(_.map(toModel))
}

package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable
import cotoami.models.{Id, Node, ParentNode}

@js.native
trait ParentNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val changes_received: Double = js.native
  val last_change_received_at: Nullable[String] = js.native
  val last_read_at: Nullable[String] = js.native
  val forked: Boolean = js.native
}

object ParentNodeJson {
  def fetchOthersLastPostedAt
      : Cmd.One[Either[ErrorJson, js.Dictionary[Nullable[String]]]] =
    Commands.send(Commands.OthersLastPostedAt)
}

object ParentNodeBackend {
  def toModel(json: ParentNodeJson): ParentNode =
    ParentNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      changesReceived = json.changes_received,
      lastChangeReceivedAtUtcIso =
        Nullable.toOption(json.last_change_received_at),
      lastReadAtUtcIso = Nullable.toOption(json.last_read_at),
      forked = json.forked
    )

  def fetchOthersLastPostedAt
      : Cmd.One[Either[ErrorJson, Map[Id[Node], Option[String]]]] =
    ParentNodeJson.fetchOthersLastPostedAt.map(_.map {
      _.map { case (nodeId, utcIso) =>
        Id[Node](nodeId) -> Nullable.toOption(utcIso)
      }.toMap
    })
}

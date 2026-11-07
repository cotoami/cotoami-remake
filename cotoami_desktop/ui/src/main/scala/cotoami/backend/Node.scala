package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*
import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.{Id, Node}

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val name: String = js.native
  val root_cotonoma_id: Nullable[String] = js.native
  val version: Int = js.native
  val created_at: String = js.native
}

object NodeJson {
  def setLocalNodeIcon(icon: String): IO[Either[ErrorJson, NodeJson]] =
    Commands.send(Commands.SetLocalNodeIcon(icon))

  def fetchOthersLastPostedAt: IO[Either[ErrorJson, js.Dictionary[String]]] =
    Commands.send(Commands.OthersLastPostedAt)
}

object NodeBackend {
  def toModel(json: NodeJson): IO[Node] =
    Node.decodeBase64Icon(json.icon).map { icon =>
      Node(
        id = Id(json.uuid),
        icon = icon,
        name = json.name,
        rootCotonomaId = Nullable.toOption(json.root_cotonoma_id).map(Id(_)),
        version = json.version,
        createdAtUtcIso = json.created_at
      )
    }

  def fetchOthersLastPostedAt: IO[Either[ErrorJson, Map[Id[Node], String]]] =
    NodeJson.fetchOthersLastPostedAt.map(_.map {
      _.map { case (nodeId, utcIso) => Id[Node](nodeId) -> utcIso }.toMap
    })

  def markAsRead(nodeId: Option[Id[Node]]): IO[Either[ErrorJson, String]] =
    Commands.send(Commands.MarkAsRead(nodeId))

  def setLocalNodeIcon(icon: String): IO[Either[ErrorJson, Node]] =
    NodeJson.setLocalNodeIcon(icon).flatMap(_.traverse(toModel))
}

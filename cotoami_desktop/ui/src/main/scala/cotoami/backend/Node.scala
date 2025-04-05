package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
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
  def setLocalNodeIcon(icon: String): Cmd.One[Either[ErrorJson, NodeJson]] =
    Commands.send(Commands.SetLocalNodeIcon(icon))
}

object NodeBackend {
  def toModel(json: NodeJson): Node =
    Node(
      id = Id(json.uuid),
      icon = Node.decodeBase64Icon(json.icon),
      name = json.name,
      rootCotonomaId = Nullable.toOption(json.root_cotonoma_id).map(Id(_)),
      version = json.version,
      createdAtUtcIso = json.created_at
    )

  def setLocalNodeIcon(icon: String): Cmd.One[Either[ErrorJson, Node]] =
    NodeJson.setLocalNodeIcon(icon).map(_.map(toModel))
}

package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

import cotoami.models.{Id, LocalNode}

@js.native
trait LocalNodeJson extends js.Object {
  val node_id: String = js.native
  val image_max_size: Nullable[Int] = js.native
  val anonymous_read_enabled: Boolean = js.native
}

object LocalNodeJson {
  def enableAnonymousRead(
      enable: Boolean
  ): Cmd.One[Either[ErrorJson, LocalNodeJson]] =
    Commands.send(Commands.EnableAnonymousRead(enable))
}

object LocalNodeBackend {
  def toModel(json: LocalNodeJson): LocalNode =
    LocalNode(
      nodeId = Id(json.node_id),
      imageMaxSize = Nullable.toOption(json.image_max_size),
      anonymousReadEnabled = json.anonymous_read_enabled
    )

  def enableAnonymousRead(
      enable: Boolean
  ): Cmd.One[Either[ErrorJson, LocalNode]] =
    LocalNodeJson.enableAnonymousRead(enable).map(_.map(toModel))
}

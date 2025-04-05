package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

case class LocalServer(
    activeConfig: Option[ServerConfig],
    anonymousReadEnabled: Boolean,
    anonymousConnections: Int
) {
  def this(json: LocalServerJson) = this(
    Nullable.toOption(json.active_config).map(ServerConfig),
    json.anonymous_read_enabled,
    json.anonymous_connections
  )
}

object LocalServer {
  def fetch: Cmd.One[Either[ErrorJson, LocalServer]] =
    LocalServerJson.fetch.map(_.map(new LocalServer(_)))
}

@js.native
trait LocalServerJson extends js.Object {
  val active_config: Nullable[ServerConfigJson] = js.native
  val anonymous_read_enabled: Boolean = js.native
  val anonymous_connections: Int = js.native
}

object LocalServerJson {
  def fetch: Cmd.One[Either[ErrorJson, LocalServerJson]] =
    Commands.send(Commands.LocalServer)
}

case class ServerConfig(json: ServerConfigJson) {
  def port: Int = json.port
  def urlScheme: String = json.url_scheme
  def urlHost: String = json.url_host
  def urlPort: Option[Int] = Nullable.toOption(json.url_port)
  def enableWebsocket: Boolean = json.enable_websocket

  def url: String = {
    val portSuffix = urlPort.map(port => s":${port}").getOrElse("")
    s"${urlScheme}://${urlHost}${portSuffix}"
  }
}

@js.native
trait ServerConfigJson extends js.Object {
  val port: Int = js.native
  val url_scheme: String = js.native
  val url_host: String = js.native
  val url_port: Nullable[Int] = js.native
  val enable_websocket: Boolean = js.native
}

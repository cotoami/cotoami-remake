package cotoami.backend

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import fui.Cmd
import cotoami.libs.tauri
import cotoami.models._

object Commands {

  def send[T](command: js.Object): Cmd.One[Either[ErrorJson, T]] =
    tauri.invokeCommand(
      "node_command",
      js.Dynamic.literal(command = command)
    ).map((e: Either[ErrorJson, String]) =>
      e.map(js.JSON.parse(_).asInstanceOf[T])
    )

  val LocalNode = jso(LocalNode = null)

  def SetLocalNodeIcon(icon: String) =
    jso(SetLocalNodeIcon = jso(icon = icon))

  def NodeDetails(id: Id[Node]) =
    jso(NodeDetails = jso(id = id.uuid))

  def TryLogIntoServer(
      url_prefix: String,
      password: String,
      client_role: Option[String] = None
  ) =
    jso(TryLogIntoServer =
      jso(
        url_prefix = url_prefix,
        password = password,
        client_role = client_role.getOrElse(null)
      )
    )

  def AddServer(
      url_prefix: String,
      password: String,
      client_role: Option[String] = None
  ) =
    jso(AddServer =
      jso(
        url_prefix = url_prefix,
        password = password,
        client_role = client_role.getOrElse(null)
      )
    )

  def UpdateServer(
      id: Id[Node],
      disabled: Option[Boolean],
      url_prefix: Option[String]
  ) =
    jso(UpdateServer =
      jso(
        id = id.uuid,
        values = jso(
          // For some reason, `disabled.getOrElse(null)` causes a compile error
          // without explicit type conversion (`asInstanceOf`).
          disabled = disabled.getOrElse(null).asInstanceOf[js.Any],
          url_prefix = url_prefix.getOrElse(null)
        )
      )
    )

  def RecentClients(pageIndex: Double, pageSize: Option[Double] = None) =
    jso(RecentClients =
      jso(
        pagination = jso(
          page = pageIndex,
          page_size = pageSize.getOrElse(null).asInstanceOf[js.Any]
        )
      )
    )

  def RecentCotonomas(nodeId: Option[Id[Node]], pageIndex: Double) =
    jso(RecentCotonomas =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def Cotonoma(id: Id[Cotonoma]) =
    jso(Cotonoma = jso(id = id.uuid))

  def CotonomaDetails(id: Id[Cotonoma]) =
    jso(CotonomaDetails = jso(id = id.uuid))

  def CotonomaByName(name: String, node: Id[Node]) =
    jso(CotonomaByName = jso(name = name, node = node.uuid))

  def SubCotonomas(id: Id[Cotonoma], pageIndex: Double) =
    jso(SubCotonomas = jso(id = id.uuid, pagination = jso(page = pageIndex)))

  def RecentCotos(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ) =
    jso(RecentCotos =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def GeolocatedCotos(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]]
  ) =
    jso(GeolocatedCotos =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null)
      )
    )

  def CotosInGeoBounds(bounds: GeoBounds) =
    jso(CotosInGeoBounds =
      jso(
        southwest = jso(
          longitude = bounds.southwest.longitude,
          latitude = bounds.southwest.latitude
        ),
        northeast = jso(
          longitude = bounds.northeast.longitude,
          latitude = bounds.northeast.latitude
        )
      )
    )

  def SearchCotos(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ) =
    jso(SearchCotos =
      jso(
        query = query,
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def GraphFromCoto(coto: Id[Coto]) =
    jso(GraphFromCoto = jso(coto = coto.uuid))

  def GraphFromCotonoma(cotonoma: Id[Cotonoma]) =
    jso(GraphFromCotonoma = jso(cotonoma = cotonoma.uuid))

  def PostCoto(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ) =
    jso(PostCoto =
      jso(
        input = jso(
          content = content,
          summary = summary.getOrElse(null),
          media_content =
            mediaContent.map(js.Tuple2.fromScalaTuple2).getOrElse(null),
          geolocation = geolocation(location)
        ),
        post_to = postTo.uuid
      )
    )

  def PostCotonoma(
      name: String,
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ) =
    jso(PostCotonoma =
      jso(
        input = jso(name = name, geolocation = geolocation(location)),
        post_to = postTo.uuid
      )
    )

  private def geolocation(location: Option[Geolocation]) =
    location.map(location =>
      jso(longitude = location.longitude, latitude = location.latitude)
    ).getOrElse(null)
}

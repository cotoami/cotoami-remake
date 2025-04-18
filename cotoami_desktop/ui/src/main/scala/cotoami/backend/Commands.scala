package cotoami.backend

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import marubinotto.fui.Cmd
import marubinotto.libs.tauri

import cotoami.models._

object Commands {

  def send[T](command: js.Object): Cmd.One[Either[ErrorJson, T]] =
    tauri.invokeCommand(
      "node_command",
      js.Dynamic.literal(command = command)
    ).map((e: Either[ErrorJson, String]) =>
      e.map(json => {
        try {
          js.JSON.parse(json).asInstanceOf[T]
        } catch {
          case e: Throwable =>
            throw new RuntimeException(
              s"[invalid-response] command: ${js.JSON.stringify(command)}, response: ${json}"
            )
        }
      })
    )

  val LocalNode = jso(LocalNode = null)

  val LocalServer = jso(LocalServer = null)

  def SetLocalNodeIcon(icon: String) = jso(SetLocalNodeIcon = jso(icon = icon))

  def SetImageMaxSize(size: Option[Int]) =
    jso(SetImageMaxSize = size.getOrElse(0).asInstanceOf[js.Any])

  def EnableAnonymousRead(enable: Boolean) =
    jso(EnableAnonymousRead = jso(enable = enable))

  def NodeDetails(id: Id[Node]) = jso(NodeDetails = jso(id = id.uuid))

  def TryLogIntoServer(
      url_prefix: String,
      password: Option[String],
      client_role: Option[String] = None
  ) =
    jso(TryLogIntoServer =
      jso(
        url_prefix = url_prefix,
        password = password.getOrElse(null),
        client_role = client_role.getOrElse(null)
      )
    )

  def AddServer(
      url_prefix: String,
      password: Option[String],
      client_role: Option[String] = None
  ) =
    jso(AddServer =
      jso(
        url_prefix = url_prefix,
        password = password.getOrElse(null),
        client_role = client_role.getOrElse(null)
      )
    )

  def EditServer(
      id: Id[Node],
      disabled: Option[Boolean],
      password: Option[String],
      url_prefix: Option[String]
  ) =
    jso(EditServer =
      jso(
        id = id.uuid,
        values = jso(
          disabled = disabled.getOrElse(null).asInstanceOf[js.Any],
          password = password.getOrElse(null),
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

  def ClientNode(id: Id[Node]) = jso(ClientNode = jso(id = id.uuid))

  def AddClient(nodeId: Id[Node], asChild: Option[ChildNodeInputJson]) =
    jso(AddClient =
      jso(
        id = nodeId.uuid,
        as_child = asChild.getOrElse(null)
      )
    )

  def ResetClientPassword(id: Id[Node]) =
    jso(ResetClientPassword = jso(id = id.uuid))

  def EditClient(
      id: Id[Node],
      disabled: Option[Boolean]
  ) =
    jso(EditClient =
      jso(
        id = id.uuid,
        values = jso(
          disabled = disabled.getOrElse(null).asInstanceOf[js.Any]
        )
      )
    )

  def ChildNode(id: Id[Node]) = jso(ChildNode = jso(id = id.uuid))

  def EditChild(
      id: Id[Node],
      values: ChildNodeInputJson
  ) =
    jso(EditChild =
      jso(
        id = id.uuid,
        values = values
      )
    )

  def RecentCotonomas(nodeId: Option[Id[Node]], pageIndex: Double) =
    jso(RecentCotonomas =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def CotonomasByPrefix(prefix: String, nodes: Option[js.Array[Id[Node]]]) =
    jso(CotonomasByPrefix =
      jso(
        prefix = prefix,
        nodes = nodes.map(_.map(_.uuid)).getOrElse((null))
      )
    )

  def Cotonoma(id: Id[Cotonoma]) = jso(Cotonoma = jso(id = id.uuid))

  def CotonomaDetails(id: Id[Cotonoma]) =
    jso(CotonomaDetails = jso(id = id.uuid))

  def CotonomaByCotoId(id: Id[Coto]) = jso(CotonomaByCotoId = jso(id = id.uuid))

  def CotonomaByName(name: String, node: Id[Node]) =
    jso(CotonomaByName = jso(name = name, node = node.uuid))

  def SubCotonomas(id: Id[Cotonoma], pageIndex: Double) =
    jso(SubCotonomas = jso(id = id.uuid, pagination = jso(page = pageIndex)))

  def RecentCotos(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      pageIndex: Double
  ) =
    jso(RecentCotos =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        only_cotonomas = onlyCotonomas,
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
      onlyCotonomas: Boolean,
      pageIndex: Double
  ) =
    jso(SearchCotos =
      jso(
        query = query,
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        only_cotonomas = onlyCotonomas,
        pagination = jso(page = pageIndex)
      )
    )

  def CotoDetails(id: Id[Coto]) = jso(CotoDetails = jso(id = id.uuid))

  def GraphFromCoto(coto: Id[Coto]) = jso(GraphFromCoto = jso(coto = coto.uuid))

  def GraphFromCotonoma(cotonoma: Id[Cotonoma]) =
    jso(GraphFromCotonoma = jso(cotonoma = cotonoma.uuid))

  def PostCoto(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ) =
    jso(PostCoto =
      jso(
        input = jso(
          content = content,
          summary = summary.getOrElse(null),
          media_content =
            mediaContent.map(js.Tuple2.fromScalaTuple2).getOrElse(null),
          geolocation = location.map(geolocationJson).getOrElse(null),
          datetime_range = timeRange.map(dateTimeRangeJson).getOrElse(null)
        ),
        post_to = postTo.uuid
      )
    )

  def PostCotonoma(
      name: String,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ) =
    jso(PostCotonoma =
      jso(
        input = jso(
          name = name,
          geolocation = location.map(geolocationJson).getOrElse(null),
          datetime_range = timeRange.map(dateTimeRangeJson).getOrElse(null)
        ),
        post_to = postTo.uuid
      )
    )

  def EditCoto(
      id: Id[Coto],
      content: Option[String],
      summary: Option[Option[String]],
      mediaContent: Option[Option[(String, String)]],
      location: Option[Option[Geolocation]],
      timeRange: Option[Option[DateTimeRange]]
  ) =
    jso(EditCoto =
      jso(
        id = id.uuid,
        diff = jso(
          content = content match {
            case Some(content) => fieldDiffJson(Some(Some(content)))
            case None          => fieldDiffJson(None)
          },
          summary =
            fieldDiffJson(summary.map(_.map(s => s))), // String => js.Any
          media_content =
            fieldDiffJson(mediaContent.map(_.map(js.Tuple2.fromScalaTuple2))),
          geolocation = fieldDiffJson(location match {
            case Some(Some(location)) =>
              Some(Some(geolocationJson(location)))
            case Some(None) => Some(None)
            case None       => None
          }),
          datetime_range = fieldDiffJson(timeRange match {
            case Some(Some(timeRange)) =>
              Some(Some(dateTimeRangeJson(timeRange)))
            case Some(None) => Some(None)
            case None       => None
          })
        )
      )
    )

  def Promote(id: Id[Coto]) = jso(Promote = jso(id = id.uuid))

  def DeleteCoto(id: Id[Coto]) = jso(DeleteCoto = jso(id = id.uuid))

  def Repost(id: Id[Coto], dest: Id[Cotonoma]) =
    jso(Repost = jso(id = id.uuid, dest = dest.uuid))

  def RenameCotonoma(id: Id[Cotonoma], name: String) =
    jso(RenameCotonoma = jso(id = id.uuid, name = name))

  def Ito(id: Id[Ito]) = jso(Ito = jso(id = id.uuid))

  def OutgoingItos(cotoId: Id[Coto]) =
    jso(OutgoingItos = jso(coto = cotoId.uuid))

  def CreateIto(
      sourceId: Id[Coto],
      targetId: Id[Coto],
      description: Option[String],
      details: Option[String],
      order: Option[Int]
  ) = jso(CreateIto =
    jso(
      source_coto_id = sourceId.uuid,
      target_coto_id = targetId.uuid,
      description = description.getOrElse(null),
      details = details.getOrElse(null),
      // getOrElse can't be used to convert `order` because Int is non-nullable.
      order = order match {
        case Some(order) => order
        case None        => null
      }
    )
  )

  def EditIto(
      id: Id[Ito],
      description: Option[Option[String]],
      details: Option[Option[String]]
  ) =
    jso(EditIto =
      jso(
        id = id.uuid,
        diff = jso(
          description = fieldDiffJson(
            description.map(_.map(s => s)) // String => js.Any
          ),
          details =
            fieldDiffJson(details.map(_.map(s => s))) // String => js.Any
        )
      )
    )

  def DeleteIto(id: Id[Ito]) = jso(DeleteIto = jso(id = id.uuid))

  def ChangeItoOrder(id: Id[Ito], newOrder: Int) =
    jso(ChangeItoOrder = jso(id = id.uuid, new_order = newOrder))

  private def geolocationJson(location: Geolocation) =
    jso(longitude = location.longitude, latitude = location.latitude)

  private def dateTimeRangeJson(range: DateTimeRange) =
    jso(start = range.startUtcIso, end = range.endUtcIso.getOrElse(null))

  private def fieldDiffJson(value: Option[Option[js.Any]]): js.Any =
    value match {
      case Some(Some(value)) => jso(Change = value)
      case Some(None)        => "Delete"
      case None              => "None"
    }
}

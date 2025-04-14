package cotoami.subparts.modals

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.Context
import cotoami.models.Server
import cotoami.subparts.{field, PartsNode, ViewConnectionStatus}

object SectionAsServer {

  def apply(server: Server)(implicit context: Context): ReactElement =
    section(className := "field-group as-server")(
      h2()(context.i18n.text.AsServer_title),
      fieldUrl(server),
      fieldConnection(server)
    )

  private def fieldUrl(
      server: Server
  )(implicit context: Context): ReactElement =
    field(
      name = context.i18n.text.AsServer_url,
      classes = "server-url"
    )(
      input(
        `type` := "text",
        readOnly := true,
        value := server.server.urlPrefix
      ),
      div(className := "edit")(
        // buttonEdit(_ => ())
      )
    )

  private def fieldConnection(
      server: Server
  )(implicit context: Context): ReactElement =
    field(
      name = context.i18n.text.AsServer_connection,
      classes = "server-connection"
    )(
      PartsNode.detailsConnectionStatus(
        ViewConnectionStatus(server)
      )
    )
}

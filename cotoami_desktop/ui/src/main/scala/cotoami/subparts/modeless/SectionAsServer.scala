package cotoami.subparts.modeless

import marubinotto.react.ReactNode
import marubinotto.react.html._

import cotoami.Context
import cotoami.models.Server
import cotoami.subparts.forms.field
import cotoami.subparts.{PartsNode, ViewConnectionStatus}

object SectionAsServer {

  def apply(server: Server)(using context: Context): ReactNode =
    section(className := "field-group as-server")(
      h2()(context.i18n.text.AsServer_title),
      fieldUrl(server),
      fieldConnection(server)
    )

  private def fieldUrl(
      server: Server
  )(using context: Context): ReactNode =
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
  )(using context: Context): ReactNode =
    field(
      name = context.i18n.text.AsServer_connection,
      classes = "server-connection"
    )(
      PartsNode.detailsConnectionStatus(
        ViewConnectionStatus(server)
      )
    )
}

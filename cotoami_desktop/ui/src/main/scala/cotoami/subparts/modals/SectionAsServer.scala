package cotoami.subparts.modals

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.Context
import cotoami.models.Server
import cotoami.subparts.field

object SectionAsServer {

  def apply(server: Server)(implicit context: Context): ReactElement =
    section(className := "field-group local-server")(
      h2()(context.i18n.text.AsServer_title),
      fieldUrl(server)
    )

  private def fieldUrl(
      server: Server
  )(implicit context: Context): ReactElement =
    field(
      name = context.i18n.text.AsServer_url,
      classes = "server"
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
}

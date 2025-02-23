package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Link}
import cotoami.components.{materialSymbol, ScrollArea}
import cotoami.subparts.{Modal, ViewCoto}

object ModalConnect {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoId: Id[Coto],
      toSelection: Boolean = true,
      error: Option[String] = None
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ConnectMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val coto = context.repo.cotos.get(model.cotoId)
    Modal.view(
      dialogClasses = "connect",
      closeButton = Some((classOf[Modal.Connect], dispatch)),
      error = model.error
    )(
      materialSymbol(Link.ConnectIconName),
      "Connect"
    )(
      section(className := "source")(
        if (model.toSelection)
          coto.map(articleCoto)
        else
          div()()
      ),
      div(className := "link-direction")(
      ),
      section(className := "target")(
        if (model.toSelection)
          div()()
        else
          coto.map(articleCoto)
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "connect"
        )("Connect")
      )
    )
  }

  private def articleCoto(coto: Coto)(implicit
      context: Context
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        ViewCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )
}

package cotoami.subparts.modals

import slinky.web.html._
import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Link}
import cotoami.components.ScrollArea
import cotoami.subparts.{Modal, ViewCoto}

object ModalLinkEditor {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      original: Link
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "link-editor",
      closeButton = Some((classOf[Modal.LinkEditor], dispatch))
    )(
      if (context.domain.isPin(model.original))
        "Pin"
      else
        "Link"
    )(
      section(className := "source-coto")(
        context.domain.cotos.get(model.original.sourceCotoId).map(articleCoto)
      ),
      section(className := "link")(
      ),
      section(className := "target-coto")(
        context.domain.cotos.get(model.original.targetCotoId).map(articleCoto)
      )
    )

  private def articleCoto(coto: Coto)(implicit context: Context): ReactElement =
    article(className := "coto embedded")(
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )
}

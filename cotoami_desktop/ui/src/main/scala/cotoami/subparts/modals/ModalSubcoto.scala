package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id}
import cotoami.components.ScrollArea
import cotoami.subparts.{Modal, PartsCoto}

object ModalSubcoto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      sourceCotoId: Id[Coto],
      error: Option[String] = None
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.SubcotoMsg(this).pipe(AppMsg.ModalMsg)
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
    val sourceCoto = context.repo.cotos.get(model.sourceCotoId)
    Modal.view(
      dialogClasses = "subcoto",
      closeButton = Some((classOf[Modal.Subcoto], dispatch)),
      error = model.error
    )(
      "New sub-coto"
    )(
      section(className := "source")(
        sourceCoto.map(articleCoto)
      )
    )
  }

  private def articleCoto(coto: Coto)(implicit
      context: Context
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        PartsCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
      )
    )
}

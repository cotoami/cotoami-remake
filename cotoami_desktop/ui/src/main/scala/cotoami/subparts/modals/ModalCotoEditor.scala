package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.models.{Coto, Id}
import cotoami.subparts.Modal
import cotoami.subparts.Editor._

object ModalCotoEditor {

  case class Model(
      cotoId: Id[Coto],
      form: CotoForm.Model = CotoForm.Model(),
      inPreview: Boolean = false,
      error: Option[String] = None
  )

  object Model {
    def apply(coto: Coto): Model =
      Model(
        coto.id,
        CotoForm.Model(
          summaryInput = coto.summary.getOrElse(""),
          contentInput = coto.content.getOrElse(""),
          mediaContent = coto.mediaContent.map(_._1)
        )
      )
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.CotoEditorMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "coto-editor",
      closeButton = Some((classOf[Modal.CotoEditor], dispatch)),
      error = model.error
    )(
      "Coto"
    )(
      CotoForm.sectionMediaPreview(model.form)(submsg =>
        dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "form")(
        if (model.inPreview)
          CotoForm.sectionPreview(model.form)
        else
          CotoForm.sectionEditor(
            model = model.form,
            onFocus = () => (),
            onCtrlEnter = () => ()
          )(submsg => dispatch(Msg.CotoFormMsg(submsg)))
      )
    )
}

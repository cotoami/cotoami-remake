package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma}
import cotoami.components.materialSymbol
import cotoami.subparts.Modal
import cotoami.subparts.EditorCoto._

object ModalPromote {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      coto: Coto,
      cotonomaForm: CotonomaForm.Model,
      cotoForm: CotoForm.Model,
      error: Option[String] = None
  )

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) =
      coto.toPromote.pipe { coto =>
        val defaultName = coto.summary.getOrElse("")
        val (cotonomaForm, cmd) =
          CotonomaForm.Model.withDefault(defaultName, coto.nodeId)
        val cotoForm = CotoForm.Model.forUpdate(coto)
        (
          Model(coto, cotonomaForm, cotoForm),
          cmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.PromoteMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotonomaFormMsg(submsg: CotonomaForm.Msg) extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) = {
    val default = (model, Cmd.none)
    msg match {
      case Msg.CotonomaFormMsg(submsg) => {
        val (form, subcmd) = CotonomaForm.update(submsg, model.cotonomaForm)
        default.copy(
          _1 = model.copy(cotonomaForm = form),
          _2 = subcmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }

      case Msg.CotoFormMsg(submsg) => {
        val (form, _, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = model.copy(cotoForm = form),
          _2 = subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "promote",
      closeButton = Some((classOf[Modal.Promote], dispatch)),
      error = model.error
    )(
      span(className := "icon")(
        materialSymbol(Coto.IconName),
        materialSymbol("arrow_right_alt"),
        materialSymbol(Cotonoma.IconName)
      ),
      "Promote to Cotonoma"
    )(
      div(className := "cotonoma-form")(
        CotonomaForm.inputName(
          model = model.cotonomaForm,
          onFocus = None,
          onBlur = None,
          onCtrlEnter = () => ()
        )(submsg => dispatch(Msg.CotonomaFormMsg(submsg))),
        Validation.sectionValidationError(model.cotonomaForm.validation)
      )
    )
}

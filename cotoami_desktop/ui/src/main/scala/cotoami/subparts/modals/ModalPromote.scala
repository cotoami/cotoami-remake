package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
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
      error: Option[String] = None
  )

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) =
      coto.toPromote.pipe { coto =>
        val defaultName = coto.summary.getOrElse("")
        val (cotonomaForm, cmd) =
          CotonomaForm.Model.withDefault(defaultName, coto.nodeId)
        (
          Model(
            coto = coto,
            cotonomaForm = cotonomaForm
          ),
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
      materialSymbol(Cotonoma.IconName),
      "Promote to Cotonoma"
    )(
    )
}

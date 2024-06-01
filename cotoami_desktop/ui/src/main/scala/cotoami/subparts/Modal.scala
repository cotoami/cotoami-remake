package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement

import fui.Cmd

object Modal {
  sealed trait Model
  case class WelcomeModel(model: ModalWelcome.Model) extends Model

  object Model {
    def default: Option[Model] = Some(
      WelcomeModel(ModalWelcome.Model())
    )
  }

  sealed trait Msg
  case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    (msg, model) match {
      case (WelcomeMsg(modalMsg), WelcomeModel(modalModel)) =>
        ModalWelcome.update(modalMsg, modalModel)
          .pipe(pair => (WelcomeModel(pair._1), pair._2))

      case (_, _) => (model, Seq.empty)
    }

  def apply(
      model: cotoami.Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    model.modal.map { case WelcomeModel(modalModel) =>
      model.systemInfo.map(info =>
        ModalWelcome(modalModel, info.recent_databases.toSeq, dispatch)
      )
    }
}

package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{log_error, tauri, Msg => AppMsg}
import cotoami.backend.{ErrorJson, Id, InitialDataset, InitialDatasetJson, Node}
import cotoami.repositories.Domain
import cotoami.components.materialSymbol

object ModalOperateAs {

  case class Model(
      current: Node,
      switchingTo: Node,
      switching: Boolean = false,
      switchingError: Option[String] = None
  )

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.OperateAsMsg andThen AppMsg.ModalMsg

    case object Switch extends Msg
    case class Switched(result: Either[ErrorJson, InitialDatasetJson])
        extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      domain: Domain
  ): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.Switch =>
        (
          model.copy(switching = true, switchingError = None),
          Seq(
            switchTo(
              Option.when(!domain.nodes.isLocal(model.switchingTo.id))(
                model.switchingTo.id
              )
            )
          )
        )

      case Msg.Switched(Right(json)) =>
        (
          model.copy(switching = false, switchingError = None),
          Seq(
            Browser.send(AppMsg.SetRemoteInitialDataset(InitialDataset(json))),
            Modal.close(classOf[Modal.OperateAs])
          )
        )

      case Msg.Switched(Left(e)) =>
        (
          model.copy(
            switching = false,
            switchingError = Some(e.default_message)
          ),
          Seq(
            log_error(
              "Couldn't switch the operating node.",
              Some(js.JSON.stringify(e))
            )
          )
        )
    }

  private def switchTo(parentId: Option[Id[Node]]): Cmd[AppMsg] =
    tauri
      .invokeCommand(
        "operate_as",
        js.Dynamic
          .literal(
            parentId = parentId.map(_.uuid).getOrElse(null)
          )
      )
      .map(Msg.toApp(Msg.Switched(_)))

  def apply(
      model: Model,
      dispatch: AppMsg => Unit
  )(implicit domain: Domain): ReactElement = {
    val modalType = classOf[Modal.OperateAs]
    Modal.view(
      elementClasses = "operate-as",
      closeButton = Some((modalType, dispatch)),
      error = model.switchingError
    )(
      "Switch Operating Node"
    )(
      section(className := "preview")(
        p("You are about to switch the operating node as below:"),
        sectionNode(model.current, "current"),
        div(className := "arrow")(materialSymbol("arrow_downward", "arrow")),
        sectionNode(model.switchingTo, "switching-to")
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType).toApp))
        )("Cancel"),
        button(
          `type` := "button",
          aria - "busy" := model.switching.toString(),
          onClick := (e => dispatch(Msg.Switch.toApp))
        )("Switch")
      )
    )
  }

  private def sectionNode(node: Node, elementClasses: String)(implicit
      domain: Domain
  ): ReactElement =
    section(className := elementClasses)(
      spanNode(node),
      Option.when(domain.nodes.isLocal(node.id)) {
        span(className := "is-local")("(local)")
      }
    )
}

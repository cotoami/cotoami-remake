package cotoami.subparts.modeless

import scala.annotation.unused

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.{Browser, Cmd}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.SectionGeomap
import cotoami.subparts.PartsNode

object ModelessGeomap {

  val DialogId = ModelessDialogId.Geomap

  case class Model()

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessGeomapMsg(this)
  }

  object Msg {
    case object Open extends Msg
    case object Focus extends Msg
    case object Close extends Msg
  }

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open  => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close => Some(ModelessDialogOrder.Action.Close)
    }

  def open: Cmd.One[AppMsg] =
    Browser.send(Msg.Open.into)

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Option[Model]): (Option[Model], Cmd[AppMsg]) =
    msg match {
      case Msg.Open  => (Some(Model()), Cmd.none)
      case Msg.Focus => (model, Cmd.none)
      case Msg.Close => (None, Cmd.none)
    }

  def apply(@unused model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    ModelessDialogFrame(
      dialogClasses = Seq("modeless-geomap" -> true),
      title = dialogTitle,
      onClose = () => dispatch(Msg.Close),
      onFocus = () => dispatch(Msg.Focus),
      zIndex = context.modeless.dialogZIndex(DialogId),
      initialWidth = "min(960px, calc(100vw - 32px))",
      initialHeight = "min(680px, calc(100vh - 48px))"
    )(
      SectionGeomap(context.geomap)
    )

  private def dialogTitle(using context: Context): ReactElement =
    context.repo.cotonomas.focused.flatMap(cotonoma =>
      context.repo.nodes.get(cotonoma.nodeId).map(node => (cotonoma, node))
    ) match {
      case Some((cotonoma, node)) =>
        Fragment(
          span(className := "title-icon")(PartsNode.imgNode(node)),
          span(className := "cotonoma-name")(cotonoma.name)
        )
      case None =>
        span(className := "title-icon")(materialSymbol("public"))
    }
}

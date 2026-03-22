package cotoami.subparts.modeless

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.{Browser, Cmd}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.subparts.SectionCotoDetails

object ModelessCoto {

  case class Model(cotoId: Id[Coto], coto: Coto)

  object Model {
    def apply(coto: Coto): Model =
      Model(coto.id, coto)
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessCotoMsg(this)
  }

  object Msg {
    case class Open(coto: Coto) extends Msg
    case class Focus(cotoId: Id[Coto]) extends Msg
    case class Close(cotoId: Id[Coto]) extends Msg
  }

  def dialogId(cotoId: Id[Coto]): ModelessDialogId =
    ModelessDialogId.CotoDetails(cotoId)

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_)    => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus(_)   => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close(_)   => Some(ModelessDialogOrder.Action.Close)
    }

  def open(coto: Coto): Cmd.One[AppMsg] =
    Browser.send(Msg.Open(coto).into)

  def update(
      msg: Msg,
      dialogs: Seq[Model]
  ): (Seq[Model], Option[ModelessDialogId], Cmd[AppMsg]) = {
    val default = (dialogs, Option.empty[ModelessDialogId], Cmd.none)

    msg match {
      case Msg.Open(coto) =>
        val opened = Model(coto)
        val updated =
          dialogs.indexWhere(_.cotoId == coto.id) match {
            case -1    => dialogs :+ opened
            case index => dialogs.updated(index, opened)
          }
        (updated, Some(dialogId(coto.id)), Cmd.none)

      case Msg.Focus(cotoId) =>
        default.copy(_2 = Some(dialogId(cotoId)))

      case Msg.Close(cotoId) =>
        (
          dialogs.filterNot(_.cotoId == cotoId),
          Some(dialogId(cotoId)),
          Cmd.none
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val coto = context.repo.cotos.get(model.cotoId).getOrElse(model.coto)
    val id = dialogId(model.cotoId)
    ModelessDialogFrame(
      dialogClasses = Seq("modeless-coto" -> true),
      title = dialogTitle(coto),
      onClose = () => dispatch(Msg.Close(model.cotoId)),
      onFocus = () => dispatch(Msg.Focus(model.cotoId)),
      zIndex = context.modeless.dialogZIndex(id),
      initialWidth =
        "min(calc(var(--max-article-width) + (var(--block-spacing-horizontal) * 2)), calc(100vw - 32px))"
    )(
      SectionCotoDetails(coto)
    )
  }

  private def dialogTitle(coto: Coto)(using context: Context): ReactElement =
    if (coto.isCotonoma)
      if (context.repo.isNodeRoot(coto.id))
        Fragment(
          span(className := "title-icon")(materialSymbol(Node.IconName)),
          context.i18n.text.Node_root
        )
      else
        Fragment(
          span(className := "title-icon")(materialSymbol(Cotonoma.IconName)),
          context.i18n.text.Cotonoma
        )
    else
      Fragment(
        span(className := "title-icon")(materialSymbol(Coto.IconName)),
        context.i18n.text.Coto
      )
}

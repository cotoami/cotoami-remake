package cotoami.subparts.modeless

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.{Browser, Cmd}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.subparts.SectionCotoDetails

object ModelessCoto {

  case class Model(instanceId: String, cotoId: Id[Coto], coto: Coto)

  object Model {
    def apply(coto: Coto): Model =
      Model(coto.id.uuid, coto.id, coto)
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessCotoMsg(this)
  }

  object Msg {
    case class Open(coto: Coto) extends Msg
    case class Show(instanceId: String, coto: Coto) extends Msg
    case class Focus(instanceId: String) extends Msg
    case class Close(instanceId: String) extends Msg
  }

  def dialogId(instanceId: String): ModelessDialogId =
    ModelessDialogId.CotoDetails(instanceId)

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_)    => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Show(_, _) => Some(ModelessDialogOrder.Action.Focus)
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
        dialogs.find(_.cotoId == coto.id) match {
          case Some(existing) =>
            (dialogs, Some(dialogId(existing.instanceId)), Cmd.none)
          case None =>
            val opened = Model(coto)
            (dialogs :+ opened, Some(dialogId(opened.instanceId)), Cmd.none)
        }

      case Msg.Show(instanceId, coto) =>
        (
          dialogs.map { model =>
            if (model.instanceId == instanceId)
              model.copy(cotoId = coto.id, coto = coto)
            else
              model
          },
          Some(dialogId(instanceId)),
          Cmd.none
        )

      case Msg.Focus(instanceId) =>
        default.copy(_2 = Some(dialogId(instanceId)))

      case Msg.Close(instanceId) =>
        (
          dialogs.filterNot(_.instanceId == instanceId),
          Some(dialogId(instanceId)),
          Cmd.none
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val coto = context.repo.cotos.get(model.cotoId).getOrElse(model.coto)
    val id = dialogId(model.instanceId)
    ModelessDialogFrame(
      dialogClasses = Seq("modeless-coto" -> true),
      title = dialogTitle(coto),
      onClose = () => dispatch(Msg.Close(model.instanceId)),
      onFocus = () => dispatch(Msg.Focus(model.instanceId)),
      zIndex = context.modeless.dialogZIndex(id),
      initialWidth =
        "min(calc(var(--max-article-width) + (var(--block-spacing-horizontal) * 2)), calc(100vw - 32px))",
      initialHeight = "auto"
    )(
      SectionCotoDetails(
        coto,
        onNavigate = cotoId =>
          context.repo.cotos.get(cotoId)
            .map(coto => Msg.Show(model.instanceId, coto))
            .getOrElse(AppMsg.FocusCoto(cotoId))
      )
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

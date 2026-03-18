package cotoami.subparts.modeless

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma}
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModelessNewCoto {

  val DialogId = ModelessDialogId.NewCoto

  case class Model(
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToPost: Boolean =
      !posting && cotoForm.hasValidContents

    def post(postTo: Cotonoma): (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        CotoBackend.post(cotoForm.toBackendInput, postTo.id)
          .map(Msg.Posted(_).into)
      )
  }

  object Model {
    def apply(cotoForm: CotoForm.Model): (Model, Cmd[AppMsg]) =
      (
        Model(cotoForm = cotoForm),
        cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg.apply).map(_.into)
      )
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessNewCotoMsg(this)
  }

  object Msg {
    case class Open(cotoForm: CotoForm.Model) extends Msg
    case object Focus extends Msg
    case object Close extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case object Post extends Msg
    case class Posted(result: Either[ErrorJson, Coto]) extends Msg
  }

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_) => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus   => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close   => Some(ModelessDialogOrder.Action.Close)
      case _           => None
    }

  def open(cotoForm: CotoForm.Model): Cmd.One[AppMsg] =
    Browser.send(Msg.Open(cotoForm).into)

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Option[Model])(using
      context: Context
  ): (Option[Model], Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)

    (msg, model) match {
      case (Msg.Open(cotoForm), _) =>
        Model(cotoForm).pipe { case (opened, cmd) =>
          (Some(opened), context.geomap, cmd)
        }

      case (Msg.Focus, _) =>
        default

      case (Msg.Close, _) =>
        default.copy(_1 = None)

      case (_, None) =>
        default

      case (Msg.CotoFormMsg(submsg), Some(current)) =>
        val (form, geomap, subcmd) = CotoForm.update(submsg, current.cotoForm)
        (
          Some(current.copy(cotoForm = form)),
          geomap,
          subcmd.map(Msg.CotoFormMsg.apply).map(_.into)
        )

      case (Msg.Post, Some(current)) =>
        context.repo.currentCotonoma match {
          case Some(cotonoma) =>
            current.post(cotonoma).pipe { case (updated, cmd) =>
              default.copy(_1 = Some(updated), _3 = cmd)
            }
          case None => default
        }

      case (Msg.Posted(Right(_)), Some(current)) =>
        (
          Some(current.copy(posting = false)),
          context.geomap,
          close
        )

      case (Msg.Posted(Left(e)), Some(current)) =>
        default.copy(
          _1 =
            Some(current.copy(posting = false, error = Some(e.default_message)))
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    ModelessDialogFrame(
      dialogClasses = Seq(
        "modeless-new-coto" -> true,
        "with-media-content" -> model.cotoForm.mediaBlob.isDefined
      ),
      title = Fragment(
        span(className := "title-icon")(materialSymbol(Coto.IconName)),
        context.i18n.text.ModelessNewCoto_title
      ),
      onClose = () => dispatch(Msg.Close),
      onFocus = () => dispatch(Msg.Focus),
      zIndex = context.modelessDialogZIndex(DialogId),
      error = model.error
    )(
      context.repo.currentCotonoma.map(sectionPostTo),
      CotoForm(
        form = model.cotoForm,
        onCtrlEnter = Some(() => dispatch(Msg.Post))
      )(using
        context,
        (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "buttons")(
        CotoForm.buttonPreview(model.cotoForm)(using
          context,
          (submsg: CotoForm.Msg) => dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "post",
          disabled := !model.readyToPost,
          aria - "busy" := model.posting.toString(),
          onClick := (_ => dispatch(Msg.Post))
        )(
          context.i18n.text.Post,
          span(className := "shortcut-help")("(Ctrl + Enter)")
        )
      )
    )

  private def sectionPostTo(cotonoma: Cotonoma)(using
      context: Context
  ): ReactElement =
    section(className := "post-to")(
      span(className := "label")(s"${context.i18n.text.PostTo}:"),
      span(className := "name")(cotonoma.name)
    )
}

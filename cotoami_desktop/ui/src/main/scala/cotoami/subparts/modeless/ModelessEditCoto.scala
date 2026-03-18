package cotoami.subparts.modeless

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.Validation
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Node}
import cotoami.repository.Cotonomas
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModelessEditCoto {

  val DialogId = ModelessDialogId.EditCoto

  case class Model(
      original: Coto,
      cotoForm: CotoForm.Model,
      cotonomaForm: CotonomaForm.Model,
      mediaContentChanged: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    lazy val edited: Boolean =
      cotoFormEdited || (original.isCotonoma && cotonomaForm.edited)

    lazy val cotoFormEdited: Boolean =
      diffSummary.isDefined ||
        diffContent.isDefined ||
        diffMediaContent.isDefined ||
        diffDateTimeRange.isDefined ||
        diffGeolocation.isDefined

    def diffSummary: Option[Option[String]] =
      Option.when(cotoForm.summary != original.summary)(cotoForm.summary)

    def diffContent: Option[String] =
      Option.when(cotoForm.content != original.content.getOrElse(""))(
        cotoForm.content
      )

    def diffMediaContent: Option[Option[(String, String)]] =
      Option.when(mediaContentChanged)(cotoForm.mediaBase64)

    def diffDateTimeRange: Option[Option[DateTimeRange]] =
      Option.when(cotoForm.dateTimeRange != original.dateTimeRange)(
        cotoForm.dateTimeRange
      )

    def diffGeolocation: Option[Option[Geolocation]] =
      Option.when(cotoForm.geolocation != original.geolocation)(
        cotoForm.geolocation
      )

    lazy val readyToSave: Boolean =
      edited && !saving && cotoForm.hasValidContents &&
        (!original.isCotonoma || cotonomaForm.hasValidContents)

    def save(cotonomas: Cotonomas): (Model, Cmd.One[AppMsg]) =
      (
        copy(saving = true),
        if (original.isCotonoma) saveCotonoma(cotonomas) else saveCoto
      )

    private def saveCoto: Cmd.One[AppMsg] =
      if (cotoFormEdited)
        CotoBackend.edit(
          original.id,
          diffContent,
          diffSummary,
          diffMediaContent,
          diffGeolocation,
          diffDateTimeRange
        ).map(Msg.Saved(_).into)
      else
        Browser.send(Msg.Saved(Right(original)).into)

    private def saveCotonoma(cotonomas: Cotonomas): Cmd.One[AppMsg] =
      (cotonomaForm.edited, cotonomas.asCotonoma(original)) match {
        case (true, Some(cotonoma)) =>
          CotonomaBackend.rename(cotonoma.id, cotonomaForm.name)
            .flatMap(_ => saveCoto)
        case _ => saveCoto
      }
  }

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) = {
      val cotoForm = CotoForm.Model.forUpdate(coto)
      val cotonomaForm =
        CotonomaForm.Model.forUpdate(coto.nameAsCotonoma.getOrElse(""))
      (
        Model(coto, cotoForm, cotonomaForm),
        cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg.apply).map(_.into)
      )
    }
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessEditCotoMsg(this)
  }

  object Msg {
    case class Open(coto: Coto) extends Msg
    case object Focus extends Msg
    case object Close extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case class CotonomaFormMsg(submsg: CotonomaForm.Msg) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, Coto]) extends Msg
  }

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_) => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus   => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close   => Some(ModelessDialogOrder.Action.Close)
      case _           => None
    }

  def open(coto: Coto): Cmd.One[AppMsg] =
    Browser.send(Msg.Open(coto).into)

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Option[Model])(using
      context: Context
  ): (Option[Model], Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)

    (msg, model) match {
      case (Msg.Open(coto), _) =>
        Model(coto).pipe { case (opened, cmd) =>
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
          Some(
            current.copy(
              cotoForm = form,
              mediaContentChanged = submsg match {
                case CotoForm.Msg.FileInput(_)       => true
                case CotoForm.Msg.DeleteMediaContent => true
                case _                               => current.mediaContentChanged
              }
            )
          ),
          geomap,
          subcmd.map(Msg.CotoFormMsg.apply).map(_.into)
        )

      case (Msg.CotonomaFormMsg(submsg), Some(current)) =>
        val (form, subcmd) = CotonomaForm.update(submsg, current.cotonomaForm)
        (
          Some(current.copy(cotonomaForm = form)),
          context.geomap,
          subcmd.map(Msg.CotonomaFormMsg.apply).map(_.into)
        )

      case (Msg.Save, Some(current)) =>
        current.save(context.repo.cotonomas).pipe { case (updated, cmd) =>
          default.copy(_1 = Some(updated), _3 = cmd)
        }

      case (Msg.Saved(Right(_)), Some(current)) =>
        (Some(current.copy(saving = false)), context.geomap, close)

      case (Msg.Saved(Left(e)), Some(current)) =>
        default.copy(
          _1 = Some(current.copy(saving = false, error = Some(e.default_message)))
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    ModelessDialogFrame(
      dialogClasses = Seq(
        "modeless-edit-coto" -> true,
        "with-media-content" -> model.cotoForm.mediaBlob.isDefined
      ),
      title = dialogTitle(model.original),
      onClose = () => dispatch(Msg.Close),
      onFocus = () => dispatch(Msg.Focus),
      zIndex = context.asInstanceOf[cotoami.Model].modelessDialogZIndex(DialogId),
      initialWidth =
        "min(calc(var(--max-article-width) + (var(--block-spacing-horizontal) * 2)), calc(100vw - 32px))",
      error = model.error
    )(
      Option.when(model.original.isCotonoma) {
        div(className := "cotonoma-form")(
          CotonomaForm.inputName(
            model = model.cotonomaForm,
            onCtrlEnter = Some(() => dispatch(Msg.Save))
          )(using
            context,
            (submsg: CotonomaForm.Msg) => dispatch(Msg.CotonomaFormMsg(submsg))
          ),
          Validation.sectionValidationError(model.cotonomaForm.validation)
        )
      },
      CotoForm(
        form = model.cotoForm,
        onCtrlEnter = Some(() => dispatch(Msg.Save))
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
          className := "save",
          disabled := !model.readyToSave,
          aria - "busy" := model.saving.toString(),
          onClick := (_ => dispatch(Msg.Save))
        )(
          context.i18n.text.Save,
          span(className := "shortcut-help")("(Ctrl + Enter)")
        )
      )
    )

  private def dialogTitle(original: Coto)(using context: Context): ReactElement =
    if (original.isCotonoma)
      if (context.repo.isNodeRoot(original.id))
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

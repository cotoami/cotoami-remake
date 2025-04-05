package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.Validation
import marubinotto.components.optionalClasses

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Node}
import cotoami.repository.Cotonomas
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.subparts.Modal
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalEditCoto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

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
      Option.when(cotoForm.summary != original.summary) {
        cotoForm.summary
      }

    def diffContent: Option[String] =
      Option.when(cotoForm.content != original.content.getOrElse("")) {
        cotoForm.content
      }

    def diffMediaContent: Option[Option[(String, String)]] =
      Option.when(mediaContentChanged) {
        cotoForm.mediaBase64
      }

    def diffDateTimeRange: Option[Option[DateTimeRange]] =
      Option.when(cotoForm.dateTimeRange != original.dateTimeRange) {
        cotoForm.dateTimeRange
      }

    def diffGeolocation: Option[Option[Geolocation]] =
      Option.when(cotoForm.geolocation != original.geolocation) {
        cotoForm.geolocation
      }

    lazy val readyToSave: Boolean =
      edited && !saving && cotoForm.hasValidContents &&
        (!original.isCotonoma || cotonomaForm.hasValidContents)

    def save(cotonomas: Cotonomas): (Model, Cmd.One[AppMsg]) =
      (
        copy(saving = true),
        if (original.isCotonoma)
          saveCotonoma(cotonomas)
        else
          saveCoto
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

    private def saveCotonoma(
        cotonomas: Cotonomas
    ): Cmd.One[AppMsg] =
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
        CotonomaForm.Model.forUpdate(
          coto.nameAsCotonoma.getOrElse("")
        )
      (
        Model(coto, cotoForm, cotonomaForm),
        cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg).map(_.into)
      )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.EditCotoMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case class CotonomaFormMsg(submsg: CotonomaForm.Msg) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, Coto]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
      case Msg.CotoFormMsg(submsg) => {
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = model.copy(
            cotoForm = form,
            mediaContentChanged = submsg match {
              case CotoForm.Msg.FileInput(_)       => true
              case CotoForm.Msg.DeleteMediaContent => true
              case _                               => model.mediaContentChanged
            }
          ),
          _2 = geomap,
          _3 = subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }

      case Msg.CotonomaFormMsg(submsg) => {
        val (form, subcmd) = CotonomaForm.update(submsg, model.cotonomaForm)
        default.copy(
          _1 = model.copy(cotonomaForm = form),
          _3 = subcmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }

      case Msg.Save =>
        model.save(context.repo.cotonomas).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Saved(Right(_)) =>
        default.copy(
          _1 = model.copy(saving = false),
          _3 = Modal.close(classOf[Modal.EditCoto])
        )

      case Msg.Saved(Left(e)) =>
        default.copy(
          _1 = model.copy(saving = false, error = Some(e.default_message))
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = optionalClasses(
        Seq(
          ("edit-coto", true),
          ("with-media-content", model.cotoForm.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.EditCoto], dispatch)),
      error = model.error
    )(
      if (model.original.isCotonoma)
        if (context.repo.isNodeRoot(model.original.id))
          Fragment(
            Modal.spanTitleIcon(Node.IconName),
            context.i18n.text.NodeRoot
          )
        else
          Fragment(
            Modal.spanTitleIcon(Cotonoma.IconName),
            context.i18n.text.Cotonoma
          )
      else
        Fragment(
          Modal.spanTitleIcon(Coto.IconName),
          context.i18n.text.Coto
        )
    )(
      Option.when(model.original.isCotonoma) {
        div(className := "cotonoma-form")(
          CotonomaForm.inputName(
            model = model.cotonomaForm,
            onCtrlEnter = Some(() => dispatch(Msg.Save))
          )(submsg => dispatch(Msg.CotonomaFormMsg(submsg))),
          Validation.sectionValidationError(model.cotonomaForm.validation)
        )
      },
      CotoForm(
        form = model.cotoForm,
        onCtrlEnter = Some(() => dispatch(Msg.Save))
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      div(className := "buttons")(
        CotoForm.buttonPreview(model.cotoForm)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
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
}

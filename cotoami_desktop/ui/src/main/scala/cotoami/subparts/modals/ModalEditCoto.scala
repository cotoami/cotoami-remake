package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma, DateTimeRange, Geolocation, Node}
import cotoami.repository.Cotonomas
import cotoami.backend.{CotoBackend, CotonomaBackend, ErrorJson}
import cotoami.components.optionalClasses
import cotoami.subparts.{Modal, SectionGeomap}
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
    def edited(geomap: Geomap): Boolean =
      cotoFormEdited(geomap) ||
        (original.isCotonoma && cotonomaForm.edited)

    def cotoFormEdited(geomap: Geomap): Boolean =
      diffSummary.isDefined ||
        diffContent.isDefined ||
        diffMediaContent.isDefined ||
        diffGeolocation(geomap).isDefined ||
        diffDateTimeRange.isDefined

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

    def diffGeolocation(geomap: Geomap): Option[Option[Geolocation]] =
      Option.when(geomap.focusedLocation != original.geolocation) {
        geomap.focusedLocation
      }

    def diffDateTimeRange: Option[Option[DateTimeRange]] =
      Option.when(cotoForm.dateTimeRange != original.dateTimeRange) {
        cotoForm.dateTimeRange
      }

    def readyToSave(geomap: Geomap): Boolean =
      edited(geomap) && !saving && cotoForm.hasValidContents &&
        (!original.isCotonoma || cotonomaForm.hasValidContents)

    def save(geomap: Geomap, cotonomas: Cotonomas): (Model, Cmd.One[AppMsg]) =
      (
        copy(saving = true),
        if (original.isCotonoma)
          saveCotonoma(cotonomas, geomap)
        else
          saveCoto(geomap)
      )

    private def saveCoto(geomap: Geomap): Cmd.One[AppMsg] =
      if (cotoFormEdited(geomap))
        CotoBackend.edit(
          original.id,
          diffContent,
          diffSummary,
          diffMediaContent,
          diffGeolocation(geomap),
          diffDateTimeRange
        ).map(Msg.Saved(_).into)
      else
        Browser.send(Msg.Saved(Right(original)).into)

    private def saveCotonoma(
        cotonomas: Cotonomas,
        geomap: Geomap
    ): Cmd.One[AppMsg] =
      (cotonomaForm.edited, cotonomas.asCotonoma(original)) match {
        case (true, Some(cotonoma)) =>
          CotonomaBackend.rename(cotonoma.id, cotonomaForm.name)
            .flatMap(_ => saveCoto(geomap))
        case _ => saveCoto(geomap)
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
        Browser.send(
          SectionGeomap.Msg.FocusLocation(coto.geolocation).into
        ) +: cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg).map(_.into)
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
        model.save(context.geomap, context.repo.cotonomas).pipe {
          case (model, cmd) => default.copy(_1 = model, _3 = cmd)
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
            "Node Root"
          )
        else
          Fragment(
            Modal.spanTitleIcon(Cotonoma.IconName),
            "Cotonoma"
          )
      else
        Fragment(
          Modal.spanTitleIcon(Coto.IconName),
          "Coto"
        )
    )(
      Option.when(model.original.isCotonoma) {
        div(className := "cotonoma-form")(
          CotonomaForm.inputName(
            model = model.cotonomaForm,
            onFocus = None,
            onBlur = None,
            onCtrlEnter = () => ()
          )(submsg => dispatch(Msg.CotonomaFormMsg(submsg))),
          Validation.sectionValidationError(model.cotonomaForm.validation)
        )
      },
      CotoForm(
        model = model.cotoForm,
        onCtrlEnter = Some(() => dispatch(Msg.Save))
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      div(className := "buttons")(
        CotoForm.buttonPreview(model = model.cotoForm)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "save",
          disabled := !model.readyToSave(context.geomap),
          aria - "busy" := model.saving.toString(),
          onClick := (_ => dispatch(Msg.Save))
        )("Save", span(className := "shortcut-help")("(Ctrl + Enter)"))
      )
    )
}

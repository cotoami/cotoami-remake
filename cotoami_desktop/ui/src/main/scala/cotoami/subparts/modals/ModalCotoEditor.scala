package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, DateTimeRange, Geolocation}
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.components.{optionalClasses, SplitPane}
import cotoami.subparts.{Modal, SectionGeomap}
import cotoami.subparts.Editor._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalCotoEditor {

  case class Model(
      original: Coto,
      cotoForm: CotoForm.Model,
      mediaContentChanged: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    def edited(geomap: Geomap): Boolean =
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
      edited(geomap) && !saving && cotoForm.readyToPost

    def save(geomap: Geomap): (Model, Cmd.One[AppMsg]) =
      (
        copy(saving = true),
        CotoBackend.edit(
          original.id,
          diffContent,
          diffSummary,
          diffMediaContent,
          diffGeolocation(geomap),
          diffDateTimeRange
        ).map(Msg.Saved(_).into)
      )
  }

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) =
      CotoForm.Model(
        isCotonoma = coto.isCotonoma,
        summaryInput = coto.summary.getOrElse(""),
        contentInput = coto.content.getOrElse(""),
        mediaBlob = coto.mediaBlob.map(_._1),
        // The content of `mediaBase64` will be used only if `mediaContentChanged` is true,
        // so let's set dummy data here to avoid the cost of base64-encoding `mediaBlob`
        // and just to denote that the coto has some media content
        // (cf. `CotoForm.Model.hasContents`).
        mediaBase64 = coto.mediaBlob.map { case (_, t) => ("", t) },
        dateTimeRange = coto.dateTimeRange
      ).pipe { form =>
        (
          Model(coto, form),
          Browser.send(
            SectionGeomap.Msg.FocusLocation(coto.geolocation).into
          ) +: form.scanMediaMetadata.map(Msg.CotoFormMsg).map(_.into)
        )
      }
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.CotoEditorMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
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

      case Msg.Save =>
        model.save(context.geomap).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Saved(Right(coto)) =>
        default.copy(
          _1 = model.copy(saving = false),
          _3 = Modal.close(classOf[Modal.CotoEditor])
        )

      case Msg.Saved(Left(e)) =>
        default.copy(
          _1 = model.copy(saving = false, error = Some(e.default_message))
        )
    }
  }

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = optionalClasses(
        Seq(
          ("coto-editor", true),
          ("with-media-content", model.cotoForm.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.CotoEditor], dispatch)),
      error = model.error
    )(
      if (model.original.isCotonoma)
        "Cotonoma"
      else
        "Coto"
    )(
      divForm(model).pipe { divForm =>
        CotoForm.sectionMediaPreview(model.cotoForm)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
        ) match {
          case Some(mediaPreview) =>
            SplitPane(
              vertical = false,
              initialPrimarySize = 300,
              className = Some("form-with-media"),
              primary = SplitPane.Primary.Props()(mediaPreview),
              secondary = SplitPane.Secondary.Props()(divForm)
            )

          case None => divForm
        }
      },
      ulAttributes(
        model.cotoForm.dateTimeRange,
        model.cotoForm.mediaDateTime,
        context.geomap.focusedLocation,
        model.cotoForm.mediaLocation
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      CotoForm.sectionValidationError(model.cotoForm),
      div(className := "buttons")(
        CotoForm.buttonPreview(model = model.cotoForm)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "save",
          `type` := "submit",
          disabled := !model.readyToSave(context.geomap),
          aria - "busy" := model.saving.toString(),
          onClick := (_ => dispatch(Msg.Save))
        )("Save", span(className := "shortcut-help")("(Ctrl + Enter)"))
      )
    )

  private def divForm(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "form")(
      CotoForm.sectionEditorOrPreview(
        model = model.cotoForm,
        onCtrlEnter = () => dispatch(Msg.Save)
      )(submsg => dispatch(Msg.CotoFormMsg(submsg)))
    )
}

package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Coto
import cotoami.components.{optionalClasses, SplitPane}
import cotoami.subparts.{Modal, SectionGeomap}
import cotoami.subparts.Editor._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalCotoEditor {

  case class Model(
      original: Coto,
      form: CotoForm.Model = CotoForm.Model(),
      mediaContentChanged: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    def edited(geomap: Geomap): Boolean =
      form.summary != original.summary ||
        Some(form.content) != original.content ||
        mediaContentChanged ||
        geomap.focusedLocation != original.geolocation ||
        form.dateTimeRange != original.dateTimeRange

    def readyToSave(geomap: Geomap): Boolean =
      edited(geomap) && !saving && form.readyToPost
  }

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) =
      Model(
        coto,
        CotoForm.Model(
          summaryInput = coto.summary.getOrElse(""),
          contentInput = coto.content.getOrElse(""),
          mediaBlob = coto.mediaContent.map(_._1),
          dateTimeRange = coto.dateTimeRange
        )
      ).pipe { model =>
        (
          model,
          Browser.send(
            SectionGeomap.Msg.FocusLocation(coto.geolocation).into
          ) +: model.form.scanMediaMetadata.map(Msg.CotoFormMsg).map(_.into)
        )
      }
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.CotoEditorMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
      case Msg.CotoFormMsg(submsg) => {
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.form)
        default.copy(
          _1 = model.copy(
            form = form,
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
          ("with-media-content", model.form.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.CotoEditor], dispatch)),
      error = model.error
    )(
      "Coto"
    )(
      divForm(model).pipe { divForm =>
        CotoForm.sectionMediaPreview(model.form)(submsg =>
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
        model.form.dateTimeRange,
        model.form.mediaDateTime,
        context.geomap.focusedLocation,
        model.form.mediaLocation
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      CotoForm.sectionValidationError(model.form),
      div(className := "buttons")(
        CotoForm.buttonPreview(model = model.form)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "save",
          `type` := "submit",
          disabled := !model.readyToSave(context.geomap),
          aria - "busy" := model.saving.toString()
        )("Save", span(className := "shortcut-help")("(Ctrl + Enter)"))
      )
    )

  private def divForm(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "form")(
      CotoForm.sectionEditorOrPreview(
        model = model.form,
        onCtrlEnter = () => ()
      )(submsg => dispatch(Msg.CotoFormMsg(submsg)))
    )
}

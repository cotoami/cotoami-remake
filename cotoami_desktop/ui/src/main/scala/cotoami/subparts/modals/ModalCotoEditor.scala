package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id}
import cotoami.components.optionalClasses
import cotoami.subparts.{Modal, SectionGeomap}
import cotoami.subparts.Editor._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModalCotoEditor {

  case class Model(
      cotoId: Id[Coto],
      form: CotoForm.Model = CotoForm.Model(),
      inPreview: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToSave: Boolean = !saving && form.readyToPost
  }

  object Model {
    def apply(coto: Coto): (Model, Cmd[AppMsg]) =
      (
        Model(
          coto.id,
          CotoForm.Model(
            summaryInput = coto.summary.getOrElse(""),
            contentInput = coto.content.getOrElse(""),
            mediaContent = coto.mediaContent.map(_._1),
            dateTimeRange = coto.dateTimeRange
          )
        ),
        Browser.send(SectionGeomap.Msg.FocusLocation(coto.geolocation).into)
      )
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.CotoEditorMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case object TogglePreview extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Geomap, Cmd[AppMsg]) = {
    val default = (model, context.geomap, Cmd.none)
    msg match {
      case Msg.CotoFormMsg(submsg) => {
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.form)
        default.copy(
          _1 = model.copy(form = form),
          _2 = geomap,
          _3 = subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }

      case Msg.TogglePreview =>
        default.copy(_1 = model.modify(_.inPreview).using(!_))
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
          ("with-media-content", model.form.mediaContent.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.CotoEditor], dispatch)),
      error = model.error
    )(
      "Coto"
    )(
      CotoForm.sectionMediaPreview(model.form)(submsg =>
        dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "form")(
        if (model.inPreview)
          CotoForm.sectionPreview(model.form)
        else
          CotoForm.sectionEditor(
            model = model.form,
            onFocus = () => (),
            onCtrlEnter = () => ()
          )(submsg => dispatch(Msg.CotoFormMsg(submsg)))
      ),
      ulAttributes(
        model.form.dateTimeRange,
        None,
        context.geomap.focusedLocation,
        None
      )(context, submsg => dispatch(Msg.CotoFormMsg(submsg))),
      div(className := "buttons")(
        button(
          className := "preview contrast outline",
          disabled := !model.form.validate.validated,
          onClick := (_ => dispatch(Msg.TogglePreview))
        )(
          if (model.inPreview)
            "Edit"
          else
            "Preview"
        ),
        button(
          className := "save",
          `type` := "submit",
          disabled := !model.readyToSave,
          aria - "busy" := model.saving.toString()
        )("Save", span(className := "shortcut-help")("(Ctrl + Enter)"))
      )
    )
}

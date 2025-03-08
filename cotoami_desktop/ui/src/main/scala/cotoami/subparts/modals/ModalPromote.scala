package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Cotonoma}
import cotoami.components.{materialSymbol, optionalClasses, SplitPane}
import cotoami.subparts.Modal
import cotoami.subparts.EditorCoto._

object ModalPromote {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      original: Coto,
      cotonomaForm: CotonomaForm.Model,
      cotoForm: CotoForm.Model,
      promoting: Boolean = false,
      error: Option[String] = None
  ) {
    def diffSummary: Option[Option[String]] =
      Option.when(cotoForm.summary != original.summary) {
        cotoForm.summary
      }

    def diffContent: Option[String] =
      Option.when(cotoForm.content != original.content.getOrElse("")) {
        cotoForm.content
      }

    def readyToPromote: Boolean =
      !promoting && cotonomaForm.hasValidContents && !cotoForm.validate.failed
  }

  object Model {
    def apply(original: Coto): (Model, Cmd[AppMsg]) =
      original.toPromote.pipe { coto =>
        val defaultName = coto.summary.getOrElse("")
        val (cotonomaForm, cmd) =
          CotonomaForm.Model.withDefault(defaultName, coto.nodeId)
        val cotoForm = CotoForm.Model.forUpdate(coto)
        (
          Model(original, cotonomaForm, cotoForm),
          cmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.PromoteMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class CotonomaFormMsg(submsg: CotonomaForm.Msg) extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) = {
    val default = (model, Cmd.none)
    msg match {
      case Msg.CotonomaFormMsg(submsg) => {
        val (form, subcmd) = CotonomaForm.update(submsg, model.cotonomaForm)
        default.copy(
          _1 = model.copy(cotonomaForm = form),
          _2 = subcmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }

      case Msg.CotoFormMsg(submsg) => {
        val (form, _, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = model.copy(cotoForm = form),
          _2 = subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = optionalClasses(
        Seq(
          ("promote", true),
          ("with-media-content", model.cotoForm.mediaBlob.isDefined)
        )
      ),
      closeButton = Some((classOf[Modal.Promote], dispatch)),
      error = model.error
    )(
      span(className := "title-icon")(
        materialSymbol(Coto.IconName),
        materialSymbol("arrow_right_alt"),
        materialSymbol(Cotonoma.IconName)
      ),
      "Promote to Cotonoma"
    )(
      div(className := "cotonoma-form")(
        CotonomaForm.inputName(
          model = model.cotonomaForm,
          onFocus = None,
          onBlur = None,
          onCtrlEnter = () => ()
        )(submsg => dispatch(Msg.CotonomaFormMsg(submsg))),
        Validation.sectionValidationError(model.cotonomaForm.validation)
      ),
      divCotoForm(model.cotoForm)(submsg => dispatch(Msg.CotoFormMsg(submsg))),
      div(className := "buttons")(
        CotoForm.buttonPreview(model.cotoForm)(submsg =>
          dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "promote",
          disabled := !model.readyToPromote,
          aria - "busy" := model.promoting.toString()
        )("Promote")
      )
    )

  private def divCotoForm(form: CotoForm.Model)(implicit
      dispatch: CotoForm.Msg => Unit
  ): ReactElement = {
    val editor = Fragment(
      CotoForm.sectionEditorOrPreview(form, None, None, false),
      CotoForm.sectionValidationError(form)
    )
    div(className := "coto-form")(
      CotoForm.sectionMediaPreview(form, false) match {
        case Some(mediaPreview) =>
          SplitPane(
            vertical = false,
            initialPrimarySize = 300,
            primary = SplitPane.Primary.Props()(mediaPreview),
            secondary = SplitPane.Secondary.Props()(editor)
          )
        case None => editor
      }
    )
  }
}

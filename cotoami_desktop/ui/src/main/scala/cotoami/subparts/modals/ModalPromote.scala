package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.fui.Cmd.One.pure
import marubinotto.Validation
import marubinotto.components.{materialSymbol, SplitPane}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma}
import cotoami.backend.{CotoBackend, ErrorJson}
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
    def diffName: Option[Option[String]] =
      // use cotonomaForm as cotonoma-name/coto-summary input
      Option.when(Some(cotonomaForm.name) != original.summary) {
        Some(cotonomaForm.name)
      }

    def diffContent: Option[String] =
      Option.when(cotoForm.content != original.content.getOrElse("")) {
        cotoForm.content
      }

    def readyToPromote: Boolean =
      !promoting && cotonomaForm.hasValidContents && !cotoForm.validate.failed

    def promote: (Model, Cmd.One[AppMsg]) =
      (
        copy(promoting = true),
        updateCoto.flatMap(_ match {
          case Right(coto) => CotoBackend.promote(coto.id)
          case Left(e)     => pure(Left(e))
        }).map(Msg.Promoted(_).into)
      )

    private def updateCoto: Cmd.One[Either[ErrorJson, Coto]] =
      (diffName, diffContent) match {
        case (None, None) => pure(Right(original))
        case (name, content) =>
          CotoBackend.edit(original.id, content, name, None, None, None)
      }
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
    object Promote extends Msg
    case class Promoted(result: Either[ErrorJson, (Cotonoma, Coto)]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.CotonomaFormMsg(submsg) => {
        val (form, subcmd) = CotonomaForm.update(submsg, model.cotonomaForm)
        (
          model.copy(cotonomaForm = form),
          subcmd.map(Msg.CotonomaFormMsg).map(_.into)
        )
      }

      case Msg.CotoFormMsg(submsg) => {
        val (form, _, subcmd) = CotoForm.update(submsg, model.cotoForm)
        (
          model.copy(cotoForm = form),
          subcmd.map(Msg.CotoFormMsg).map(_.into)
        )
      }

      case Msg.Promote => model.promote

      case Msg.Promoted(Right(_)) =>
        (
          model.copy(promoting = false),
          Modal.close(classOf[Modal.Promote])
        )

      case Msg.Promoted(Left(e)) =>
        (
          model.copy(promoting = false, error = Some(e.default_message)),
          Cmd.none
        )
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
      context.i18n.text.ModalPromote_title
    )(
      div(className := "cotonoma-form")(
        CotonomaForm.inputName(model = model.cotonomaForm)(
          context,
          submsg => dispatch(Msg.CotonomaFormMsg(submsg))
        ),
        Validation.sectionValidationError(model.cotonomaForm.validation)
      ),
      divCotoForm(model.cotoForm)(
        context,
        submsg => dispatch(Msg.CotoFormMsg(submsg))
      ),
      div(className := "buttons")(
        CotoForm.buttonPreview(model.cotoForm)(
          context,
          submsg => dispatch(Msg.CotoFormMsg(submsg))
        ),
        button(
          className := "promote",
          disabled := !model.readyToPromote,
          aria - "busy" := model.promoting.toString(),
          onClick := (_ =>
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  context.i18n.text.ModalPromote_confirm,
                  Msg.Promote
                )
              )
            )
          )
        )(context.i18n.text.Promote)
      )
    )

  private def divCotoForm(form: CotoForm.Model)(implicit
      context: Context,
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

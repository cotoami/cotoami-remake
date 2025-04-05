package cotoami.subparts.modals

import scala.util.chaining._

import slinky.web.html._
import slinky.core.facade.{Fragment, ReactElement}

import marubinotto.fui.Cmd
import marubinotto.Validation
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Ito}
import cotoami.backend.{ErrorJson, ItoBackend}
import cotoami.subparts.{Modal, PartsCoto, PartsIto}

object ModalEditIto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      original: Ito,
      descriptionInput: String,
      disconnecting: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    def description: Option[String] =
      Option.when(!descriptionInput.isBlank())(descriptionInput.trim)

    def diffDescription: Option[Option[String]] =
      Option.when(description != original.description) {
        description
      }

    def edited: Boolean = diffDescription.isDefined

    def validate: Validation.Result =
      if (edited) {
        Validation.Result(
          description
            .map(Ito.validateDescription)
            .getOrElse(Seq.empty)
        )
      } else
        Validation.Result.notYetValidated

    def readyToDisconnect: Boolean = !disconnecting && !saving

    def disconnect: (Model, Cmd.One[AppMsg]) =
      (
        copy(disconnecting = true),
        ItoBackend.disconnect(original.id).map(Msg.Disconnected(_).into)
      )

    def readyToSave: Boolean = validate.validated && !disconnecting && !saving

    def save: (Model, Cmd.One[AppMsg]) =
      (
        copy(saving = true),
        ItoBackend.edit(original.id, diffDescription, None)
          .map(Msg.Saved(_).into)
      )
  }

  object Model {
    def apply(original: Ito): Model =
      Model(original, original.description.getOrElse(""))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.EditItoMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class DescriptionInput(description: String) extends Msg
    case class Disconnect(id: Id[Ito]) extends Msg
    case class Disconnected(result: Either[ErrorJson, Id[Ito]]) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, Ito]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.DescriptionInput(description) =>
        (model.copy(descriptionInput = description), Cmd.none)

      case Msg.Disconnect(id) => model.disconnect

      case Msg.Disconnected(Right(_)) =>
        (
          model.copy(disconnecting = false),
          Modal.close(classOf[Modal.EditIto])
        )

      case Msg.Disconnected(Left(e)) =>
        (
          model.copy(disconnecting = false, error = Some(e.default_message)),
          cotoami.error("Couldn't delete an ito.", e)
        )

      case Msg.Save => model.save

      case Msg.Saved(Right(_)) =>
        (
          model.copy(saving = false),
          Modal.close(classOf[Modal.EditIto])
        )

      case Msg.Saved(Left(e)) =>
        (
          model.copy(saving = false, error = Some(e.default_message)),
          cotoami.error("Couldn't save an ito.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "edit-ito",
      closeButton = Some((classOf[Modal.EditIto], dispatch)),
      error = model.error
    )(
      if (context.repo.isPin(model.original))
        Fragment(
          Modal.spanTitleIcon(Ito.PinIconName),
          context.i18n.text.Pin
        )
      else
        Fragment(
          Modal.spanTitleIcon(Ito.IconName),
          context.i18n.text.Ito
        )
    )(
      section(className := "source-coto")(
        context.repo.cotos.get(model.original.sourceCotoId).map(articleCoto)
      ),
      sectionIto(model),
      section(className := "target-coto")(
        context.repo.cotos.get(model.original.targetCotoId).map(articleCoto)
      ),
      div(className := "buttons")(
        button(
          className := "disconnect contrast outline",
          disabled := !model.readyToDisconnect,
          aria - "busy" := model.disconnecting.toString(),
          onClick := (_ =>
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  context.i18n.text.ModalEditIto_confirmDisconnect,
                  Msg.Disconnect(model.original.id)
                )
              )
            )
          )
        )(
          materialSymbol("content_cut"),
          span(className := "label")(context.i18n.text.ModalEditIto_disconnect)
        ),
        button(
          className := "save",
          disabled := !model.readyToSave,
          aria - "busy" := model.saving.toString(),
          onClick := (_ => dispatch(Msg.Save))
        )(context.i18n.text.Save)
      )
    )

  private def sectionIto(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "ito")(
      div(className := "ito-icon")(
        materialSymbol("arrow_downward")
      ),
      PartsIto.inputDescription(
        model.descriptionInput,
        model.validate,
        value => dispatch(Msg.DescriptionInput(value))
      )
    )

  private def articleCoto(coto: Coto)(implicit context: Context): ReactElement =
    article(className := "coto embedded")(
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
      )
    )
}

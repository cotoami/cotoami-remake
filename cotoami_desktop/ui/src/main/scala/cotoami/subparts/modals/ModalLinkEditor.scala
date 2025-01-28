package cotoami.subparts.modals

import scala.util.chaining._

import slinky.web.html._
import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Coto, Id, Link}
import cotoami.backend.{ErrorJson, LinkBackend}
import cotoami.components.{materialSymbol, ScrollArea}
import cotoami.subparts.{Modal, ViewCoto}

object ModalLinkEditor {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      original: Link,
      linkingPhraseInput: String,
      disconnecting: Boolean = false,
      saving: Boolean = false
  ) {
    def linkingPhrase: Option[String] =
      Option.when(!linkingPhraseInput.isBlank())(linkingPhraseInput.trim)

    def validate: Validation.Result =
      if (linkingPhraseInput.isBlank)
        Validation.Result.notYetValidated
      else {
        Validation.Result(
          linkingPhrase
            .map(Link.validateLinkingPhrase)
            .getOrElse(Seq.empty)
        )
      }

    def diffLinkingPhrase: Option[Option[String]] =
      Option.when(linkingPhrase != original.linkingPhrase) {
        linkingPhrase
      }

    def readyToDisconnect: Boolean = !disconnecting && !saving

    def disconnect: (Model, Cmd.One[AppMsg]) =
      (
        copy(disconnecting = true),
        LinkBackend.disconnect(original.id).map(Msg.Disconnected(_).into)
      )

    def readyToSave: Boolean = !disconnecting && !saving && !validate.failed
  }

  object Model {
    def apply(original: Link): Model =
      Model(original, original.linkingPhrase.getOrElse(""))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.LinkEditorMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class LinkingPhraseInput(linkingPhrase: String) extends Msg
    case class Disconnect(id: Id[Link]) extends Msg
    case class Disconnected(result: Either[ErrorJson, Id[Link]]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.LinkingPhraseInput(linkingPhrase) =>
        (model.copy(linkingPhraseInput = linkingPhrase), Cmd.none)

      case Msg.Disconnect(id) => model.disconnect

      case Msg.Disconnected(Right(_)) =>
        (
          model.copy(disconnecting = false),
          Modal.close(classOf[Modal.LinkEditor])
        )

      case Msg.Disconnected(Left(e)) =>
        (
          model.copy(disconnecting = false),
          cotoami.error("Couldn't delete a link.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "link-editor",
      closeButton = Some((classOf[Modal.LinkEditor], dispatch))
    )(
      if (context.domain.isPin(model.original))
        "Pin"
      else
        "Link"
    )(
      section(className := "source-coto")(
        context.domain.cotos.get(model.original.sourceCotoId).map(articleCoto)
      ),
      section(className := "link")(
        div(className := "link-icon")(
          materialSymbol("arrow_downward")
        ),
        div(className := "linking-phrase")(
          input(
            className := "linking-phrase",
            `type` := "text",
            placeholder := "Linking phrase (optional)",
            value := model.linkingPhraseInput,
            Validation.ariaInvalid(model.validate),
            onChange := (e => dispatch(Msg.LinkingPhraseInput(e.target.value)))
          ),
          Validation.sectionValidationError(model.validate)
        )
      ),
      section(className := "target-coto")(
        context.domain.cotos.get(model.original.targetCotoId).map(articleCoto)
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
                  "Are you sure you want to delete this link?",
                  Msg.Disconnect(model.original.id)
                )
              )
            )
          )
        )(
          materialSymbol("content_cut"),
          span(className := "label")("Disconnect")
        ),
        button(
          className := "save",
          disabled := !model.readyToSave,
          aria - "busy" := model.saving.toString()
        )("Save")
      )
    )

  private def articleCoto(coto: Coto)(implicit context: Context): ReactElement =
    article(className := "coto embedded")(
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )
}

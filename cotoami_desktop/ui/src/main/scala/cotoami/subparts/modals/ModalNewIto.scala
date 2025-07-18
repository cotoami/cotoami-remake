package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.fui.Cmd.One.pure
import marubinotto.Validation
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Ito}
import cotoami.repository.{Cotos, Itos, Root}
import cotoami.backend.{ErrorJson, ItoBackend}
import cotoami.subparts.{Modal, PartsCoto, PartsIto}

object ModalNewIto {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoId: Id[Coto],
      toSelection: Boolean = true,
      descriptionInput: String = "",
      clearSelection: Boolean = true,
      connecting: Boolean = false,
      error: Option[String] = None
  ) {
    def description: Option[String] =
      Option.when(!descriptionInput.isBlank())(descriptionInput.trim)

    val validate: Validation.Result =
      description
        .map(Ito.validateDescription)
        .map(Validation.Result(_))
        .getOrElse(Validation.Result.notYetValidated)

    def readyToConnect: Boolean = !validate.failed && !connecting

    def connect(repo: Root): (Model, Cmd.One[AppMsg]) = {
      val acc: Cmd.One[Either[ErrorJson, Seq[Ito]]] = pure(Right(Seq.empty))
      val cmd = repo.cotos.selectedIds
        .foldLeft(acc) { (cmd, selectedId) =>
          cmd.flatMap(_ match {
            case Right(itos) => {
              if (alreadyConnected(selectedId, repo.itos))
                pure(Right(itos))
              else
                createIto(selectedId).map(_.map(itos :+ _))
            }
            case Left(e) => pure(Left(e))
          })
        }
        .map(Msg.Connected(_).into)
      (copy(connecting = true), cmd)
    }

    private def alreadyConnected(selectedId: Id[Coto], itos: Itos): Boolean =
      if (toSelection)
        itos.connected(cotoId, selectedId)
      else
        itos.connected(selectedId, cotoId)

    private def createIto(
        selectedId: Id[Coto]
    ): Cmd.One[Either[ErrorJson, Ito]] =
      ItoBackend.create(
        if (toSelection) cotoId else selectedId,
        if (toSelection) selectedId else cotoId,
        description,
        None,
        None
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NewItoMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Reverse extends Msg
    case class DescriptionInput(description: String) extends Msg
    object ClearSelectionToggled extends Msg
    object Connect extends Msg
    case class Connected(result: Either[ErrorJson, Seq[Ito]]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotos, Cmd[AppMsg]) = {
    val default = (model, context.repo.cotos, Cmd.none)
    msg match {
      case Msg.Reverse =>
        default.copy(_1 = model.modify(_.toSelection).using(!_))

      case Msg.DescriptionInput(description) =>
        default.copy(_1 = model.copy(descriptionInput = description))

      case Msg.ClearSelectionToggled =>
        default.copy(_1 = model.modify(_.clearSelection).using(!_))

      case Msg.Connect =>
        model.connect(context.repo).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Connected(Right(itos)) =>
        default.copy(
          _1 = model.copy(connecting = false),
          _2 =
            if (model.clearSelection)
              context.repo.cotos.clearSelection
            else
              context.repo.cotos,
          _3 = Modal.close(classOf[Modal.NewIto])
        )

      case Msg.Connected(Left(e)) =>
        default.copy(
          _1 = model.copy(connecting = false, error = Some(e.default_message)),
          _3 = cotoami.error("Couldn't create itos.", e)
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val coto = context.repo.cotos.get(model.cotoId)
    Modal.view(
      dialogClasses = "new-ito",
      closeButton = Some((classOf[Modal.NewIto], dispatch)),
      error = model.error
    )(
      Modal.spanTitleIcon(Ito.NewIconName),
      context.i18n.text.ModalNewIto_title
    )(
      div(className := "buttons reverse")(
        button(
          `type` := "button",
          className := "reverse contrast outline",
          onClick := (_ => dispatch(Msg.Reverse))
        )(context.i18n.text.ModalNewIto_reverse)
      ),
      section(className := "source")(
        if (model.toSelection)
          coto.map(articleCoto)
        else
          divSelection
      ),
      sectionIto(model),
      section(className := "target")(
        if (model.toSelection)
          divSelection
        else
          coto.map(articleCoto)
      ),
      div(className := "buttons connect")(
        button(
          `type` := "button",
          className := "connect",
          disabled := !model.readyToConnect,
          aria - "busy" := model.connecting.toString(),
          onClick := (_ => dispatch(Msg.Connect))
        )(context.i18n.text.ModalNewIto_connect),
        label(className := "clear-selection", htmlFor := "clear-selection")(
          input(
            `type` := "checkbox",
            id := "clear-selection",
            checked := model.clearSelection,
            disabled := model.connecting,
            onChange := (_ => dispatch(Msg.ClearSelectionToggled))
          ),
          context.i18n.text.ModalNewIto_clearSelection
        )
      )
    )
  }

  private def sectionIto(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "ito")(
      div(className := "ito-icon")(
        materialSymbol("arrow_downward")
      ),
      Option.when(context.repo.cotos.selectedIds.size == 1) {
        PartsIto.inputDescription(
          model.descriptionInput,
          model.validate,
          value => dispatch(Msg.DescriptionInput(value))
        )
      }
    )

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        PartsCoto.addressRemoteAuthor(coto)
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.divContentPreview(coto)
        )
      )
    )

  private def divSelection(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val count = context.repo.cotos.selectedIds.size
    div(className := "selection")(
      if (count == 1)
        context.repo.cotos.selected.headOption.map(articleCoto)
      else
        button(
          className := "selection default",
          onClick := (_ =>
            dispatch(Modal.Msg.OpenModal(Modal.Selection(false)))
          )
        )(
          s"${context.i18n.text.ModalSelection_title} (${count})"
        )
    )
  }
}

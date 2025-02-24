package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import fui.Cmd.One.pure
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Link}
import cotoami.repository.{Cotos, Links, Root}
import cotoami.components.{materialSymbol, ScrollArea}
import cotoami.backend.{ErrorJson, LinkBackend}
import cotoami.subparts.{Modal, ViewCoto}

object ModalConnect {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoId: Id[Coto],
      toSelection: Boolean = true,
      clearSelection: Boolean = true,
      connecting: Boolean = false,
      error: Option[String] = None
  ) {
    def connect(repo: Root): (Model, Cmd.One[AppMsg]) = {
      val acc: Cmd.One[Either[ErrorJson, Seq[Link]]] = pure(Right(Seq.empty))
      val cmd = repo.cotos.selectedIds
        .foldLeft(acc) { (cmd, selectedId) =>
          cmd.flatMap(_ match {
            case Right(links) => {
              if (alreadyConnected(selectedId, repo.links))
                pure(Right(links))
              else
                connectOne(selectedId).map(_.map(links :+ _))
            }
            case Left(e) => pure(Left(e))
          })
        }
        .map(Msg.Connected(_).into)
      (copy(connecting = true), cmd)
    }

    private def alreadyConnected(selectedId: Id[Coto], links: Links): Boolean =
      if (toSelection)
        links.linked(cotoId, selectedId)
      else
        links.linked(selectedId, cotoId)

    private def connectOne(
        selectedId: Id[Coto]
    ): Cmd.One[Either[ErrorJson, Link]] =
      LinkBackend.connect(
        if (toSelection) cotoId else selectedId,
        if (toSelection) selectedId else cotoId,
        None,
        None,
        None
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ConnectMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Reverse extends Msg
    object ClearSelectionToggled extends Msg
    object Connect extends Msg
    case class Connected(result: Either[ErrorJson, Seq[Link]]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotos, Cmd[AppMsg]) = {
    val default = (model, context.repo.cotos, Cmd.none)
    msg match {
      case Msg.Reverse =>
        default.copy(_1 = model.modify(_.toSelection).using(!_))

      case Msg.ClearSelectionToggled =>
        default.copy(_1 = model.modify(_.clearSelection).using(!_))

      case Msg.Connect =>
        model.connect(context.repo).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Connected(Right(links)) =>
        default.copy(
          _1 = model.copy(connecting = false),
          _2 =
            if (model.clearSelection)
              context.repo.cotos
            else
              context.repo.cotos.clearSelection,
          _3 = Modal.close(classOf[Modal.Connect])
        )

      case Msg.Connected(Left(e)) =>
        default.copy(
          _1 = model.copy(connecting = false, error = Some(e.default_message)),
          _3 = cotoami.error("Couldn't create links.", e)
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
      dialogClasses = "connect",
      closeButton = Some((classOf[Modal.Connect], dispatch)),
      error = model.error
    )(
      materialSymbol(Link.ConnectIconName),
      "Connect"
    )(
      div(className := "buttons reverse")(
        button(
          `type` := "button",
          className := "reverse contrast outline",
          onClick := (_ => dispatch(Msg.Reverse))
        )("Reverse")
      ),
      section(className := "source")(
        if (model.toSelection)
          coto.map(articleCoto)
        else
          divSelection
      ),
      div(className := "link")(
        span(className := "arrow")(
          materialSymbol("arrow_downward")
        )
      ),
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
          disabled := model.connecting,
          aria - "busy" := model.connecting.toString()
        )("Connect"),
        label(className := "clear-selection", htmlFor := "clear-selection")(
          input(
            `type` := "checkbox",
            id := "clear-selection",
            checked := model.clearSelection,
            disabled := model.connecting,
            onChange := (_ => dispatch(Msg.ClearSelectionToggled))
          ),
          "Clear selection"
        )
      )
    )
  }

  private def articleCoto(coto: Coto)(implicit
      context: Context
  ): ReactElement =
    article(className := "coto embedded")(
      header()(
        ViewCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ScrollArea()(
          ViewCoto.divContentPreview(coto)
        )
      )
    )

  private def divSelection(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "selection")(
      button(
        className := "selection default",
        onClick := (_ => dispatch(Modal.Msg.OpenModal(Modal.Selection)))
      )(
        s"Selected cotos (${context.repo.cotos.selectedIds.size})"
      )
    )
}

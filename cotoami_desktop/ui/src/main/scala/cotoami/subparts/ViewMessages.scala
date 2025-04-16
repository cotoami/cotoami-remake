package cotoami.subparts

import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.components.materialSymbol

import cotoami.{Into, Msg => AppMsg}
import cotoami.models.SystemMessages

object ViewMessages {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      open: Boolean = false,
      messages: SystemMessages = SystemMessages()
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.ViewMessagesMsg(this)
  }

  object Msg {
    case object Toggle extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Toggle =>
        (model.copy(open = !model.open), Cmd.none)
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.open) {
      section(className := "system-messages")(
        header(className := "tools")(
          button(
            className := "close default",
            onClick := (_ => dispatch(Msg.Toggle.into))
          )(materialSymbol("close"))
        ),
        ContentArea(entries = model.messages.entries)
      )
    }

  @react object ContentArea {
    case class Props(
        entries: Seq[SystemMessages.Entry]
    )

    val component = FunctionalComponent[Props] { props =>
      val bottomRef = useRef[html.Div](null)

      useEffect(() => {
        bottomRef.current.scrollIntoView(false)
      })

      section(className := "entries")(
        props.entries.map(entry =>
          section(
            className := s"entry ${entry.category.name}",
            key := entry.timestamp.getTime().toString()
          )(
            section(className := "category")(
              materialSymbol(entry.category.icon)
            ),
            div(className := "content")(
              section(className := "message")(entry.message),
              section(className := "details")(entry.details)
            )
          )
        ) :+ div(key := "bottom", ref := bottomRef)(): _*
      )
    }
  }
}

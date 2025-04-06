package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import marubinotto.Validation
import marubinotto.components.materialSymbol

import cotoami.{Msg => AppMsg}
import cotoami.models.ParentStatus
import cotoami.repository.Nodes

package object subparts {

  def labeledField(
      classes: String = "",
      label: String,
      labelFor: Option[String] = None
  )(fieldContent: ReactElement*): ReactElement =
    div(className := s"labeled-field ${classes}")(
      html.label(htmlFor := labelFor)(label),
      div(className := "field")(
        fieldContent: _*
      )
    )

  def labeledInputField(
      classes: String = "",
      label: String,
      inputType: String = "text",
      inputPlaceholder: Option[String] = None,
      inputValue: String,
      readOnly: Boolean = false,
      inputErrors: Option[Validation.Result] = None,
      onInput: (String => Unit) = (_ => ())
  ): ReactElement =
    labeledField(
      classes = classes,
      label = label
    )(
      input(
        `type` := inputType,
        placeholder := inputPlaceholder,
        value := inputValue,
        html.readOnly := readOnly,
        Validation.ariaInvalid(
          inputErrors.getOrElse(Validation.Result.notYetValidated)
        ),
        // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
        // (onChange is almost the same as onInput in React)
        onChange := (e => onInput(e.target.value))
      ),
      inputErrors.map(Validation.sectionValidationError)
    )

  def buttonHelp(disable: Boolean, onButtonClick: () => Unit): ReactElement =
    button(
      className := s"default help",
      disabled := disable,
      onClick := onButtonClick
    )(
      materialSymbol("help")
    )

  def sectionHelp(
      display: Boolean,
      onCloseClick: () => Unit,
      contents: ReactElement*
  ): ReactElement =
    Option.when(display) {
      section(className := "help")(
        button(
          className := "close default",
          onClick := onCloseClick
        ),
        contents
      )
    }

  def paneToggle(
      paneName: String
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    marubinotto.components.paneToggle(
      onFoldClick = () => dispatch(AppMsg.SetPaneOpen(paneName, false)),
      onUnfoldClick = () => dispatch(AppMsg.SetPaneOpen(paneName, true))
    )

  val EnterKey = "Enter"

  def detectCtrlEnter[T](e: SyntheticKeyboardEvent[T]): Boolean =
    e.key == EnterKey && (e.ctrlKey || e.metaKey)

  case class ParentStatusView(
      className: String,
      icon: ReactElement,
      title: String,
      message: Option[String]
  )

  def viewParentStatus(status: ParentStatus): Option[ParentStatusView] =
    status match {
      case ParentStatus.Disabled =>
        Some(
          ParentStatusView(
            "disabled",
            materialSymbol("link_off"),
            "not synced",
            None
          )
        )
      case ParentStatus.Connecting(message) =>
        Some(
          ParentStatusView(
            "connecting",
            span(className := "busy", aria - "busy" := "true")(),
            "connecting",
            message
          )
        )
      case ParentStatus.InitFailed(message) =>
        Some(
          ParentStatusView(
            "init-failed",
            materialSymbol("error"),
            "initialization failed",
            Some(message)
          )
        )
      case ParentStatus.Disconnected(message) =>
        Some(
          ParentStatusView(
            "disconnected",
            materialSymbol("do_not_disturb_on"),
            "disconnected",
            message
          )
        )
      case _ => None
    }

  def sectionClientNodesCount(
      clientCount: Double,
      nodes: Nodes
  ): ReactElement = {
    val connecting = nodes.activeClients.count
    section(className := "client-nodes-count")(
      Option.when(connecting > 0) {
        Fragment(
          code(className := "connecting")(
            nodes.activeClients.count
          ),
          "connecting",
          span(className := "separator")("/")
        )
      },
      code(className := "nodes")(clientCount),
      "nodes"
    )
  }
}

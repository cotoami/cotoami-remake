package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import marubinotto.Validation

import cotoami.{Msg => AppMsg}
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

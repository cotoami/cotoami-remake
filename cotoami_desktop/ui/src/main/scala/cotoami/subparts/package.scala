package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import cotoami.{Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Ito, Node, ParentStatus}
import cotoami.repository.Nodes
import cotoami.components.{materialSymbol, optionalClasses, toolButton}

package object subparts {

  def imgNode(node: Node, additionalClasses: String = ""): ReactElement =
    img(
      className := s"node-icon ${additionalClasses}",
      alt := node.name,
      src := node.iconUrl
    )

  def spanNode(node: Node): ReactElement =
    span(className := "node")(
      imgNode(node),
      span(className := "name")(node.name)
    )

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
      inputId: String,
      inputType: String,
      inputPlaceholder: Option[String] = None,
      inputValue: String,
      readOnly: Boolean = false,
      inputErrors: Option[Validation.Result] = None,
      onInput: (String => Unit) = (_ => ())
  ): ReactElement =
    labeledField(
      classes = classes,
      label = label,
      labelFor = Some(inputId)
    )(
      input(
        `type` := inputType,
        id := inputId,
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

  sealed trait CollapseDirection
  case object ToLeft extends CollapseDirection
  case object ToRight extends CollapseDirection

  def paneToggle(
      paneName: String,
      direction: CollapseDirection = ToLeft
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    div(className := "pane-toggle")(
      button(
        className := "fold default",
        title := "Fold",
        onClick := (_ => dispatch(AppMsg.OpenOrClosePane(paneName, false)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_left"
            case ToRight => "arrow_right"
          }
        )
      ),
      button(
        className := "unfold default",
        title := "Unfold",
        onClick := (_ => dispatch(AppMsg.OpenOrClosePane(paneName, true)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_right"
            case ToRight => "arrow_left"
          }
        )
      )
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

  def buttonPin(
      ito: Ito
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val canEditPin = context.repo.nodes.canEdit(ito)
    div(
      className := optionalClasses(
        Seq(
          ("ito-container", true),
          ("with-description", ito.description.isDefined)
        )
      )
    )(
      div(
        className := optionalClasses(
          Seq(
            ("pin", true),
            ("ito", true),
            ("editable", canEditPin)
          )
        )
      )(
        toolButton(
          classes = "edit-pin",
          symbol = "push_pin",
          tip = Option.when(canEditPin)("Edit pin"),
          tipPlacement = "right",
          disabled = !canEditPin,
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.ItoEditor(ito)))
          }
        ),
        ito.description.map(phrase =>
          section(
            className := "description",
            onClick := (e => {
              e.stopPropagation()
              if (canEditPin)
                dispatch(Modal.Msg.OpenModal(Modal.ItoEditor(ito)))
            })
          )(phrase)
        )
      )
    )
  }

  def buttonSubcotoIto(
      ito: Ito
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val canEditIto = context.repo.nodes.canEdit(ito)
    div(
      className := optionalClasses(
        Seq(
          ("ito-container", true),
          ("with-description", ito.description.isDefined)
        )
      )
    )(
      div(
        className := optionalClasses(
          Seq(
            ("subcoto-ito", true),
            ("ito", true),
            ("editable", canEditIto)
          )
        )
      )(
        toolButton(
          classes = "edit-ito",
          symbol = "subdirectory_arrow_right",
          tip = Option.when(canEditIto)("Edit ito"),
          tipPlacement = "right",
          disabled = !canEditIto,
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.ItoEditor(ito)))
          }
        ),
        ito.description.map(phrase =>
          section(
            className := "description",
            onClick := (e => {
              e.stopPropagation()
              if (canEditIto)
                dispatch(Modal.Msg.OpenModal(Modal.ItoEditor(ito)))
            })
          )(phrase)
        )
      )
    )
  }
}

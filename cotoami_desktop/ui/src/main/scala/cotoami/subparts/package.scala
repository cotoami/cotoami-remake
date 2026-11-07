package cotoami

import marubinotto.react.{Fragment, ReactNode}
import marubinotto.react.html._
import marubinotto.react.SyntheticKeyboardEvent

import cotoami.{Msg => AppMsg}
import cotoami.repository.Nodes

package object subparts {

  def paneToggle(
      paneName: String
  )(using dispatch: AppMsg => Unit): ReactNode =
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
  )(using
      context: Context
  ): ReactNode = {
    val connecting = nodes.activeClients.count
    section(className := "client-nodes-count")(
      Option.when(connecting > 0) {
        Fragment(
          code(className := "connecting")(
            nodes.activeClients.count
          ),
          context.i18n.text.ModalClients_connecting,
          span(className := "separator")("/")
        )
      },
      code(className := "nodes")(clientCount),
      context.i18n.text.ModalClients_nodes
    )
  }
}

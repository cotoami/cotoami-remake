package cotoami.updates

import com.softwaremill.quicklens._

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

import cotoami.{Model, Msg}
import cotoami.models.{Id, Node, Server}
import cotoami.repository.Root
import cotoami.backend.{
  ActiveClientBackend,
  ChildNodeBackend,
  LocalNodeEventJson,
  NotConnectedBackend,
  ParentSyncEndBackend,
  ParentSyncProgressBackend
}
import cotoami.subparts.Modal

object LocalNodeEvent {

  def handle(event: LocalNodeEventJson, model: Model): (Model, Cmd[Msg]) = {
    // ServerStateChanged
    for (change <- event.ServerStateChanged.toOption) {
      val nodeId = Id[Node](change.node_id)
      val notConnected =
        Nullable.toOption(change.not_connected)
          .map(NotConnectedBackend.toModel(_))
      val childPrivileges =
        Nullable.toOption(change.child_privileges)
          .map(ChildNodeBackend.toModel(_))
      return (
        model.modify(_.repo.nodes.servers).using(
          _.setState(nodeId, notConnected, childPrivileges)
        ),
        notConnected match {
          case Some(Server.NotConnected.Unauthorized) =>
            Modal.open(
              Modal.InputPassword(
                Root.Msg.Reconnect(nodeId, _).into,
                model.i18n.text.ModalInputClientPassword_title,
                Some(model.i18n.text.ModalInputClientPassword_message),
                model.repo.nodes.get(nodeId)
              )
            )
          case _ => Cmd.none
        }
      )
    }

    // ParentSyncProgress
    for (progress <- event.ParentSyncProgress.toOption) {
      val parentSync =
        model.parentSync.progress(ParentSyncProgressBackend.toModel(progress))
      val modalStack =
        if (parentSync.comingManyChanges)
          model.modalStack.openIfNot(Modal.ParentSync())
        else
          model.modalStack
      return (
        model.copy(parentSync = parentSync, modalStack = modalStack),
        Cmd.none
      )
    }

    // ParentSyncEnd
    for (end <- event.ParentSyncEnd.toOption) {
      return (
        model.modify(_.parentSync).using(
          _.end(ParentSyncEndBackend.toModel(end))
        ),
        Cmd.none
      )
    }

    // ClientConnected
    for (activeClientJson <- event.ClientConnected.toOption) {
      val activeClient = ActiveClientBackend.toModel(activeClientJson)
      return (
        model.modify(_.repo.nodes.activeClients).using(
          _.put(activeClient)
        ),
        Cmd.none
      )
    }

    // ClientDisconnected
    for (json <- event.ClientDisconnected.toOption) {
      return (
        model.modify(_.repo.nodes.activeClients).using(
          _.remove(Id(json.node_id))
        ),
        Cmd.none
      )
    }

    (model, Cmd.none)
  }
}

package cotoami.updates

import com.softwaremill.quicklens._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.facade.Nullable

import cotoami.{Model, Msg}
import cotoami.models.{Id, Node, Server}
import cotoami.repository.Root
import cotoami.backend.{
  ActiveClientBackend,
  ChildNodeBackend,
  InitialDataset,
  LocalNodeEventJson,
  NotConnectedBackend,
  ParentSyncEndBackend,
  ParentSyncProgressBackend
}
import cotoami.subparts.Modal

object LocalNodeEvent {

  def handle(event: LocalNodeEventJson, model: Model): (Model, Cmd[Msg]) =
    event.ServerStateChanged.toOption.map { change =>
      val nodeId = Id[Node](change.node_id)
      val notConnected =
        Nullable.toOption(change.not_connected)
          .map(NotConnectedBackend.toModel(_))
      val childPrivileges =
        Nullable.toOption(change.child_privileges)
          .map(ChildNodeBackend.toModel(_))

      val disconnectedFromRemoteSelf =
        model.repo.nodes.isSelfRemote &&
          model.repo.nodes.isSelf(nodeId) &&
          notConnected.isDefined

      (
        model
          .modify(_.repo.nodes.servers).using(
            _.setState(nodeId, notConnected, childPrivileges)
          )
          .modify(_.modalStack).using { stack =>
            if (disconnectedFromRemoteSelf)
              stack.clear
            else
              stack
          },
        if (disconnectedFromRemoteSelf) {
          // Switch back to the local node when disconnected from the
          // remote node on which the app is currently operating.
          InitialDataset.switchSelfNodeTo(None).flatMap(_ match {
            case Right(dataset) =>
              Browser.send(Msg.SetInitialDataset(dataset))
            case Left(_) => Cmd.none
          })
        } else {
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
        }
      )
    }.orElse(
      event.ParentSyncProgress.toOption.map { progress =>
        val parentSync =
          model.parentSync.progress(ParentSyncProgressBackend.toModel(progress))
        val modalStack =
          if (parentSync.comingManyChanges)
            model.modalStack.openIfNot(Modal.ParentSync())
          else
            model.modalStack
        (
          model.copy(parentSync = parentSync, modalStack = modalStack),
          Cmd.none
        )
      }
    ).orElse(
      event.ParentSyncEnd.toOption.map { end =>
        (
          model.modify(_.parentSync).using(
            _.end(ParentSyncEndBackend.toModel(end))
          ),
          Cmd.none
        )
      }
    ).orElse(
      event.ClientConnected.toOption.map { activeClientJson =>
        val activeClient = ActiveClientBackend.toModel(activeClientJson)
        (
          model.modify(_.repo.nodes.activeClients).using(
            _.put(activeClient)
          ),
          Cmd.none
        )
      }
    ).orElse(
      event.ClientDisconnected.toOption.map { json =>
        (
          model.modify(_.repo.nodes.activeClients).using(
            _.remove(Id(json.node_id))
          ),
          Cmd.none
        )
      }
    ).orElse(
      event.PluginEvent.toOption.flatMap { pluginEvent =>
        pluginEvent.Registered.toOption.map { json =>
          (
            model.info(
              "Plugin registered.",
              Some(s"${json.name} v${json.version} (${json.identifier})")
            ),
            Cmd.none
          )
        }.orElse(
          pluginEvent.InvalidFile.toOption.map { json =>
            (
              model.error(
                "Invalid plugin file.",
                Some(s"${json.path} - ${json.message}")
              ),
              Cmd.none
            )
          }
        ).orElse(
          pluginEvent.Info.toOption.map { json =>
            (
              model.info(s"Plugin(${json.identifier}): ${json.message}", None),
              Cmd.none
            )
          }
        ).orElse(
          pluginEvent.Error.toOption.map { json =>
            (
              model.error(
                s"Plugin(${json.identifier}): an error occurred",
                Some(json.message)
              ),
              Cmd.none
            )
          }
        ).orElse(
          pluginEvent.Destroyed.toOption.map { json =>
            (
              model.info("Plugin destroyed.", Some(json.identifier)),
              Cmd.none
            )
          }
        )
      }
    ).getOrElse((model, Cmd.none))
}

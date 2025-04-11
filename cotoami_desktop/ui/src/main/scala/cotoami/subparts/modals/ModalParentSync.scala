package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.facade.Nullable

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node, ParentSync}
import cotoami.subparts.{Modal, PartsNode}

object ModalParentSync {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model()

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ParentSyncMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Close extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Close =>
        (
          model,
          Cmd.Batch(
            Modal.close(classOf[Modal.ParentSync]),
            Browser.send(AppMsg.ReloadRepository)
          )
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      parentSync: ParentSync
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "parent-sync"
    )(
      Modal.spanTitleIcon("sync"),
      "Syncing with Remote Nodes"
    )(
      Option.when(!parentSync.syncing.isEmpty) {
        sectionSyncing(parentSync)
      },
      Option.when(!parentSync.synced.isEmpty) {
        sectionSynced(parentSync)
      }
    )

  private def sectionSyncing(
      parentSync: ParentSync
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "syncing")(
      h2()("Syncing"),
      ul()(
        parentSync.syncing.map(progress => {
          val current = progress.progress.toString()
          val total = progress.total.toString()
          li(key := progress.nodeId.uuid)(
            section(className := "parent-node")(
              spanNode(progress.nodeId),
              span(className := "progress")(s"${current}/${total}")
            ),
            div(className := "progress-bar")(
              html.progress(value := current, max := total)
            )
          )
        })
      )
    )

  private def sectionSynced(
      parentSync: ParentSync
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "synced")(
      h2()("Synced"),
      ul()(
        parentSync.synced.map(done => {
          li(key := done.nodeId.uuid)(
            section(className := "parent-node")(
              spanNode(done.nodeId),
              done.range.map(range =>
                span(className := "result")(
                  span(className := "range")(s"${range._1} to ${range._2}"),
                  span(className := "changes")(
                    s"(${range._2 - range._1 + 1} changes)"
                  )
                )
              ),
              Nullable.toOption(done.error).map(error =>
                span(className := "error")(error)
              )
            )
          )
        })
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          disabled := !parentSync.syncing.isEmpty,
          onClick := (e => dispatch(Msg.Close))
        )("OK")
      )
    )

  private def spanNode(
      id: Id[Node]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    context.repo.nodes.get(id).map(PartsNode.spanNode)
      .getOrElse(
        span(className := "node not-found")(
          s"Node not found: ${id}"
        )
      )
}

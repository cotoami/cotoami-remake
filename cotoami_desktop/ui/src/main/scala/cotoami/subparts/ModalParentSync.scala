package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Msg => AppMsg}
import cotoami.backend.{Id, Node, Nullable}
import cotoami.repositories.Domain
import cotoami.models.ParentSync

object ModalParentSync {

  case class Model()

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.ParentSyncMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Close extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.Close =>
        (
          model,
          Seq(
            Modal.close(classOf[Modal.ParentSync]),
            Browser.send(AppMsg.ReloadDomain)
          )
        )
    }

  def apply(
      model: Model,
      parentSync: ParentSync,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
    dialog(
      className := "parent-sync",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          h1()("Syncing with remote nodes")
        ),
        div(className := "body")(
          Option.when(!parentSync.syncing.isEmpty) {
            sectionSyncing(parentSync, domain, dispatch)
          },
          Option.when(!parentSync.synced.isEmpty) {
            sectionSynced(parentSync, domain, dispatch)
          }
        )
      )
    )

  private def sectionSyncing(
      parentSync: ParentSync,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
    section(className := "syncing")(
      h2()("Syncing"),
      ul()(
        parentSync.syncing.map(progress => {
          val current = progress.progress.toString()
          val total = progress.total.toString()
          li()(
            section(className := "parent-node")(
              spanNode(progress.nodeId, domain),
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
      parentSync: ParentSync,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
    section(className := "synced")(
      h2()("Synced"),
      ul()(
        parentSync.synced.map(done => {
          li()(
            section(className := "parent-node")(
              spanNode(done.nodeId, domain),
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
          onClick := (e => dispatch(Msg.Close.toApp))
        )("OK")
      )
    )

  private def spanNode(id: Id[Node], domain: Domain): ReactElement =
    domain.nodes.get(id).map(node =>
      span(className := "node")(
        nodeImg(node),
        span(className := "name")(node.name)
      )
    ).getOrElse(
      span(className := "node not-found")(
        s"Node not found: ${id}"
      )
    )
}

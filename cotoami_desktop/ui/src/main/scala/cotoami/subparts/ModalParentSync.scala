package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html
import slinky.web.html._

import cotoami.backend.Id
import cotoami.repositories.Domain
import cotoami.models.ParentSync

object ModalParentSync {

  def apply(
      parentSync: ParentSync,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
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
          }
        )
      )
    )

  private def sectionSyncing(
      parentSync: ParentSync,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "syncing")(
      h2()("Syncing"),
      ul()(
        parentSync.syncing.map(progress => {
          li()(
            domain.nodes.get(Id(progress.node_id)).map(node =>
              section(className := "parent-node")(
                nodeImg(node),
                span(className := "name")(node.name)
              )
            ).getOrElse(
              section(className := "parent-node not-found")(
                s"Node not found: ${progress.node_id}"
              )
            ),
            div(className := "progress")(
              html.progress(
                value := progress.progress.toString(),
                max := progress.total.toString()
              )
            )
          )
        })
      )
    )
}

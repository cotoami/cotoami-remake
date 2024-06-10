package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

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
          //
        )
      )
    )
}

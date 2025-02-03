package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Link

object ToolbarReorder {

  def apply(
      link: Link,
      previousOrder: Int,
      nextOrder: Int,
      maxOrder: Int
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "reorder-toolbar")()
}

package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Coto, PaginatedIds}

object PaneSearch {

  case class Model(
      query: String = "",
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),
      loading: Boolean = false
  )

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "search")(
    )
}

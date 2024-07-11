package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.backend.{Cotonoma, Node}
import cotoami.repositories.Domain
import cotoami.components.materialSymbol

object AppHeader {

  def apply(
      model: Model,
      dispatch: AppMsg => Unit
  )(implicit domain: Domain): ReactElement =
    header(
      data - "tauri-drag-region" := "default",
      data - "os" := model.systemInfo.map(_.os).getOrElse("")
    )(
      div(
        className := "header-content",
        data - "tauri-drag-region" := "default"
      )(
        button(
          className := "app-info default",
          title := "View app info"
        )(
          img(
            className := "app-icon",
            alt := "Cotoami",
            src := "/images/logo/logomark.svg"
          )
        ),
        domain.location.map(sectionLocation(_, dispatch)),
        section(className := "tools")(
          domain.nodes.operating.map(buttonNodeProfile(_, dispatch))
        )
      )
    )

  private def sectionLocation(
      location: (Node, Option[Cotonoma]),
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val (node, cotonoma) = location
    section(
      className := "location",
      data - "tauri-drag-region" := "default"
    )(
      a(
        className := "node-home",
        title := node.name,
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.SelectNode(node.id))
        })
      )(imgNode(node)),
      cotonoma.map(cotonoma =>
        Fragment(
          materialSymbol("chevron_right", "arrow"),
          h1(className := "current-cotonoma")(cotonoma.name)
        )
      )
    )
  }

  private def buttonNodeProfile(
      node: Node,
      dispatch: AppMsg => Unit
  ): ReactElement =
    button(
      className := "node-profile, default",
      title := "Node profile",
      onClick := (_ =>
        dispatch(
          Modal.Msg.OpenModal(Modal.NodeProfile(node)).toApp
        )
      )
    )(
      imgNode(node)
    )
}

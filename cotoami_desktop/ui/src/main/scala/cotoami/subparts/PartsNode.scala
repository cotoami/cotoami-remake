package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.models.Node

object PartsNode {

  def imgNode(node: Node, additionalClasses: String = ""): ReactElement =
    img(
      className := s"node-icon ${additionalClasses}",
      alt := node.name,
      src := node.iconUrl
    )

  def spanNode(node: Node): ReactElement =
    span(className := "node")(
      imgNode(node),
      span(className := "name")(node.name)
    )
}

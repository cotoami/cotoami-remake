package cotoami.subparts

import scala.scalajs.js

import org.scalajs.dom
import org.scalatest.funsuite.AnyFunSuite

import cotoami.models.{Id, LocalNode, Node}
import cotoami.repository.{Nodes, Root}

class AppHeaderSpec extends AnyFunSuite {
  test("browserFocusedNodeId falls back to the self node") {
    val selfId = Id[Node]("self-node")
    val repo = Root(
      nodes = Nodes(
        map = Map(selfId -> node(selfId)),
        selfSettings = Some(LocalNode(selfId, None, false, None))
      )
    )

    assert(AppHeader.browserFocusedNodeId(repo) == Some(selfId))
  }

  test("browserFocusedNodeId prefers the focused node") {
    val selfId = Id[Node]("self-node")
    val focusedId = Id[Node]("focused-node")
    val repo = Root(
      nodes = Nodes(
        map = Map(
          selfId -> node(selfId),
          focusedId -> node(focusedId)
        ),
        selfSettings = Some(LocalNode(selfId, None, false, None)),
        focusedId = Some(focusedId)
      )
    )

    assert(AppHeader.browserFocusedNodeId(repo) == Some(focusedId))
  }

  private def node(id: Id[Node]): Node =
    Node(
      id,
      new dom.Blob(js.Array(), new dom.BlobPropertyBag {}),
      id.uuid,
      None,
      0,
      "2026-01-01T00:00:00"
    )
}

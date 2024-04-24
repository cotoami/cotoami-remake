package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import com.softwaremill.quicklens._

import cotoami.backend.{Coto, Id}
import cotoami.repositories.Links
import cotoami.components.materialSymbol

object SectionTraversals {

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def openTraversal(start: Id[Coto]): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def traverse(traversalIndex: Int, stepIndex: Int, next: Id[Coto]): Model =
      this.traversals.lift(traversalIndex).map(traversal => {
        this.modify(_.traversals).using(
          _.updated(traversalIndex, traversal.traverse(stepIndex, next))
        )
      }).getOrElse(this)

    def closeTraversal(traversalIndex: Int): Model =
      this.modify(_.traversals).using(_.patch(traversalIndex, Nil, 1))
  }

  case class Traversal(start: Id[Coto], steps: Seq[Id[Coto]] = Seq.empty) {
    def traverse(stepIndex: Int, next: Id[Coto]): Traversal =
      this.modify(_.steps).using(_.take(stepIndex) :+ next)

    def traverseToParent(parentId: Id[Coto], links: Links): Traversal = {
      val oldStart = this.start
      this
        .modify(_.start).setTo(parentId)
        .modify(_.steps).using(steps =>
          // if oldStart has no outgoing links, there's no need to prepend it
          // to the steps because the new start box will contain it as a sub coto.
          if (links.linksFrom(oldStart).isDefined) {
            oldStart +: steps
          } else {
            steps
          }
        )
    }

    def traversed(stepIndex: Int, subCotoId: Id[Coto]): Boolean =
      this.steps.lift(stepIndex + 1).map(_ == subCotoId).getOrElse(false)
  }

  object Traversal {
    def apply(start: Id[Coto]): Traversal = Traversal(start)
  }

  def apply(model: Model, dispatch: cotoami.Msg => Unit): Option[ReactElement] =
    Option.when(!model.traversals.isEmpty) {
      section(className := "traversals")(
        model.traversals.map(sectionTraversal(_, model, dispatch)): _*
      )
    }

  private def sectionTraversal(
      traversal: Traversal,
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "traversal header-and-body")(
      header(className := "tools")(
        button(className := "close-traversal default")(
          materialSymbol("close")
        )
      ),
      section(className := "body")(
      )
    )
}

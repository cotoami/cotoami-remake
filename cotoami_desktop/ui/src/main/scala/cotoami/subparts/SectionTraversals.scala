package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import com.softwaremill.quicklens._

import cotoami.Context
import cotoami.backend.{Coto, Id, Link}
import cotoami.repositories.{Domain, Links}
import cotoami.components.{materialSymbol, optionalClasses}

object SectionTraversals {

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def openTraversal(start: Id[Coto]): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def step(traversalIndex: Int, stepIndex: Int, next: Id[Coto]): Model =
      this.traversals.lift(traversalIndex).map(traversal => {
        this.modify(_.traversals).using(
          _.updated(traversalIndex, traversal.step(stepIndex, next))
        )
      }).getOrElse(this)

    def closeTraversal(traversalIndex: Int): Model =
      this.modify(_.traversals).using(_.patch(traversalIndex, Nil, 1))
  }

  case class Traversal(start: Id[Coto], steps: Seq[Id[Coto]] = Seq.empty) {
    def step(stepIndex: Int, next: Id[Coto]): Traversal =
      this.modify(_.steps).using(_.take(stepIndex) :+ next)

    def stepToParent(parentId: Id[Coto], links: Links): Traversal = {
      val oldStart = this.start
      this
        .modify(_.start).setTo(parentId)
        .modify(_.steps).using(steps =>
          // if oldStart has no outgoing links, there's no need to prepend it
          // to the steps because the new start box will contain it as a sub coto.
          if (links.linksFrom(oldStart).isEmpty) {
            steps
          } else {
            oldStart +: steps
          }
        )
    }

    def traversed(stepIndex: Int, subCotoId: Id[Coto]): Boolean =
      this.steps.lift(stepIndex + 1).map(_ == subCotoId).getOrElse(false)
  }

  object Traversal {
    def apply(start: Id[Coto]): Traversal = Traversal(start)
  }

  def apply(
      model: Model,
      openedCotoViews: Set[String],
      domain: Domain,
      context: Context,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(!model.traversals.isEmpty) {
      section(className := "traversals")(
        model.traversals.zipWithIndex.map { case (traversal, index) =>
          sectionTraversal(
            traversal,
            index,
            openedCotoViews,
            domain,
            context,
            dispatch
          )
        }: _*
      )
    }

  private def sectionTraversal(
      traversal: Traversal,
      traversalIndex: Int,
      openedCotoViews: Set[String],
      domain: Domain,
      context: Context,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    section(className := "traversal header-and-body")(
      header(className := "tools")(
        button(className := "close-traversal default")(
          materialSymbol("close")
        )
      ),
      section(className := "body")(
        divParents(domain.parentsOf(traversal.start)),
        // traversal start
        domain.cotos.get(traversal.start).map(
          divTraversalStep(
            _,
            None,
            traversalIndex,
            openedCotoViews,
            domain,
            context,
            dispatch
          )
        ),
        // traversal steps
        traversal.steps.zipWithIndex.map { case (step, index) =>
          domain.cotos.get(step).map(
            divTraversalStep(
              _,
              Some(index),
              traversalIndex,
              openedCotoViews,
              domain,
              context,
              dispatch
            )
          )
        }
      )
    )
  }

  private def divParents(parents: Seq[(Coto, Link)]): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      div(className := "parents")(
        ul(className := "parents")(
          parents.map { case (parent, link) =>
            li()(
              button(className := "parent default")(parent.abbreviate)
            )
          }
        ),
        div(className := "arrow")(
          materialSymbol("arrow_downward")
        )
      )
    }

  private def divTraversalStep(
      coto: Coto,
      stepIndex: Option[Int],
      traversalIndex: Int,
      openedCotoViews: Set[String],
      domain: Domain,
      context: Context,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    val subCotos = domain.childrenOf(coto.id)
    div(
      className := optionalClasses(
        Seq(
          ("traversal-start", stepIndex.isEmpty),
          ("traversal-step", stepIndex.isDefined)
        )
      )
    )(
      Option.when(stepIndex.isDefined) {
        div(className := "arrow")(
          materialSymbol("arrow_downward")
        )
      },
      article(
        className := optionalClasses(
          Seq(
            ("coto", true),
            ("has-children", !subCotos.isEmpty)
          )
        )
      )(
      )
    )
  }
}

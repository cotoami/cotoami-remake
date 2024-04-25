package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.backend.{Coto, Id, Link}
import cotoami.repositories.{Domain, Links}
import cotoami.components.{materialSymbol, optionalClasses, ToolButton}

object SectionTraversals {

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def isEmpty: Boolean = this.traversals.isEmpty

    def openTraversal(start: Id[Coto]): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def step(traversalIndex: Int, stepIndex: Int, step: Id[Coto]): Model =
      this.traversals.lift(traversalIndex).map(traversal => {
        this.modify(_.traversals).using(
          _.updated(traversalIndex, traversal.step(stepIndex, step))
        )
      }).getOrElse(this)

    def closeTraversal(traversalIndex: Int): Model =
      this.modify(_.traversals).using(_.patch(traversalIndex, Nil, 1))
  }

  case class Traversal(start: Id[Coto], steps: Seq[Id[Coto]] = Seq.empty) {
    def step(stepIndex: Int, step: Id[Coto]): Traversal =
      this.modify(_.steps).using(_.take(stepIndex) :+ step)

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

  sealed trait Msg
  case class OpenTraversal(start: Id[Coto]) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case OpenTraversal(start) =>
        (model.openTraversal(start), Seq.empty)
    }

  def apply(
      model: Model,
      openedCotoViews: Set[String],
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(!model.traversals.isEmpty) {
      section(className := "traversals")(
        model.traversals.zipWithIndex.map(
          sectionTraversal(
            _,
            openedCotoViews,
            domain,
            dispatch
          )
        ): _*
      )
    }

  private def sectionTraversal(
      traversal: (Traversal, Int),
      openedCotoViews: Set[String],
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    section(className := "traversal header-and-body")(
      header(className := "tools")(
        button(className := "close-traversal default")(
          materialSymbol("close")
        )
      ),
      section(className := "body")(
        divParents(domain.parentsOf(traversal._1.start)),
        // traversal start
        domain.cotos.get(traversal._1.start).map(
          divTraversalStep(
            _,
            None,
            traversal,
            openedCotoViews,
            domain,
            dispatch
          )
        ),
        // traversal steps
        traversal._1.steps.zipWithIndex.map { case (step, index) =>
          domain.cotos.get(step).map(
            divTraversalStep(
              _,
              Some(index),
              traversal,
              openedCotoViews,
              domain,
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
      traversal: (Traversal, Int),
      openedCotoViews: Set[String],
      domain: Domain,
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
        header()(
          ViewCoto.otherCotonomas(coto, domain, dispatch)
        ),
        div(className := "body")(
          ViewCoto.content(
            coto,
            s"traversal-${traversal._2}-${coto.id}",
            openedCotoViews,
            domain,
            dispatch
          )
        )
      ),
      ol(className := "sub-cotos")(
        subCotos.map { case (link, subCoto) =>
          liSubCoto(
            link,
            subCoto,
            stepIndex,
            traversal,
            openedCotoViews,
            domain,
            dispatch
          )
        }
      )
    )
  }

  private def liSubCoto(
      link: Link,
      coto: Coto,
      stepIndex: Option[Int],
      traversal: (Traversal, Int),
      openedCotoViews: Set[String],
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    li(key := link.id.uuid, className := "sub")(
      article(
        className := optionalClasses(
          Seq(
            ("sub-coto", true),
            ("coto", true),
            (
              "traversed",
              stepIndex.map(traversal._1.traversed(_, coto.id)).getOrElse(false)
            )
          )
        )
      )(
        header()(
          ToolButton(
            classes = "unlink",
            tip = "Unlink",
            tipPlacement = "right",
            symbol = "subdirectory_arrow_right"
          ),
          ViewCoto.otherCotonomas(coto, domain, dispatch)
        ),
        div(className := "body")(
          ViewCoto.content(
            coto,
            s"traversal-${traversal._2}-sub-${coto.id}",
            openedCotoViews,
            domain,
            dispatch
          ),
          Option.when(domain.links.anyLinksFrom(coto.id)) {
            ToolButton(
              classes = "traverse",
              tip = "Traverse",
              tipPlacement = "left",
              symbol = "arrow_downward"
            )
          }
        )
      )
    )
}

package cotoami.subparts

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._
import java.time.Instant

import fui.Cmd
import cotoami.backend.{Coto, Id, Link}
import cotoami.repositories.{Domain, Links}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  ToolButton
}

object SectionTraversals {

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def isEmpty: Boolean = this.traversals.isEmpty

    def openTraversal(start: Id[Coto]): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def step(traversalIndex: Int, stepIndex: Int, step: Id[Coto]): Model =
      this.modify(_.traversals.index(traversalIndex)).using(
        _.step(stepIndex, step)
      )

    def stepToParent(
        traversalIndex: Int,
        parentId: Id[Coto],
        links: Links
    ): Model =
      this.modify(_.traversals.index(traversalIndex)).using(
        _.stepToParent(parentId, links)
      )

    def closeTraversal(traversalIndex: Int): Model =
      this.modify(_.traversals).using(_.patch(traversalIndex, Nil, 1))
  }

  case class Traversal(
      start: Id[Coto],
      steps: Seq[Id[Coto]] = Seq.empty,
      id: String =
        Instant.now().toEpochMilli().toString() // for react element's key
  ) {
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

    def traversed(stepIndex: Option[Int], subCotoId: Id[Coto]): Boolean =
      (stepIndex match {
        case Some(index) => this.steps.lift(index + 1).map(_ == subCotoId)
        // For the start coto of the traversal
        case None => this.steps.headOption.map(_ == subCotoId)
      }).getOrElse(false)
  }

  sealed trait Msg {
    def toAppMsg: cotoami.Msg = cotoami.SectionTraversalsMsg(this)
  }

  case class OpenTraversal(start: Id[Coto]) extends Msg
  case class CloseTraversal(traversalIndex: Int) extends Msg
  case class Step(traversalIndex: Int, stepIndex: Int, step: Id[Coto])
      extends Msg
  case class StepToParent(traversalIndex: Int, parentId: Id[Coto]) extends Msg

  def update(
      msg: Msg,
      model: Model,
      domain: Domain
  ): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case OpenTraversal(start) =>
        (
          model.openTraversal(start),
          // scroll to the right end on opening a traversal.
          Seq(
            Cmd(IO.async { cb =>
              IO {
                js.timers.setTimeout(10) {
                  dom.document.getElementById(
                    PaneStock.ScrollableElementId
                  ) match {
                    case element: HTMLElement =>
                      element.scrollLeft = element.scrollWidth.toDouble
                    case _ => ()
                  }
                  cb(Right(None))
                }
                None // no finalizer on cancellation
              }
            }),
            domain.lazyFetchGraphFromCoto(start)
          )
        )

      case CloseTraversal(traversalIndex) =>
        (model.closeTraversal(traversalIndex), Seq.empty)

      case Step(traversalIndex, stepIndex, step) =>
        (
          model.step(traversalIndex, stepIndex, step),
          // scroll to the new step
          Seq(
            Cmd(IO.async { cb =>
              IO {
                js.timers.setTimeout(10) {
                  dom.document.getElementById(
                    elementIdOfTraversalStep(traversalIndex, Some(stepIndex))
                  ) match {
                    case element: HTMLElement =>
                      element.scrollIntoView(true)
                    case _ => ()
                  }
                  cb(Right(None))
                }
                None // no finalizer on cancellation
              }
            }),
            domain.lazyFetchGraphFromCoto(step)
          )
        )

      case StepToParent(traversalIndex, parentId) =>
        (model.stepToParent(traversalIndex, parentId, domain.links), Seq.empty)
    }

  def apply(
      model: Model,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(!model.traversals.isEmpty) {
      section(className := "traversals")(
        model.traversals.zipWithIndex.map(
          sectionTraversal(_, domain, dispatch)
        ): _*
      )
    }

  private def sectionTraversal(
      traversal: (Traversal, Int),
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(key := traversal._1.id, className := "traversal header-and-body")(
      header(className := "tools")(
        button(
          className := "close-traversal default",
          onClick := (_ => dispatch(CloseTraversal(traversal._2).toAppMsg))
        )(
          materialSymbol("close")
        )
      ),
      section(className := "body")(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => ()
        )(
          divParents(
            domain.parentsOf(traversal._1.start),
            traversal._2,
            dispatch
          ),
          // traversal start
          domain.cotos.get(traversal._1.start).map(
            divTraversalStep(_, None, traversal, domain, dispatch)
          ),
          // traversal steps
          traversal._1.steps.zipWithIndex.map { case (step, index) =>
            domain.cotos.get(step).map(
              divTraversalStep(_, Some(index), traversal, domain, dispatch)
            )
          }
        )
      )
    )

  private def divParents(
      parents: Seq[(Coto, Link)],
      traversalIndex: Int,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      div(className := "parents")(
        ul(className := "traverse-to-parents")(
          parents.map { case (parent, link) =>
            li(key := link.id.uuid)(
              button(
                className := "parent default",
                onClick := (_ =>
                  dispatch(
                    StepToParent(traversalIndex, parent.id).toAppMsg
                  )
                )
              )(parent.abbreviate)
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
      ),
      id := elementIdOfTraversalStep(traversal._2, stepIndex)
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
          ViewCoto.divClassifiedAs(coto, domain, dispatch)
        ),
        div(className := "body")(
          ViewCoto.divContent(coto, domain, dispatch)
        )
      ),
      ol(className := "sub-cotos")(
        subCotos.map(liSubCoto(_, stepIndex, traversal, domain, dispatch))
      )
    )
  }

  private def elementIdOfTraversalStep(
      traversalIndex: Int,
      stepIndex: Option[Int]
  ): String =
    s"traversal-${traversalIndex}" +
      stepIndex.map(step => s"-step-${step}").getOrElse("-start")

  private def liSubCoto(
      subCoto: (Link, Coto),
      stepIndex: Option[Int],
      traversal: (Traversal, Int),
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    val (link, coto) = subCoto
    val traversed = traversal._1.traversed(stepIndex, coto.id)
    li(key := link.id.uuid, className := "sub")(
      ViewCoto.ulParents(
        domain.parentsOf(coto.id).filter(_._2.id != link.id),
        dispatch
      ),
      article(
        className := optionalClasses(
          Seq(
            ("sub-coto", true),
            ("coto", true),
            ("traversed", traversed)
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
          ViewCoto.divClassifiedAs(coto, domain, dispatch)
        ),
        div(className := "body")(
          // Coto content
          if (traversed) {
            div(className := "content")(
              section(className := "abbreviated-content")(coto.abbreviate)
            )
          } else {
            ViewCoto.divContent(coto, domain, dispatch)
          },
          // Traverse button
          Option.when(!traversed && coto.outgoingLinks > 0) {
            val stepMsg = Step(
              traversal._2,
              stepIndex.map(_ + 1).getOrElse(0),
              coto.id
            ).toAppMsg
            div(className := "traverse")(
              ToolButton(
                classes = "traverse",
                tip = "Traverse",
                tipPlacement = "left",
                symbol = "arrow_downward",
                onClick = (() => dispatch(stepMsg))
              )
            )
          }
        )
      )
    )
  }
}

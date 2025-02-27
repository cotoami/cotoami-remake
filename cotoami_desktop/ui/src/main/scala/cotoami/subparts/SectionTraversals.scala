package cotoami.subparts

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._
import java.time.Instant

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Ito, OrderContext}
import cotoami.repository.Itos
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  Flipped,
  Flipper,
  ScrollArea
}

object SectionTraversals {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def isEmpty: Boolean = traversals.isEmpty

    def openTraversal(start: Id[Coto]): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def step(traversalIndex: Int, stepIndex: Int, step: Id[Coto]): Model =
      this.modify(_.traversals.index(traversalIndex)).using(
        _.step(stepIndex, step)
      )

    def stepToParent(
        traversalIndex: Int,
        parentId: Id[Coto],
        itos: Itos
    ): Model =
      this.modify(_.traversals.index(traversalIndex)).using(
        _.stepToParent(parentId, itos)
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

    def stepToParent(parentId: Id[Coto], itos: Itos): Traversal = {
      val oldStart = start
      this
        .modify(_.start).setTo(parentId)
        .modify(_.steps).using(steps =>
          // if oldStart has no outgoing itos, there's no need to prepend it
          // to the steps because the new start box will contain it as a sub coto.
          if (itos.from(oldStart).isEmpty) {
            steps
          } else {
            oldStart +: steps
          }
        )
    }

    def traversed(stepIndex: Option[Int], subCotoId: Id[Coto]): Boolean =
      (stepIndex match {
        case Some(index) => steps.lift(index + 1).map(_ == subCotoId)
        // For the start coto of the traversal
        case None => steps.headOption.map(_ == subCotoId)
      }).getOrElse(false)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionTraversalsMsg(this)
  }

  object Msg {
    case class OpenTraversal(start: Id[Coto]) extends Msg
    case class CloseTraversal(traversalIndex: Int) extends Msg
    case class Step(traversalIndex: Int, stepIndex: Int, step: Id[Coto])
        extends Msg
    case class StepToParent(traversalIndex: Int, parentId: Id[Coto]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.OpenTraversal(start) =>
        (
          model.openTraversal(start),
          Cmd.Batch(
            Browser.send(
              AppMsg.OpenOrClosePane(PaneStock.PaneName, true)
            ),
            // scroll to the right end on opening a traversal.
            Cmd(IO.async { cb =>
              IO {
                js.timers.setTimeout(10) {
                  dom.document.getElementById(
                    PaneStock.CotoGraphScrollableElementId
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
            context.repo.lazyFetchGraphFrom(start)
          )
        )

      case Msg.CloseTraversal(traversalIndex) =>
        (model.closeTraversal(traversalIndex), Cmd.none)

      case Msg.Step(traversalIndex, stepIndex, step) =>
        (
          model.step(traversalIndex, stepIndex, step),
          // scroll to the new step
          Cmd.Batch(
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
            context.repo.lazyFetchGraphFrom(step)
          )
        )

      case Msg.StepToParent(traversalIndex, parentId) =>
        (
          model.stepToParent(traversalIndex, parentId, context.repo.itos),
          Cmd.none
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(!model.traversals.isEmpty) {
      section(className := "traversals")(
        model.traversals.zipWithIndex.map(sectionTraversal): _*
      )
    }

  private def sectionTraversal(
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(key := traversal._1.id, className := "traversal header-and-body")(
      header(className := "tools")(
        button(
          className := "close-traversal default",
          onClick := (_ => dispatch(Msg.CloseTraversal(traversal._2)))
        )(
          materialSymbol("close")
        )
      ),
      section(className := "body")(
        ScrollArea(
          className = Some("traversal"),
          scrollableClassName = Some("scrollable-traversal")
        )(
          divParents(
            context.repo.parentsOf(traversal._1.start),
            traversal._2
          ),
          // traversal start
          context.repo.cotos.get(traversal._1.start).map(
            divTraversalStep(_, None, traversal)
          ),
          // traversal steps
          traversal._1.steps.zipWithIndex.map { case (step, index) =>
            context.repo.cotos.get(step).map(
              divTraversalStep(_, Some(index), traversal)
            )
          }
        )
      )
    )

  private def divParents(
      parents: Seq[(Coto, Ito)],
      traversalIndex: Int
  )(implicit dispatch: Into[AppMsg] => Unit): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      div(className := "parents")(
        ul(className := "traverse-to-parents")(
          parents.map { case (parent, ito) =>
            li(key := ito.id.uuid)(
              button(
                className := "parent default",
                onClick := (_ =>
                  dispatch(Msg.StepToParent(traversalIndex, parent.id))
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
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val subCotos = context.repo.childrenOf(coto.id)
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
      ViewCoto.article(
        coto,
        dispatch,
        Seq(
          ("step-coto", true),
          ("has-children", !subCotos.isEmpty)
        )
      )(
        ToolbarCoto(coto),
        div(className := "body")(
          ViewCoto.divContent(coto)
        ),
        footer()(
          ViewCoto.divAttributes(coto)
        )
      ),
      Flipper(
        element = "ol",
        className = "sub-cotos",
        flipKey = subCotos.fingerprint
      )(
        subCotos.eachWithOrderContext.map(sub =>
          Flipped(key = sub._1.id.uuid, flipId = sub._1.id.uuid)(
            liSubCoto(sub, stepIndex, traversal)
          ): ReactElement
        )
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
      subCoto: (Ito, Coto, OrderContext),
      stepIndex: Option[Int],
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (ito, coto, order) = subCoto
    val traversed = traversal._1.traversed(stepIndex, coto.id)
    li(className := "sub")(
      ViewCoto.ulParents(
        context.repo.parentsOf(coto.id).filter(_._2.id != ito.id),
        Msg.OpenTraversal(_)
      ),
      ViewCoto.article(
        coto,
        dispatch,
        Seq(
          ("sub-coto", true),
          ("traversed", traversed)
        )
      )(
        ToolbarCoto(coto),
        ToolbarReorder(ito, order),
        header()(
          buttonSubcotoIto(ito)
        ),
        div(className := "body")(
          // Coto content
          if (traversed) {
            div(className := "content")(
              section(className := "abbreviated-content")(coto.abbreviate)
            )
          } else {
            ViewCoto.divContent(coto)
          },
          // Traverse button
          Option.when(
            !traversed && context.repo.itos.anyFrom(coto.id)
          ) {
            val stepMsg = Msg.Step(
              traversal._2,
              stepIndex.map(_ + 1).getOrElse(0),
              coto.id
            )
            div(className := "traverse")(
              toolButton(
                symbol = "arrow_downward",
                tip = Some("Traverse"),
                tipPlacement = "left",
                classes = "traverse",
                onClick = e => {
                  e.stopPropagation()
                  dispatch(stepMsg)
                }
              )
            )
          }
        ),
        footer()(
          ViewCoto.divAttributes(coto)
        )
      )
    )
  }
}

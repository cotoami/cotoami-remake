package cotoami.subparts

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._
import java.time.Instant

import marubinotto.optionalClasses
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.{materialSymbol, toolButton, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Ito, OrderContext, Siblings}
import cotoami.repository.{Itos, Root}

object SectionTraversals {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      traversals: Seq[Traversal] = Seq.empty
  ) {
    def isEmpty: Boolean = traversals.isEmpty

    def openTraversal(start: Id[Coto])(implicit context: Context): Model =
      this.modify(_.traversals).using(_ :+ Traversal(start))

    def step(traversalIndex: Int, stepIndex: Int, step: Id[Coto])(implicit
        context: Context
    ): Model =
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
    def step(stepIndex: Int, step: Id[Coto])(implicit
        context: Context
    ): Traversal =
      this
        .modify(_.steps).using(_.take(stepIndex) :+ step)
        .traverseSingleSuccessors

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

    def lastStep: Id[Coto] = steps.lastOption.getOrElse(start)

    def nextStepIndex: Int = steps.size

    def hasNextStep(stepIndex: Option[Int]): Boolean =
      stepIndex match {
        case None        => !steps.isEmpty
        case Some(index) => steps.size > (index + 1)
      }

    def traversed(stepIndex: Option[Int], subCotoId: Id[Coto]): Boolean =
      (stepIndex match {
        case Some(index) => steps.lift(index + 1).map(_ == subCotoId)
        // For the start coto of the traversal
        case None => steps.headOption.map(_ == subCotoId)
      }).getOrElse(false)

    def traverseSingleSuccessors(implicit context: Context): Traversal = {
      var traversal = this
      var step = lastStep
      var nextIndex = nextStepIndex
      var continue = true
      while (continue) {
        context.repo.itos.onlyOneFrom(step) match {
          case Some(next) => {
            if (traversal.steps.contains(next.targetCotoId))
              continue = false // prevent infinite loop
            else {
              traversal = traversal.step(nextIndex, next.targetCotoId)
              step = next.targetCotoId
              nextIndex = nextIndex + 1
            }
          }
          case None => {
            continue = false
          }
        }
      }
      traversal
    }
  }

  object Traversal {
    def apply(start: Id[Coto])(implicit context: Context): Traversal =
      new Traversal(start).traverseSingleSuccessors
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
            Browser.send(AppMain.Msg.SetPaneStockOpen(true).into),
            Browser.send(AppMsg.Unhighlight),
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
            context.repo.lazyFetchGraphFrom(start),
            // Reload the start coto with its parents.
            Root.fetchCotoDetails(start)
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
                    stepElementId(traversalIndex, Some(stepIndex))
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
          // Reload the parent with its parents (grandparents).
          Root.fetchCotoDetails(parentId)
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
      div(className := "body")(
        context.repo.cotos.get(traversal._1.start).map(
          divTraversalSteps(_, traversal)
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

  private def divTraversalSteps(
      startCoto: Coto,
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    ScrollArea(
      className = Some("traversal-steps"),
      scrollableClassName = Some("scrollable-traversal")
    )(
      divParents(
        context.repo.parentsOf(traversal._1.start),
        traversal._2
      ),
      // traversal start
      divTraversalStep(startCoto, None, traversal),
      // traversal steps
      traversal._1.steps.zipWithIndex.foldLeft(
        (Seq.empty[ReactElement], false)
      ) { case ((divSteps, break), (step, index)) =>
        if (break)
          (divSteps, true)
        else
          context.repo.cotos.get(step)
            .map(divTraversalStep(_, Some(index), traversal))
            .map(divStep => (divSteps :+ divStep, false))
            .getOrElse((divSteps, true)) // the step is missing
      }._1
    )

  private def divTraversalStep(
      coto: Coto,
      stepIndex: Option[Int],
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val subCotos = context.repo.childrenOf(coto.id)
    val subCotosCount = subCotos.map(_.count).getOrElse(0)
    val noVisibleSubCotos = subCotosCount == 0 ||
      // if the single sub-coto is traversed
      (subCotosCount == 1 && traversal._1.hasNextStep(stepIndex))
    div(
      key := s"${stepIndex}-${coto.id.uuid}",
      className := optionalClasses(
        Seq(
          ("traversal-start", stepIndex.isEmpty),
          ("traversal-step", stepIndex.isDefined)
        )
      ),
      id := stepElementId(traversal._2, stepIndex)
    )(
      Option.when(stepIndex.isDefined) {
        div(className := "arrow")(
          materialSymbol("arrow_downward")
        )
      },
      PartsCoto.article(
        coto,
        dispatch,
        Seq(
          ("step-coto", true),
          ("has-children", !noVisibleSubCotos)
        )
      )(
        ToolbarCoto(coto),
        header()(
          PartsCoto.addressRemoteAuthor(coto)
        ),
        div(className := "body")(
          PartsCoto.divContent(coto)
        ),
        footer()(
          PartsCoto.divAttributes(coto)
        )
      ),
      subCotos.map(sectionSubCotos(_, stepIndex, traversal))
    )
  }

  private def stepElementId(
      traversalIndex: Int,
      stepIndex: Option[Int]
  ): String =
    s"traversal-${traversalIndex}" +
      stepIndex.map(step => s"-step-${step}").getOrElse("-start")

  private def sectionSubCotos(
      subCotos: Siblings,
      stepIndex: Option[Int],
      traversal: (Traversal, Int)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(subCotos, "sub-cotos") { case (ito, coto, order) =>
      val traversed = traversal._1.traversed(stepIndex, coto.id)
      div(
        className := optionalClasses(
          Seq(
            ("sub-coto", true),
            ("traversed", traversed)
          )
        )
      )(
        Option.when(!traversed) {
          articleSubCoto(traversal._2, stepIndex, ito, coto, order)
        }
      )
    }

  private def articleSubCoto(
      traversalIndex: Int,
      stepIndex: Option[Int],
      ito: Ito,
      coto: Coto,
      order: OrderContext
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsCoto.article(
      coto,
      dispatch,
      Seq(("sub-coto", true))
    )(
      ToolbarCoto(coto),
      ToolbarReorder(ito, order),
      PartsIto.buttonSubcotoIto(ito),
      PartsCoto.ulParents(
        context.repo.parentsOf(coto.id).filter(_._2.id != ito.id),
        Msg.OpenTraversal(_)
      ),
      div(className := "body")(
        PartsCoto.divContent(coto),
        Option.when(context.repo.itos.anyFrom(coto.id)) {
          val stepMsg = Msg.Step(
            traversalIndex,
            stepIndex.map(_ + 1).getOrElse(0),
            coto.id
          )
          div(className := "traverse")(
            toolButton(
              symbol = "arrow_downward",
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
        PartsCoto.divAttributes(coto)
      )
    )
}

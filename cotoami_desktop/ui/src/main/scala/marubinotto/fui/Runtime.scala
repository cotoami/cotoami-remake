package marubinotto.fui

import scala.collection.mutable.{Map => MutableMap}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.Event

import cats.effect.unsafe.implicits.global
import cats.syntax.parallel._

import slinky.web.ReactDOM

class Runtime[Model, Msg](
    container: Element,
    val program: Program[Model, Msg]
) {
  private val init = program.init(new URL(dom.window.location.href))
  private var state = init._1
  private val subs: MutableMap[String, Option[Sub.Unsubscribe]] = MutableMap()
  private val debounceTimers: MutableMap[String, Int] = MutableMap()

  def dispatch(msg: Msg): Unit = apply(program.update(msg, state))

  def apply(change: (Model, Cmd[Msg])): Unit = {
    val (model, cmd) = change
    state = model

    ReactDOM.render(program.view(model, dispatch), container)
    run(cmd)
    updateSubs(state)
  }

  private def run(cmd: Cmd[Msg]): Unit =
    cmd match {
      case cmd @ Cmd.One(_, Some(Cmd.Debounce(key, delay))) => {
        debounceTimers.get(key).foreach(dom.window.clearTimeout)
        val timer = dom.window.setTimeout(() => runOne(cmd), delay)
        debounceTimers.put(key, timer)
      }
      case cmd: Cmd.One[Msg]          => runOne(cmd)
      case Cmd.Batch(cmds @ _*)       => for (cmd <- cmds) run(cmd)
      case Cmd.Sequence(batches @ _*) => runSequence(batches.toList)
    }

  private def runOne(cmd: Cmd.One[Msg]): Unit =
    cmd.io.unsafeRunAsync {
      case Right(optionMsg) => optionMsg.map(dispatch)
      case Left(e) => throw e // IO should return Right even when it fails
    }

  private def runSequence(batches: List[Cmd.Batch[Msg]]): Unit =
    batches match {
      case Nil => ()
      case head :: tail => {
        head.cmds.map(_.io).parSequence.unsafeRunAsync {
          case Right(optionMsgs) => {
            optionMsgs.foreach(_.map(dispatch))
            runSequence(tail)
          }
          case Left(e) => throw e // IO should return Right even when it fails
        }
      }
    }

  private def updateSubs(model: Model): Unit = {
    val nextSubs = Sub.toMap(program.subscriptions(model))
    val keysToAdd = nextSubs.keySet.diff(subs.keySet)
    val keysToRemove = subs.keySet.diff(nextSubs.keySet)
    keysToAdd.foreach(key => {
      // Register the key first to avoid multiple subscriptions
      subs.update(key, None)

      // subscribe and keep the `unsubscribe` function
      nextSubs.get(key) match {
        case Some(subscribe) => subscribe(dispatch, subs.update(key, _))
        case None            => subs.update(key, None)
      }
    })
    keysToRemove.foreach(key => {
      // remove and unsubscribe
      subs.remove(key).map(_.map(_()))
    })
  }

  def onPushUrl(url: URL): Unit =
    program.onUrlChange.map(_(url)).map(dispatch)

  program.onUrlChange.map(onUrlChange => {
    dom.window.addEventListener(
      "popstate",
      (e: Event) => dispatch(onUrlChange(new URL(dom.window.location.href)))
    )
  })

  apply(init)
}

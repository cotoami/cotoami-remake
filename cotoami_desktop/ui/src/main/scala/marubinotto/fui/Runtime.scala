package marubinotto.fui

import scala.collection.mutable.{Map => MutableMap}
import scala.compiletime.uninitialized
import scala.concurrent.Future
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.Event

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.std.Queue

import slinky.web.ReactDOMClient

class Runtime[Model, Msg] private (
    container: Element,
    val program: Program[Model, Msg],
    dispatcher: Dispatcher[IO],
    queue: Queue[IO, Msg]
) {
  private val reactRoot = ReactDOMClient.createRoot(container)
  private var state: Model = uninitialized
  private val subs: MutableMap[String, () => Future[Unit]] = MutableMap()
  private val debounceTimers: MutableMap[String, Int] = MutableMap()

  def dispatch(msg: Msg): Unit =
    dispatcher.unsafeRunAndForget(queue.offer(msg))

  private def apply(change: (Model, Cmd[Msg])): Unit = {
    val (model, cmd) = change
    state = model
    reactRoot.render(program.view(model, dispatch))
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
      case cmd: Cmd.One[Msg]    => runOne(cmd)
      case Cmd.Batch(cmds @ _*) => for (cmd <- cmds) run(cmd)
    }

  private def runOne(cmd: Cmd.One[Msg]): Unit =
    dispatcher.unsafeRunAndForget(
      cmd.io.flatMap(optionMsg => IO(optionMsg.foreach(msg => dispatch(msg))))
    )

  private def updateSubs(model: Model): Unit = {
    val nextSubs = Sub.toMap(program.subscriptions(model))
    val keysToAdd = nextSubs.keySet.diff(subs.keySet)
    val keysToRemove = subs.keySet.diff(nextSubs.keySet)
    keysToAdd.foreach(key =>
      nextSubs.get(key).foreach(stream =>
        subs.update(
          key,
          dispatcher.unsafeRunCancelable(
            stream.evalMap(queue.offer).compile.drain
          )
        )
      )
    )
    keysToRemove.foreach(key => subs.remove(key).foreach(_()))
  }

  def onPushUrl(url: URL): Unit =
    program.onUrlChange.map(_(url)).map(dispatch)
}

object Runtime {
  def make[Model, Msg](
      container: Element,
      program: Program[Model, Msg],
      dispatcher: Dispatcher[IO]
  ): IO[Runtime[Model, Msg]] =
    for {
      queue <- Queue.unbounded[IO, Msg]
      runtime = new Runtime(container, program, dispatcher, queue)
      init = program.init(new URL(dom.window.location.href))
      _ <- IO {
        runtime.state = init._1
        program.onUrlChange.foreach(onUrlChange =>
          dom.window.addEventListener(
            "popstate",
            (_: Event) => runtime.dispatch(
              onUrlChange(new URL(dom.window.location.href))
            )
          )
        )
        runtime.apply((init._1, Cmd.none))
      }
      _ <- IO(
        dispatcher.unsafeRunAndForget(
          queue.take.flatMap(msg => IO(runtime.apply(program.update(msg, runtime.state)))).foreverM
        )
      )
      _ <- IO(runtime.run(init._2))
    } yield runtime
}

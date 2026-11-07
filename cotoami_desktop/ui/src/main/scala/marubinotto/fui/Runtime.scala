package marubinotto.fui

import scala.collection.mutable.Map as MutableMap
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{Element, Event, URL}

import cats.effect.{Deferred, FiberIO, IO, Ref, Resource}
import cats.effect.std.{Dispatcher, Mutex, Queue, Supervisor}
import cats.syntax.all.*
import slinky.core.facade.ReactElement
import slinky.web.ReactDOMClient

class Runtime[Model, Msg] private (
    render: ReactElement => Unit,
    val program: Program[Model, Msg],
    dispatcher: Dispatcher[IO],
    queue: Queue[IO, Msg],
    supervisor: Supervisor[IO],
    debounceLock: Mutex[IO],
    failure: Deferred[IO, Throwable],
    onError: RuntimeError => IO[Unit],
    private var state: Model
) {
  // Scala.js callbacks and the message loop share a single JS thread.
  private var active = true
  private val subs: MutableMap[String, FiberIO[Unit]] = MutableMap()
  private val debounceTimers: MutableMap[String, (Object, FiberIO[Unit])] =
    MutableMap()

  /** Observe the first fatal loop error. The owner remains responsible for
    * release.
    */
  def awaitFailure: IO[Throwable] = failure.get

  private[fui] def awaitTermination: IO[Unit] =
    awaitFailure.flatMap(error =>
      report(RuntimeError.UpdateLoop(error)) *> IO.raiseError[Unit](error)
    )

  private def report(error: RuntimeError): IO[Unit] =
    IO.defer(onError(error)).handleErrorWith { reportingError =>
      (RuntimeError.log(error) *> IO(
        dom.console.error(
          "Scalafui error handler failed",
          reportingError.toString
        )
      )).handleErrorWith(_ => IO.unit)
    }

  private def messageLoop: IO[Unit] =
    processNextMessage.foreverM.void.handleErrorWith { error =>
      IO.uncancelable(_ => stopAccepting *> failure.complete(error).void)
    }

  def dispatch(msg: Msg): Unit =
    if (active) dispatcher.unsafeRunAndForget(offer(msg))

  private def offer(msg: Msg): IO[Unit] =
    IO.defer(if (active) queue.offer(msg) else IO.unit)

  private def apply(change: (Model, Cmd[Msg])): IO[Unit] = {
    val (model, cmd) = change
    IO {
      state = model
      render(program.view(model, dispatch))
    } *> run(cmd) *> updateSubs(model)
  }

  private def run(cmd: Cmd[Msg]): IO[Unit] = IO.defer {
    if (!active) IO.unit
    else
      cmd match {
        case cmd @ Cmd.One(_, Some(debounce)) => runDebounced(cmd, debounce)
        case cmd: Cmd.One[Msg] => runOne(cmd)
        case Cmd.Batch(cmds*)  => cmds.toList.traverse_(run)
      }
  }

  private def runDebounced(
      cmd: Cmd.One[Msg],
      debounce: Cmd.Debounce
  ): IO[Unit] = IO.uncancelable { _ =>
    val Cmd.Debounce(key, delay) = debounce
    debounceLock.lock.surround {
      for {
        previous <- IO(debounceTimers.remove(key))
        token = new Object()
        fiber <- supervisor.supervise(
          IO.sleep(delay) *>
            fireDebounced(cmd, key, token)
        )
        _ <- IO(debounceTimers.update(key, (token, fiber)))
      } yield previous
    }.flatMap(_.traverse_(_._2.cancel))
  }

  private def fireDebounced(
      cmd: Cmd.One[Msg],
      key: String,
      token: Object
  ): IO[Unit] = debounceLock.lock.surround(IO.uncancelable { _ =>
    IO(debounceTimers.get(key).exists(_._1 eq token)).flatMap {
      case true  => IO(debounceTimers.remove(key)) *> runOne(cmd)
      case false => IO.unit
    }
  })

  private def runOne(cmd: Cmd.One[Msg]): IO[Unit] = IO.defer {
    if (active)
      supervisor.supervise(
        cmd.io.flatMap(_.traverse_(offer))
          .handleErrorWith(error => report(RuntimeError.Command(error)))
      ).void
    else IO.unit
  }

  private def updateSubs(model: Model): IO[Unit] = IO.defer {
    val nextSubs = Sub.toMap(program.subscriptions(model))
    val keysToAdd = nextSubs.keySet.diff(subs.keySet).toList
    val keysToRemove = subs.keySet.diff(nextSubs.keySet).toList
    keysToRemove.traverse_(key =>
      IO(subs.remove(key)).flatMap(_.traverse_(_.cancel))
    ) *>
      keysToAdd.traverse_(key =>
        supervisor.supervise(
          nextSubs(key).evalMap(offer).compile.drain
            .handleErrorWith(error =>
              report(RuntimeError.Subscription(key, error))
            )
        )
          .flatMap(fiber => IO { subs.update(key, fiber) })
      )
  }

  def onPushUrl(url: URL): Unit =
    if (active) program.onUrlChange.foreach(f => dispatch(f(url)))

  private def processNextMessage: IO[Unit] =
    queue.take.flatMap { msg =>
      IO.defer(
        if (active) apply(program.update(msg, state)) else IO.unit
      ) *> IO.cede
    }

  private def stopAccepting: IO[Unit] = IO { active = false }

  private def clearTimers: IO[Unit] =
    debounceLock.lock.surround(IO {
      val pending = debounceTimers.values.toList
      debounceTimers.clear()
      pending
    }).flatMap(_.traverse_(_._2.cancel))

  private def urlListener: Resource[IO, Unit] = program.onUrlChange match {
    case None => Resource.unit[IO]
    case Some(_) =>
      Resource.make(IO {
        val listener: js.Function1[Event, Unit] =
          (_: Event) => if (active) onPushUrl(new URL(dom.window.location.href))
        dom.window.addEventListener("popstate", listener)
        listener
      })(listener =>
        IO(dom.window.removeEventListener("popstate", listener))
      ).void
  }
}

object Runtime {
  def make[Model, Msg](
      container: Element,
      program: Program[Model, Msg],
      onError: RuntimeError => IO[Unit] = RuntimeError.log
  ): Resource[IO, Runtime[Model, Msg]] =
    for {
      root <- Resource.make(IO(ReactDOMClient.createRoot(container)))(root =>
        IO(root.unmount())
      )
      dispatcher <- Dispatcher.sequential[IO]
      supervisor <- Supervisor[IO](await = false)
      debounceLock <- Resource.eval(Mutex[IO])
      queue <- Resource.eval(Queue.unbounded[IO, Msg])
      failure <- Resource.eval(Deferred[IO, Throwable])
      init <- Resource.eval(IO(program.init(new URL(dom.window.location.href))))
      runtime <- Resource.eval(
        IO(
          new Runtime(
            element => root.render(element),
            program,
            dispatcher,
            queue,
            supervisor,
            debounceLock,
            failure,
            onError,
            init._1
          )
        )
      )
      // Finalizers run in reverse acquisition order: close inputs, stop work, unmount.
      loop <- Resource.make(Ref.of[IO, Option[FiberIO[Unit]]](None))(ref =>
        ref.get.flatMap(_.traverse_(_.cancel))
      )
      _ <- Resource.onFinalize(runtime.clearTimers)
      _ <- runtime.urlListener
      _ <- Resource.onFinalize(runtime.stopAccepting)
      _ <- Resource.eval(runtime.apply((init._1, Cmd.none)))
      _ <- Resource.eval(IO.uncancelable { _ =>
        runtime.messageLoop.start.flatMap(fiber => loop.set(Some(fiber)))
      })
      _ <- Resource.eval(runtime.run(init._2))
    } yield runtime
}

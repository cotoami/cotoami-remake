package marubinotto.fui

import cats.effect.{Deferred, IO, Resource}
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.duration.*
import scala.scalajs.js
import marubinotto.react.html.div

class FeatureRuntimeSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def environment = Resource.make(IO {
    val oldWindow = js.Dynamic.global.globalThis.window
    val oldDocument = js.Dynamic.global.globalThis.document
    val env = new TestDOM(
      "<div id='app'></div>",
      js.Dynamic.literal(url = "https://example.test/")
    )
    js.Dynamic.global.globalThis.window = env.window
    js.Dynamic.global.globalThis.document = env.window.document
    (env, oldWindow, oldDocument)
  }) { case (env, window, document) =>
    IO.sleep(20.millis) *> IO {
      env.window.close()
      js.Dynamic.global.globalThis.window = window
      js.Dynamic.global.globalThis.document = document
    }
  }

  private class App(
      command: Int => Cmd[Int],
      subscription: Int => Sub[Int] = _ => Sub.Empty,
      closeOnInit: Boolean = false,
      featureSubscriptions: Boolean = false
  ) {
    val leaf = new Feature[Int, Unit, Int, Int, Unit] {
      override def subscriptions(model: Int, context: Unit): Sub[Int] =
        if (featureSubscriptions) subscription(model) else Sub.Empty
      def init(input: Int, context: Unit) =
        Transition(input, command(input), if (closeOnInit) List(()) else Nil)
      def update(
          msg: Int,
          model: Int,
          context: Unit
      ): Transition[Int, Int, Unit] = Transition(
        model + msg
      )
      def view(model: Int, context: Unit, dispatch: Int => Unit) = div(
        model.toString
      )
    }
    object Content extends FeatureSlots[Unit] {
      val leaf = this.slot(App.this.leaf); val peer = this.slot(App.this.leaf)
    }
    case class Model(content: Content.State = Content.empty)
    enum Msg {
      case ChildEvent(event: Content.Event)
      case Replace
      case Close
      case OpenPeer
      case Barrier(done: Deferred[IO, Model])
    }
    val child = Content.bind[Model, Msg, Nothing](
      Content.policy(),
      _.content,
      (m, s) => m.copy(content = s),
      Msg.ChildEvent.apply,
      { case Msg.ChildEvent(e) => Some(e); case _ => None },
      (_, m) => Transition(m.copy(content = Content.clear(m.content)))
    )
    val program = FeatureProgram[Model, Msg](
      _ => child.init(Model(), Content.leaf, 1),
      (msg, m) =>
        msg match {
          case Msg.Replace       => child.init(m, Content.leaf, 3)
          case Msg.Close         => child.remove(m, Content.leaf)
          case Msg.OpenPeer      => child.init(m, Content.peer, 10)
          case Msg.Barrier(done) => Transition(m, Cmd.exec(done.complete(m)))
          case _ => throw new AssertionError("child message reached parent")
        },
      (_, _) => div("features"),
      subscriptions = model =>
        if (featureSubscriptions) Sub.Empty else Content.leaf.model(model.content).fold[Sub[Msg]](Sub.Empty) { value =>
          // App owns the source and captures this visit's destination.
          val address = model.content
          subscription(value).map(msg =>
            Msg.ChildEvent(Content.leaf.event(address, msg).get)
          )
        }
    ).withFeatures(child).program

    def snapshot(runtime: Runtime[Model, Msg]): IO[Model] =
      Deferred[IO, Model].flatMap(done =>
        IO(runtime.dispatch(Msg.Barrier(done))) *> done.get
      )
  }

  test(
    "replacement leaves commands running, and their eventual results cannot update the new child"
  ) {
    environment.use { _ =>
      for {
        started <- Deferred[IO, Unit]
        finish <- Deferred[IO, Unit]
        completed <- Deferred[IO, Unit]
        app = new App(i =>
          if (i == 1)
            (started.complete(()) *> finish.get *> completed.complete(()).as(
              100
            )).toCmd
          else Cmd.none
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            _ <- started.get
            initial <- app.snapshot(runtime)
            old = app.Msg.ChildEvent(
              app.Content.leaf.event(initial.content, 100).get
            )
            _ <- IO { runtime.dispatch(app.Msg.Replace); runtime.dispatch(old) }
            replacement <- app.snapshot(runtime)
            _ <- IO(
              assert(app.Content.leaf.model(replacement.content).contains(3))
            )
            _ <- finish.complete(()) *> completed.get *> IO.sleep(20.millis)
            after <- app.snapshot(runtime)
            _ <- IO(assert(app.Content.leaf.model(after.content).contains(3)))
            _ <- IO(
              runtime.dispatch(
                app.Msg.ChildEvent(app.Content.leaf.event(after.content, 5).get)
              )
            )
            current <- app.snapshot(runtime)
          } yield assert(app.Content.leaf.model(current.content).contains(8))
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "closing one child ignores its command result while a concurrent child still receives results"
  ) {
    environment.use { _ =>
      for {
        leafStarted <- Deferred[IO, Unit]
        peerStarted <- Deferred[IO, Unit]
        finishLeaf <- Deferred[IO, Unit]
        finishPeer <- Deferred[IO, Unit]
        leafDone <- Deferred[IO, Unit]
        peerDone <- Deferred[IO, Unit]
        app = new App(i =>
          (if (i == 1)
             leafStarted.complete(()) *> finishLeaf.get *> leafDone.complete(
               ()
             ).as(100)
           else
             peerStarted.complete(()) *> finishPeer.get *> peerDone.complete(
               ()
             ).as(5)).toCmd
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            _ <- leafStarted.get
            _ <- IO(runtime.dispatch(app.Msg.OpenPeer))
            _ <- peerStarted.get
            _ <- IO(runtime.dispatch(app.Msg.Close))
            _ <- app.snapshot(runtime)
            _ <- finishLeaf.complete(()) *> finishPeer.complete(())
            _ <- leafDone.get *> peerDone.get *> IO.sleep(20.millis)
            current <- app.snapshot(runtime)
          } yield {
            assert(app.Content.leaf.model(current.content).isEmpty)
            assert(app.Content.peer.model(current.content).contains(15))
          }
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test("removing a child does not cancel its pending debounce") {
    environment.use { _ =>
      for {
        fired <- Deferred[IO, Unit]
        app = new App(_ =>
          Cmd.exec(fired.complete(())).debounce("search", 50.millis)
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          IO(runtime.dispatch(app.Msg.Close)) *> app.snapshot(
            runtime
          ) *> fired.get
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "initial outputs can close children without suppressing their generated commands"
  ) {
    environment.use { _ =>
      for {
        completed <- Deferred[IO, Unit]
        app = new App(_ => Cmd.exec(completed.complete(())), closeOnInit = true)
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            _ <- completed.get
            state <- app.snapshot(runtime)
          } yield assert(app.Content.leaf.model(state.content).isEmpty)
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "App starts and stops subscriptions based on child state and changes IDs when rebinding"
  ) {
    environment.use { _ =>
      for {
        started <- Queue.unbounded[IO, Int]
        stopped <- Queue.unbounded[IO, Int]
        app = new App(
          _ => Cmd.none,
          i =>
            Sub.Impl(
              s"app-source-$i",
              Stream.bracket(started.offer(i))(_ => stopped.offer(i)).flatMap(
                _ => Stream.never[IO]
              )
            )
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            first <- started.take
            _ <- IO(runtime.dispatch(app.Msg.Replace))
            _ <- app.snapshot(runtime)
            ended <- stopped.take
            second <- started.take
            _ <- IO(runtime.dispatch(app.Msg.Close))
            _ <- app.snapshot(runtime)
            closed <- stopped.take
            _ <- IO(assert((first, ended, second, closed) == (1, 1, 3, 3)))
          } yield ()
        }
        extra <- stopped.tryTake
      } yield assert(extra.isEmpty)
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "retaining a subscription ID retains its captured destination, so stale messages are ignored"
  ) {
    environment.use { _ =>
      for {
        emit <- Queue.unbounded[IO, Int]
        started <- Deferred[IO, Unit]
        app = new App(
          _ => Cmd.none,
          _ =>
            Sub.Impl(
              "same",
              Stream.eval(
                started.complete(())
              ).drain ++ Stream.fromQueueUnterminated(emit)
            )
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            _ <- started.get
            _ <- IO(runtime.dispatch(app.Msg.Replace))
            _ <- app.snapshot(runtime)
            _ <- emit.offer(100) *> IO.sleep(20.millis)
            current <- app.snapshot(runtime)
          } yield assert(app.Content.leaf.model(current.content).contains(3))
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test("Feature subscriptions retain streams on update, isolate siblings and reconnect on replacement") {
    environment.use { _ =>
      for {
        started <- Queue.unbounded[IO, Int]
        stopped <- Queue.unbounded[IO, Int]
        callbacks <- Queue.unbounded[IO, Int => Unit]
        app = new App(
          _ => Cmd.none,
          value => Sub.fromResource[Int]("same") { emit =>
            Resource.make(started.offer(value) *> callbacks.offer(emit))(
              _ => stopped.offer(value)
            )
          },
          featureSubscriptions = true
        )
        _ <- Browser.mount(dom.document.getElementById("app"), app.program).use { runtime =>
          for {
            first <- started.take
            old <- callbacks.take
            _ <- IO(old(2))
            updated <- app.snapshot(runtime)
            // Callback dispatch is asynchronous; await delivery via snapshots.
            _ <- IO.sleep(20.millis)
            delivered <- app.snapshot(runtime)
            unchanged <- started.tryTake
            stale = app.Msg.ChildEvent(app.Content.leaf.event(delivered.content, 100).get)
            _ <- IO(runtime.dispatch(app.Msg.OpenPeer))
            peer <- started.take
            peerEmit <- callbacks.take
            _ <- IO(runtime.dispatch(app.Msg.Replace))
            ended <- stopped.take
            replacement <- started.take
            fresh <- callbacks.take
            _ <- IO { runtime.dispatch(stale); fresh(4); peerEmit(5) }
            _ <- IO.sleep(20.millis)
            current <- app.snapshot(runtime)
            _ <- IO(runtime.dispatch(app.Msg.Close))
            closed <- stopped.take
          } yield {
            assert(first == 1 && peer == 10 && replacement == 3)
            assert(ended == 1 && closed == 3 && unchanged.isEmpty)
            assert(app.Content.leaf.model(updated.content).isDefined)
            assert(app.Content.leaf.model(delivered.content).contains(3))
            assert(app.Content.leaf.model(current.content).contains(7))
            assert(app.Content.peer.model(current.content).contains(15))
          }
        }
        remaining <- stopped.take
      } yield assert(remaining == 10)
    }.timeout(5.seconds).unsafeToFuture()
  }

  test("debounce waits with the same local key survive sibling opening and instance replacement") {
    environment.use { _ =>
      for {
        fired <- Queue.unbounded[IO, Int]
        app = new App(i => Cmd.exec(fired.offer(i)).debounce("same", 100.millis))
        _ <- Browser.mount(dom.document.getElementById("app"), app.program).use { runtime =>
          for {
            _ <- IO { runtime.dispatch(app.Msg.OpenPeer); runtime.dispatch(app.Msg.Replace) }
            _ <- app.snapshot(runtime)
            a <- fired.take
            b <- fired.take
            c <- fired.take
          } yield assert(List(a, b, c).sorted == List(1, 3, 10))
        }
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "application release still cancels commands after their originating child is removed"
  ) {
    environment.use { _ =>
      for {
        started <- Deferred[IO, Unit]
        stopped <- Deferred[IO, Unit]
        app = new App(_ =>
          Cmd.exec(
            started.complete(()) *> IO.never.guarantee(
              stopped.complete(()).void
            )
          )
        )
        _ <- Browser.mount(
          dom.document.getElementById("app"),
          app.program
        ).use { runtime =>
          for {
            _ <- started.get
            _ <- IO(runtime.dispatch(app.Msg.Close))
            _ <- app.snapshot(runtime)
            ended <- stopped.tryGet
          } yield assert(ended.isEmpty)
        }
        _ <- stopped.get
      } yield succeed
    }.timeout(5.seconds).unsafeToFuture()
  }
}

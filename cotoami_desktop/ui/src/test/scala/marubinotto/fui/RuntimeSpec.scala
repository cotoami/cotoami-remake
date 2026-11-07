package marubinotto.fui

import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import cats.effect.{Deferred, IO, Resource}
import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import slinky.web.html.div

@js.native
@JSImport("jsdom", "JSDOM")
class TestDOM(
    @scala.annotation.unused html: String,
    @scala.annotation.unused options: js.Object
) extends js.Object {
  val window: js.Dynamic = js.native
}

class RuntimeSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def environment = Resource.make(IO {
    val previousWindow = js.Dynamic.global.globalThis.window
    val previousDocument = js.Dynamic.global.globalThis.document
    val env = new TestDOM(
      "<div id='app'></div>",
      js.Dynamic.literal(url = "https://example.test/")
    )
    js.Dynamic.global.globalThis.window = env.window
    js.Dynamic.global.globalThis.document = env.window.document
    (env, previousWindow, previousDocument)
  }) { case (env, previousWindow, previousDocument) =>
    IO.sleep(20.millis) *> IO {
      env.window.close()
      js.Dynamic.global.globalThis.window = previousWindow
      js.Dynamic.global.globalThis.document = previousDocument
    }
  }

  test(
    "reentrant dispatch is queued and updates are processed without nesting"
  ) {
    environment.use { _ =>
      (for {
        done <- Resource.eval(Deferred[IO, Unit])
        events = scala.collection.mutable.ArrayBuffer.empty[String]
        runtimeRef = new Array[Runtime[Int, Int]](1)
        runtime <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ => (0, Cmd.none),
            (model, _) => div(model.toString),
            (msg, model) => {
              events += s"start-$msg-$model"
              if (msg == 1) runtimeRef(0).dispatch(2)
              events += s"end-$msg"
              (
                model + msg,
                if (msg == 2) Cmd(done.complete(()).as(None)) else Cmd.none
              )
            }
          )
        )
        _ <- Resource.eval(IO {
          runtimeRef(0) = runtime; runtime.dispatch(1)
        })
        _ <- Resource.eval(done.get.timeout(2.seconds))
      } yield assert(
        events.toList == List("start-1-0", "end-1", "start-2-1", "end-2")
      )).use(result => IO.pure(result))
    }.unsafeToFuture()
  }

  test("dispatch from one callback preserves message order") {
    environment.use { _ =>
      for {
        done <- Deferred[IO, List[Int]]
        program = Program[List[Int], Int](
          _ => (Nil, Cmd.none),
          (_, _) => div("mounted"),
          (msg, model) => {
            val next = msg :: model
            (next, if (next.size == 1000) Cmd.exec(done.complete(next.reverse)) else Cmd.none)
          }
        )
        received <- Browser.mount(dom.document.getElementById("app"), program).use { runtime =>
          IO((1 to 1000).foreach(runtime.dispatch)) *> done.get
        }
      } yield assert(received == (1 to 1000).toList)
    }.timeout(3.seconds).unsafeToFuture()
  }

  test("initial view and subscription evaluation precede the initial command") {
    environment.use { _ =>
      (for {
        done <- Resource.eval(Deferred[IO, Unit])
        events = scala.collection.mutable.ArrayBuffer.empty[String]
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ => (7, Cmd(IO { events += "command"; Some(1) })),
            (model, _) => { events += s"view-$model"; div(model.toString) },
            (_, model) => (model + 1, Cmd(done.complete(()).as(None))),
            _ => { events += "subscriptions"; Sub.Empty }
          )
        )
        _ <- Resource.eval(done.get.timeout(2.seconds))
      } yield assert(
        events.take(4).toList == List(
          "view-7",
          "subscriptions",
          "command",
          "view-8"
        )
      )).use(result => IO.pure(result))
    }.unsafeToFuture()
  }

  test(
    "a burst of messages is retained and debounced commands keep only the last value"
  ) {
    environment.use { _ =>
      (for {
        done <- Resource.eval(Deferred[IO, (Int, Int)])
        runtime <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ => (0, Cmd.none),
            (model, _) => div(model.toString),
            (msg, model) =>
              if (msg < 0) (model, Cmd(done.complete((model, msg)).as(None)))
              else
                (model + 1, Browser.send(-msg).debounce("burst", 30.millis))
          )
        )
        _ <- Resource.eval(IO((1 to 100).foreach(runtime.dispatch)))
        count <- Resource.eval(done.get.timeout(3.seconds))
      } yield assert(count == (100, -100))).use(result => IO.pure(result))
    }.unsafeToFuture()
  }

  test("pushUrl and popstate dispatch URL changes") {
    environment.use { env =>
      (for {
        pushed <- Resource.eval(Deferred[IO, String])
        popped <- Resource.eval(Deferred[IO, String])
        runtime <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, String](
            _ => (0, Cmd.none),
            (model, _) => div(model.toString),
            (msg, model) =>
              (
                model + 1,
                Cmd(
                  (if (model == 0) pushed else popped).complete(msg).as(None)
                )
              ),
            onUrlChange = Some(_.pathname)
          )
        )
        _ <- Resource.eval(
          IO(runtime.onPushUrl(new dom.URL("https://example.test/pushed")))
        )
        first <- Resource.eval(pushed.get.timeout(2.seconds))
        _ <- Resource.eval(IO {
          dom.window.history.pushState(null, "", "/popped")
          env._1.window.dispatchEvent(
            js.Dynamic.newInstance(env._1.window.PopStateEvent)("popstate")
          )
        })
        second <- Resource.eval(popped.get.timeout(2.seconds))
      } yield assert(first == "/pushed" && second == "/popped")).use(result =>
        IO.pure(result)
      )
    }.unsafeToFuture()
  }

  test(
    "subscriptions retain identity, cancel on removal, and restart when re-added"
  ) {
    environment.use { _ =>
      (for {
        starts <- Resource.eval(cats.effect.Ref.of[IO, Int](0))
        started <- Resource.eval(cats.effect.std.Queue.unbounded[IO, Int])
        stopped <- Resource.eval(cats.effect.std.Queue.unbounded[IO, Unit])
        updated <- Resource.eval(cats.effect.std.Queue.unbounded[IO, Unit])
        sub = Sub.fromResource[Boolean]("external") { _ =>
          Resource.make(starts.updateAndGet(_ + 1).flatMap(started.offer))(
            _ => stopped.offer(())
          )
        }
        runtime <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Boolean, Boolean](
            _ => (true, Cmd.none),
            (model, _) => div(model.toString),
            (msg, _) => (msg, Cmd(updated.offer(()).as(None))),
            active => if (active) sub else Sub.Empty
          )
        )
        first <- Resource.eval(started.take.timeout(2.seconds))
        _ <- Resource.eval(IO(runtime.dispatch(true)))
        _ <- Resource.eval(updated.take.timeout(2.seconds))
        unchanged <- Resource.eval(starts.get)
        _ <- Resource.eval(IO(runtime.dispatch(false)))
        _ <- Resource.eval(updated.take.timeout(2.seconds))
        _ <- Resource.eval(stopped.take.timeout(2.seconds))
        _ <- Resource.eval(IO(runtime.dispatch(true)))
        _ <- Resource.eval(updated.take.timeout(2.seconds))
        second <- Resource.eval(started.take.timeout(2.seconds))
        _ <- Resource.eval(IO(runtime.dispatch(false)))
        _ <- Resource.eval(stopped.take.timeout(2.seconds))
      } yield assert(first == 1 && unchanged == 1 && second == 2)).use(
        result => IO.pure(result)
      )
    }.unsafeToFuture()
  }

  test("subscription re-addition waits for the previous cleanup to finish") {
    environment.use { _ =>
      for {
        events <- cats.effect.Ref.of[IO, List[String]](Nil)
        starts <- cats.effect.Ref.of[IO, Int](0)
        firstStarted <- Deferred[IO, Unit]
        secondStarted <- Deferred[IO, Unit]
        cleanupStarted <- Deferred[IO, Unit]
        allowCleanup <- Deferred[IO, Unit]
        sub = Sub.fromResource[Boolean]("external") { _ =>
          Resource.make(
            starts.updateAndGet(_ + 1).flatMap { generation =>
              (events.update(_ :+ s"start-$generation") *>
                (if (generation == 1) firstStarted
                 else secondStarted).complete(()))
                .as(generation)
            }
          ) { generation =>
            events.update(_ :+ s"cleanup-start-$generation") *>
              cleanupStarted.complete(()) *> allowCleanup.get *>
              events.update(_ :+ s"cleanup-end-$generation")
          }.void
        }
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Boolean, Boolean](
            _ => (true, Cmd.none),
            (model, _) => div(model.toString),
            (msg, _) => (msg, Cmd.none),
            active => if (active) sub else Sub.Empty
          )
        ).use { runtime =>
          (for {
            _ <- firstStarted.get
            _ <- IO { runtime.dispatch(false); runtime.dispatch(true) }
            _ <- cleanupStarted.get
            before <- events.get
            _ <- IO(assert(before == List("start-1", "cleanup-start-1")))
            _ <- allowCleanup.complete(())
            _ <- secondStarted.get
            after <- events.get
            _ <- IO(
              assert(
                after == List(
                  "start-1",
                  "cleanup-start-1",
                  "cleanup-end-1",
                  "start-2"
                )
              )
            )
          } yield ()).timeout(2.seconds)
            .guarantee(allowCleanup.complete(()).void)
        }
      } yield succeed
    }.unsafeToFuture()
  }

  test("batch commands run independently and both results reach update") {
    environment.use { _ =>
      for {
        slowStarted <- Deferred[IO, Unit]
        allowSlow <- Deferred[IO, Unit]
        fastUpdated <- Deferred[IO, Unit]
        slowUpdated <- Deferred[IO, Unit]
        updates = scala.collection.mutable.ArrayBuffer.empty[String]
        slow = Cmd[String](
          slowStarted.complete(()) *> allowSlow.get.as(Some("slow"))
        )
        fast = Cmd[String](slowStarted.get.as(Some("fast")))
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, String](
            _ => (0, Cmd.Batch(slow, fast)),
            (model, _) => div(model.toString),
            (msg, model) => {
              updates += msg
              val updated = if (msg == "fast") fastUpdated else slowUpdated
              (model + 1, Cmd(updated.complete(()).as(None)))
            }
          )
        ).use { _ =>
          (for {
            _ <- fastUpdated.get
            _ <- IO(assert(updates.toList == List("fast")))
            _ <- allowSlow.complete(())
            _ <- slowUpdated.get
            _ <- IO(assert(updates.toList == List("fast", "slow")))
          } yield ()).timeout(2.seconds)
        }
      } yield succeed
    }.unsafeToFuture()
  }

  test(
    "normal, failed and canceled use await command and subscription cleanup exactly once"
  ) {
    environment.use { _ =>
      List("normal", "failed", "canceled").traverse_ { mode =>
        for {
          commandStarted <- Deferred[IO, Unit]
          subStarted <- Deferred[IO, Unit]
          cleanups <- cats.effect.Ref.of[IO, List[String]](Nil)
          command = Cmd[Unit](
            (commandStarted.complete(()) *> IO.never[Option[Unit]])
              .guarantee(
                IO.sleep(10.millis) *> cleanups.update("command" :: _)
              )
          )
          sub = Sub.fromResource[Unit]("cleanup") { _ =>
            Resource.make(subStarted.complete(()))(_ =>
              IO.sleep(10.millis) *> cleanups.update("sub" :: _)
            ).void
          }
          resource = Runtime.make(
            dom.document.getElementById("app"),
            Program[Int, Unit](
              _ => (0, command),
              (model, _) => div(model.toString),
              (_, model) => (model, Cmd.none),
              _ => sub
            )
          )
          ready = commandStarted.get *> subStarted.get
          _ <- mode match {
            case "normal" => resource.use(_ => ready)
            case "failed" =>
              resource.use(_ =>
                ready *> IO.raiseError[Unit](new Exception("use failed"))
              )
                .attempt.flatMap(result => IO(assert(result.isLeft)))
            case _ =>
              for {
                fiber <- resource.use(_ => ready *> IO.never).start
                _ <- ready
                _ <- fiber.cancel
              } yield ()
          }
          result <- cleanups.get
          _ <- IO {
            assert(result.sorted == List("command", "sub"))
            assert(dom.document.getElementById("app").childNodes.length == 0)
          }
        } yield ()
      }.as(succeed)
    }.timeout(5.seconds).unsafeToFuture()
  }

  test(
    "Batch emits independently and debounce does not cancel an already started command"
  ) {
    environment.use { _ =>
      for {
        started <- Deferred[IO, Unit]
        finish <- Deferred[IO, Unit]
        seen <- cats.effect.std.Queue.unbounded[IO, Int]
        canceled <- cats.effect.Ref.of[IO, Boolean](false)
        slow = (started.complete(()) *> finish.get.as(1))
          .onCancel(canceled.set(true)).toCmd.debounce("search", 1.millis)
        program = Program[Int, Int](
          _ => (0, Cmd.Batch(slow, IO.pure(2).toCmd) ++ Cmd.none),
          (model, _) => div(model.toString),
          (msg, model) =>
            if (msg == 0)
              (model, IO.pure(3).toCmd.debounce("search", 1.millis))
            else (model, Cmd.exec(seen.offer(msg)))
        )
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          program
        ).use { runtime =>
          for {
            _ <- started.get
            immediate <- seen.take
            _ <- IO(runtime.dispatch(0))
            replacement <- seen.take
            _ <- finish.complete(())
            original <- seen.take
            wasCanceled <- canceled.get
          } yield {
            assert(List(immediate, replacement, original) == List(2, 3, 1))
            assert(!wasCanceled)
          }
        }
      } yield succeed
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "debounce keys are independent and non-positive delays run without waiting"
  ) {
    environment.use { _ =>
      for {
        seen <- cats.effect.std.Queue.unbounded[IO, Int]
        program = Program[Int, Int](
          _ =>
            (
              0,
              Cmd.Batch(
                IO.pure(1).toCmd.debounce("a", 50.millis),
                IO.pure(2).toCmd.debounce("a", 50.millis),
                IO.pure(3).toCmd.debounce("b", 50.millis),
                IO.pure(4).toCmd.debounce("zero", Duration.Zero),
                IO.pure(5).toCmd.debounce("negative", (-1).second)
              )
            ),
          (model, _) => div(model.toString),
          (msg, model) => (model, Cmd.exec(seen.offer(msg)))
        )
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          program
        ).use { _ =>
          for {
            values <- List.fill(4)(seen.take).sequence
            _ <- IO.sleep(80.millis)
            extra <- seen.tryTake
          } yield {
            assert(values.sorted == List(2, 3, 4, 5))
            assert(extra.isEmpty)
          }
        }
      } yield succeed
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "release clears pending debounce and ignores retained dispatch callbacks"
  ) {
    environment.use { _ =>
      for {
        executions <- cats.effect.Ref.of[IO, Int](0)
        updates = scala.collection.mutable.ArrayBuffer.empty[Int]
        runtime <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ =>
              (
                0,
                Cmd(executions.update(_ + 1).as(None)).debounce(
                  "pending",
                  100.millis
                )
              ),
            (model, _) => div(model.toString),
            (msg, model) => { updates += msg; (model, Cmd.none) }
          )
        ).use(IO.pure)
        _ <- IO(runtime.dispatch(1))
        _ <- IO.sleep(150.millis)
        count <- executions.get
      } yield assert(count == 0 && updates.isEmpty)
    }.unsafeToFuture()
  }

  test(
    "URL registrations are removed and another independently owned Runtime survives"
  ) {
    environment.use { env =>
      for {
        seen <- cats.effect.std.Queue.unbounded[IO, String]
        conversions = scala.collection.mutable.ArrayBuffer.empty[String]
        other <- IO {
          val element = dom.document.createElement("div")
          dom.document.body.appendChild(element)
          element
        }
        program = (name: String) =>
          Program[Int, String](
            _ => (0, Cmd.none),
            (model, _) => div(model.toString),
            (msg, model) => (model, Cmd(seen.offer(msg).as(None))),
            onUrlChange = Some(url => {
              conversions += name; name + url.pathname
            })
          )
        _ <- Browser.mount(other, program("live")).use { _ =>
          Browser.mount(
            dom.document.getElementById("app"),
            program("closed")
          )
            .use(_ => IO.unit) *>
            Browser.pushUrl("/push") *>
            seen.take.flatMap(msg => IO(assert(msg == "live/push"))) *>
            IO(
              env._1.window.dispatchEvent(
                js.Dynamic.newInstance(env._1.window.PopStateEvent)(
                  "popstate"
                )
              )
            ) *>
            seen.take.flatMap(msg => IO(assert(msg == "live/push")))
        }
        _ <- Browser.pushUrl("/after")
      } yield assert(conversions.toList == List("live", "live"))
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "startup failures unmount the root and unregister popstate before remounting"
  ) {
    environment.use { env =>
      val stages = List("init", "view", "subscriptions")
      stages.traverse_ { stage =>
        val conversions = scala.collection.mutable.ArrayBuffer.empty[String]
        val failure = new Exception(stage)
        val program = Program[Int, Unit](
          _ => if (stage == "init") throw failure else (0, Cmd.none),
          (model, _) =>
            if (stage == "view") throw failure else div(model.toString),
          (_, model) => (model, Cmd.none),
          _ => if (stage == "subscriptions") throw failure else Sub.Empty,
          Some(_ => { conversions += "url"; () })
        )
        for {
          result <- Runtime.make(
            dom.document.getElementById("app"),
            program
          ).use(_ => IO.unit).attempt
          _ <- IO {
            env._1.window.dispatchEvent(
              js.Dynamic.newInstance(env._1.window.PopStateEvent)("popstate")
            )
            assert(result == Left(failure))
            assert(conversions.isEmpty)
            assert(dom.document.getElementById("app").childNodes.length == 0)
          }
          _ <- Runtime.make(
            dom.document.getElementById("app"),
            program.copy[Int, Unit](
              init = _ => (0, Cmd.none),
              view = (_, _) => div("remounted"),
              subscriptions = _ => Sub.Empty
            )
          ).use(_ => IO.unit)
        } yield ()
      }.as(succeed)
    }.unsafeToFuture()
  }

  test(
    "a failing listener finalizer still cancels subscriptions and unmounts React"
  ) {
    environment.use { env =>
      for {
        started <- Deferred[IO, Unit]
        cleaned <- Deferred[IO, Unit]
        original <- IO(env._1.window.removeEventListener)
        result <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Unit](
            _ => (0, Cmd.none),
            (_, _) => div("mounted"),
            (_, model) => (model, Cmd.none),
            _ =>
              Sub.fromResource[Unit]("cleanup")(_ =>
                Resource.make(started.complete(()))(_ =>
                  cleaned.complete(()).void
                ).void
              ),
            Some(_ => ())
          )
        ).use { _ =>
          started.get *> IO.sleep(30.millis) *> IO {
            assert(
              dom.document.getElementById("app").textContent == "mounted"
            )
            env._1.window.removeEventListener = (
                (
                    name: String,
                    listener: js.Any
                ) => {
                  original.call(env._1.window, name, listener)
                  throw new RuntimeException("remove failed")
                }
            ): js.Function2[String, js.Any, Unit]
          }
        }.attempt.guarantee(IO {
          env._1.window.removeEventListener = original
        })
        cleanup <- cleaned.tryGet
      } yield {
        assert(result.isLeft)
        assert(cleanup.contains(()))
        assert(dom.document.getElementById("app").childNodes.length == 0)
      }
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "command and subscription failures report once while other work continues"
  ) {
    environment.use { _ =>
      for {
        errors <- cats.effect.std.Queue.unbounded[IO, RuntimeError]
        started <- cats.effect.Ref.of[IO, Int](0)
        updates <- cats.effect.std.Queue.unbounded[IO, Int]
        commandError = new Exception("command")
        subError = new Exception("subscription")
        _ <- Runtime.make(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ =>
              (
                0,
                Cmd.Batch(
                  Cmd.exec(IO.raiseError[Unit](commandError)),
                  Browser.send(1)
                )
              ),
            (model, _) => div(model.toString),
            (msg, model) => (model + 1, Cmd.exec(updates.offer(msg))),
            _ =>
              Sub.Impl(
                "failed",
                fs2.Stream.eval(
                  started.update(_ + 1) *>
                    IO.raiseError[Int](subError)
                )
              ).combine(Sub.Impl("live", fs2.Stream.emit(2)))
          ),
          errors.offer
        ).use { runtime =>
          for {
            first <- errors.take
            second <- errors.take
            a <- updates.take
            b <- updates.take
            _ <- IO(runtime.dispatch(3))
            c <- updates.take
            count <- started.get
            extra <- errors.tryTake
            _ <- IO {
              assert(
                Set(first, second) == Set(
                  RuntimeError.Command(commandError),
                  RuntimeError.Subscription("failed", subError)
                )
              )
              assert(Set(a, b) == Set(1, 2) && c == 3)
              assert(count == 1 && extra.isEmpty)
            }
          } yield ()
        }
      } yield succeed
    }.timeout(3.seconds).unsafeToFuture()
  }

  test("mount exposes dispatch and fatal failures without interrupting use") {
    environment.use { _ =>
      List("update", "view", "subscriptions").traverse_ { stage =>
        val failure = new Exception(stage)
        val seen = scala.collection.mutable.ArrayBuffer.empty[Int]
        Browser.mount(
          dom.document.getElementById("app"),
          Program[Int, Int](
            _ => (0, Cmd.none),
            (model, _) =>
              if (model == 1 && stage == "view") throw failure
              else div(model.toString),
            (msg, _) => {
              seen += msg
              if (stage == "update") throw failure
              (msg, Cmd.none)
            },
            model =>
              if (model == 1 && stage == "subscriptions") throw failure
              else Sub.Empty
          )
        ).use { runtime =>
          for {
            _ <- IO(runtime.dispatch(1))
            error <- runtime.awaitFailure
            _ <- IO(runtime.dispatch(2))
            again <- runtime.awaitFailure
            _ <- IO.cede
            _ <- IO(
              assert(
                (error eq failure) && (again eq failure) && seen.toList == List(
                  1
                )
              )
            )
          } yield ()
        }
      }.as(succeed)
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "reporter exceptions do not prevent other commands or fatal resource release"
  ) {
    environment.use { _ =>
      List(true, false).traverse_ { synchronous =>
        for {
          attempted <- Deferred[IO, Unit]
          continued <- Deferred[IO, Unit]
          reporterError = new Exception("reporter")
          reporter = (_: RuntimeError) => {
            attempted.complete(()).unsafeRunAndForget()
            if (synchronous) throw reporterError
            else IO.raiseError[Unit](reporterError)
          }
          _ <- Runtime.make(
            dom.document.getElementById("app"),
            Program[Int, Unit](
              _ =>
                (
                  0,
                  Cmd.Batch(
                    Cmd.exec(IO.raiseError[Unit](new Exception("command"))),
                    Cmd.exec(attempted.get *> continued.complete(()))
                  )
                ),
              (_, _) => div("mounted"),
              (_, model) => (model, Cmd.none)
            ),
            reporter
          ).use(_ => continued.get)
          fatal = new Exception("fatal")
          result <- Browser.run(
            dom.document.getElementById("app"),
            Program[Int, Unit](
              _ => (0, Browser.send(())),
              (_, _) => div("mounted"),
              (_, _) => throw fatal
            ),
            reporter
          ).attempt
          _ <- IO {
            assert(result == Left(fatal))
            assert(dom.document.getElementById("app").childNodes.length == 0)
          }
        } yield ()
      }.as(succeed)
    }.timeout(3.seconds).unsafeToFuture()
  }

  test(
    "Browser.run releases work and URL listeners on failure and cancellation"
  ) {
    environment.use { env =>
      List(true, false).traverse_ { fail =>
        for {
          commandStarted <- Deferred[IO, Unit]
          subStarted <- Deferred[IO, Unit]
          trigger <- Deferred[IO, Unit]
          cleaned <- cats.effect.Ref.of[IO, Set[String]](Set.empty)
          reports <- cats.effect.Ref.of[IO, List[RuntimeError]](Nil)
          timerRan <- cats.effect.Ref.of[IO, Boolean](false)
          failure = new Exception("update")
          conversions = scala.collection.mutable.ArrayBuffer.empty[String]
          program = Program[Int, Unit](
            _ =>
              (
                0,
                Cmd.Batch(
                  Cmd.exec(
                    (commandStarted.complete(()) *> IO.never[Unit])
                      .guarantee(cleaned.update(_ + "command"))
                  ),
                  Cmd.exec(timerRan.set(true)).debounce("pending", 1.hour),
                  (commandStarted.get *> subStarted.get *> trigger.get).toCmd
                )
              ),
            (_, _) => div("mounted"),
            (_, _) => throw failure,
            _ =>
              Sub.fromResource[Unit]("live")(_ =>
                Resource.make(subStarted.complete(()))(_ =>
                  cleaned.update(_ + "subscription")
                ).void
              ),
            Some(_ => { conversions += "url"; () })
          )
          fiber <- Browser.run(
            dom.document.getElementById("app"),
            program,
            error => reports.update(_ :+ error)
          ).attempt.start
          _ <- commandStarted.get *> subStarted.get
          _ <- if (fail) trigger.complete(()).void else fiber.cancel
          outcome <- fiber.join
          result <-
            if (fail) outcome match {
              case cats.effect.Outcome.Succeeded(value) => value
              case other =>
                IO.raiseError(new Exception(s"Unexpected outcome: $other"))
            }
            else IO.pure(Right(()): Either[Throwable, Unit])
          releases <- cleaned.get
          errors <- reports.get
          ran <- timerRan.get
          _ <- Browser.pushUrl("/after")
          _ <- IO {
            env._1.window.dispatchEvent(
              js.Dynamic.newInstance(env._1.window.PopStateEvent)("popstate")
            )
            assert(releases == Set("command", "subscription"))
            assert(!ran && conversions.isEmpty)
            assert(dom.document.getElementById("app").childNodes.length == 0)
            assert(
              if (fail)
                result == Left(failure) && errors == List(
                  RuntimeError.UpdateLoop(failure)
                )
              else errors.isEmpty
            )
          }
        } yield ()
      }.as(succeed)
    }.timeout(3.seconds).unsafeToFuture()
  }

}

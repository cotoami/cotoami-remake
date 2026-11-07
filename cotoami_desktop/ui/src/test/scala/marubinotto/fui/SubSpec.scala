package marubinotto.fui

import scala.concurrent.duration.*
import scala.scalajs.js
import cats.effect.{Deferred, IO, Ref, Resource}
import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite

class SubSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def stream[A](sub: Sub[A]): Stream[IO, A] = Sub.toMap(sub).values.head

  test("resource registration is lazy and retains emissions during acquisition through map") {
    (for {
      starts <- Ref.of[IO, Int](0)
      cleanups <- Ref.of[IO, Int](0)
      sub = Sub.fromResource[Int]("resource") { emit =>
        Resource.make(starts.update(_ + 1) *> IO { emit(1); emit(2) })(
          _ => cleanups.update(_ + 1)
        )
      }
      before <- starts.get
      values <- stream(sub.map(_ * 10)).take(2).compile.toList
      after <- starts.get
      released <- cleanups.get
    } yield {
      assert(before == 0 && after == 1)
      assert(values == List(10, 20))
      assert(released == 1)
    }).timeout(2.seconds).unsafeToFuture()
  }

  test("resource cleanup runs once on consumer failure and cancellation") {
    List(false, true).traverse_ { cancel =>
      for {
        ready <- Deferred[IO, Unit]
        cleanups <- Ref.of[IO, Int](0)
        failure = new RuntimeException("consumer failed")
        sub = Sub.fromResource[Int]("resource") { emit =>
          Resource.make(IO(emit(1)))(_ => cleanups.update(_ + 1))
        }
        consume = stream(sub).evalMap { _ =>
          ready.complete(()) *> (if (cancel) IO.never[Unit] else IO.raiseError[Unit](failure))
        }.compile.drain
        _ <- if (cancel)
          Resource.make(consume.start)(_.cancel).use(fiber => ready.get *> fiber.cancel)
        else consume.attempt.flatMap(result => IO(assert(result == Left(failure))))
        released <- cleanups.get
        _ <- IO(assert(released == 1))
      } yield ()
    }.as(succeed).timeout(2.seconds).unsafeToFuture()
  }

  test("failed second resource acquisition releases the first and preserves the error") {
    (for {
      cleanups <- Ref.of[IO, Int](0)
      failure = new RuntimeException("second acquisition failed")
      sub = Sub.fromResource[Int]("partial") { _ =>
        for {
          _ <- Resource.make(IO.unit)(_ => cleanups.update(_ + 1))
          _ <- Resource.eval(IO.raiseError[Unit](failure))
        } yield ()
      }
      result <- stream(sub).compile.drain.attempt
      released <- cleanups.get
    } yield {
      assert(result == Left(failure))
      assert(released == 1)
    }).timeout(2.seconds).unsafeToFuture()
  }

  test("resource finalizers run in reverse order even when one fails") {
    (for {
      releases <- Ref.of[IO, List[String]](Nil)
      failure = new RuntimeException("second cleanup failed")
      sub = Sub.fromResource[Int]("finalizers") { emit =>
        for {
          _ <- Resource.make(IO.unit)(_ => releases.update(_ :+ "first"))
          _ <- Resource.make(IO(emit(1)))(_ =>
            releases.update(_ :+ "second") *> IO.raiseError[Unit](failure)
          )
        } yield ()
      }
      result <- stream(sub).take(1).compile.drain.attempt
      order <- releases.get
    } yield {
      assert(result == Left(failure))
      assert(order == List("second", "first"))
    }).timeout(2.seconds).unsafeToFuture()
  }

  test("every reads epoch milliseconds from the effect clock at each virtual tick") {
    cats.effect.testkit.TestControl.executeEmbed {
      stream(Sub.every(1.second, "clock")).take(3).compile.toList.map { ticks =>
        assert(ticks == List(1000L, 2000L, 3000L))
      }
    }.unsafeToFuture()
  }

  test("cancellation during asynchronous registration waits for cleanup") {
    (for {
      started <- Deferred[IO, Unit]
      acquired <- Deferred[IO, Unit]
      cleanups <- Ref.of[IO, Int](0)
      sub = Sub.fromResource[Int]("async") { _ =>
        Resource.make(started.complete(()) *> IO.sleep(30.millis) *> acquired.complete(()))(
          _ => cleanups.update(_ + 1)
        ).void
      }
      fiber <- stream(sub).compile.drain.start
      _ <- started.get
      _ <- fiber.cancel.timeout(2.seconds)
      ready <- acquired.tryGet
      count <- cleanups.get
    } yield {
      assert(ready.contains(()))
      assert(count == 1)
    }).unsafeToFuture()
  }

  test("registration failure reaches the stream consumer") {
    val failure = new RuntimeException("registration failed")
    stream(Sub.fromResource[Int]("failed")(_ => Resource.eval(IO.raiseError[Unit](failure))))
      .compile.drain.attempt.timeout(2.seconds)
      .map(result => assert(result == Left(failure))).unsafeToFuture()
  }

  test("timeout emits once and every can be canceled and restarted") {
    (for {
      once <- stream(Sub.timeout(5.millis, "done", "timeout")).compile.toList
      ticks <- Ref.of[IO, Int](0)
      first <- Deferred[IO, Unit]
      fiber <- stream(Sub.every(5.millis, "tick"))
        .evalMap(_ => ticks.update(_ + 1) *> first.complete(()).void).compile.drain.start
      _ <- first.get.timeout(2.seconds)
      _ <- fiber.cancel
      stopped <- ticks.get
      _ <- IO.sleep(20.millis)
      after <- ticks.get
      restarted <- stream(Sub.every(5.millis, "tick")).take(2).compile.toList.timeout(2.seconds)
    } yield {
      assert(once == List("done"))
      assert(stopped > 0 && after == stopped)
      assert(restarted.size == 2)
    }).unsafeToFuture()
  }

  test("DOM event listeners are removed when their stream is canceled") {
    (for {
      ready <- Deferred[IO, Unit]
      received <- Deferred[IO, String]
      conversions <- Ref.of[IO, Int](0)
      // Instrument the event target to know when registration has completed.
      listener = new Array[js.Function1[dom.Event, Unit]](1)
      removed = new Array[Boolean](1)
      target = js.Dynamic.literal(
        addEventListener = ((_: String, f: js.Function1[dom.Event, Unit]) => {
          listener(0) = f
          ready.complete(()).unsafeRunAndForget()
        }): js.Function2[String, js.Function1[dom.Event, Unit], Unit],
        removeEventListener = ((_: String, f: js.Function1[dom.Event, Unit]) => {
          assert(listener(0) == f)
          removed(0) = true
          listener(0) = null
        }): js.Function2[String, js.Function1[dom.Event, Unit], Unit]
      ).asInstanceOf[dom.EventTarget]
      sub = Sub.fromEvent[dom.Event, String]("custom", target)(e => Some(e.`type`))
      fiber <- stream(sub).evalMap(msg => conversions.update(_ + 1) *> received.complete(msg).void).compile.drain.start
      _ <- ready.get.timeout(2.seconds)
      _ <- IO(listener(0)(js.Dynamic.literal(`type` = "custom").asInstanceOf[dom.Event]))
      value <- received.get.timeout(2.seconds)
      _ <- fiber.cancel
      count <- conversions.get
    } yield {
      assert(value == "custom" && count == 1)
      assert(removed(0) && listener(0) == null)
    }).unsafeToFuture()
  }
}

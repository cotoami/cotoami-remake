package marubinotto.fui

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite

class FeatureBindingMessageSpec extends AsyncFunSuite {
  import FeatureSlotsExamples.*

  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def emitted(cmd: Cmd[Msg]): IO[List[Msg]] = cmd match {
    case one: Cmd.One[Msg] => one.io.map(_.toList)
    case Cmd.Batch(cmds*) =>
      cmds.foldLeft(IO.pure(List.empty[Msg])) { (acc, one) =>
        acc.flatMap(xs => one.io.map(result => xs ++ result.toList))
      }
  }

  test("message wraps a typed child event without updating it") {
    val connected = binding(Children.policy())
    val opened = connected.init(Model(), Children.a, 1).model
    val message: Option[Msg] = connected.message(opened, Children.a, 2)
    assert(Children.a.model(opened.children).contains(1))
    val next = connected.route(message.get, opened).get.model
    assert(Children.a.model(next.children).contains(3))
    assert(next.log == List("a:1", "a:3", "a:2"))
  }

  test(
    "inactive children return None and Cmd.none and cannot be targeted by opening later"
  ) {
    val connected = binding(Children.policy())
    val inactive = Model()
    assert(connected.message(inactive, Children.a, 2).isEmpty)
    val command = connected.send(inactive, Children.a, 2)
    assert(command == Cmd.none)
    val opened = connected.init(inactive, Children.a, 1).model
    emitted(command).map { messages =>
      assert(messages.isEmpty)
      assert(Children.a.model(opened.children).contains(1))
    }.unsafeToFuture()
  }

  test(
    "send emits one parent message and only routing performs the transition"
  ) {
    val connected = binding(Children.policy())
    val opened = connected.init(Model(), Children.text, "hello").model
    val command: Cmd[Msg] = connected.send(opened, Children.text, "!")
    emitted(command).map { messages =>
      assert(messages.size == 1)
      assert(Children.text.model(opened.children).contains("hello"))
      val next = connected.route(messages.head, opened).get.model
      assert(Children.text.model(next.children).contains("hello!"))
      assert(next.log == List("text:!"))
    }.unsafeToFuture()
  }

  for (operation <- List("reinitialize", "remove", "clear", "exclusive")) {
    test(
      s"messages and commands captured before $operation never target a new visit"
    ) {
      val connected = binding(rules)
      val opened = connected.init(Model(), Children.a, 1).model
      val message = connected.message(opened, Children.a, 100).get
      val command = connected.send(opened, Children.a, 100)
      val intermediate = operation match {
        case "remove"     => connected.remove(opened, Children.a).model
        case "clear"     => connected.clear(opened).model
        case "exclusive" => connected.init(opened, Children.b, 10).model
        case _           => opened
      }
      if (operation != "reinitialize") {
        assert(connected.message(intermediate, Children.a, 1).isEmpty)
        assert(connected.send(intermediate, Children.a, 1) == Cmd.none)
        assert(
          connected.route(message, intermediate).get == Transition.unchanged(
            intermediate
          )
        )
      }
      val reopened = connected.init(intermediate, Children.a, 5).model
      assert(
        connected.route(message, reopened).get == Transition.unchanged(reopened)
      )
      // Execute only after replacement: send must already have captured the old visit.
      emitted(command).map { messages =>
        assert(messages.size == 1)
        assert(
          connected.route(messages.head, reopened).get == Transition.unchanged(
            reopened
          )
        )
        val current = connected.message(reopened, Children.a, 1).get
        assert(
          Children.a.model(
            connected.route(current, reopened).get.model.children
          ).contains(6)
        )
      }.unsafeToFuture()
    }
  }

  test("ordinary updates and closing siblings preserve captured destinations") {
    val connected = binding(Children.policy())
    val opened = connected.init(Model(), Children.a, 1).model
    val both = connected.init(opened, Children.text, "keep").model
    val command = connected.send(both, Children.a, 2)
    val updated = connected.route(
      connected.message(both, Children.a, 3).get,
      both
    ).get.model
    val closedSibling = connected.remove(updated, Children.text).model
    emitted(command).map { messages =>
      val next = connected.route(messages.head, closedSibling).get.model
      assert(Children.a.model(next.children).contains(6))
      assert(Children.text.model(next.children).isEmpty)
    }.unsafeToFuture()
  }

  test("message and send reject foreign children and incorrect payload types") {
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] { val a = slot(Count) }
      binding(rules).message(Model(), Foreign.a, 1)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] { val a = slot(Count) }
      binding(rules).send(Model(), Foreign.a, 1)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      binding(rules).message(Model(), Children.text, 1)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      binding(rules).send(Model(), Children.a, "wrong")
    """)
  }
}

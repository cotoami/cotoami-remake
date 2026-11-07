package marubinotto.fui

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.duration.*

class TransitionSpec extends AnyFunSuite {
  test("andThen passes the latest model once and retains outputs in order") {
    var seen = List.empty[Int]
    val result = Transition(2, outputs = List("first", "second"))
      .andThen { model =>
        seen = seen :+ model
        Transition(model + 3, outputs = List("third"))
      }
      .andThen { model =>
        seen = seen :+ model
        Transition(model * 2, outputs = List("fourth"))
      }

    assert(seen == List(2, 5))
    assert(result.model == 10)
    assert(result.outputs == List("first", "second", "third", "fourth"))
    assert(result.cmd == Cmd.none)
  }

  test("andThen preserves every command and debounce policy without executing IO") {
    var executions = 0
    val first = IO { executions += 1; "first" }.toCmd.debounce("first", 1.second)
    val second = IO { executions += 1; "second" }.toCmd
    val third = IO { executions += 1; "third" }.toCmd.debounce("third", 2.seconds)

    val result = Transition(0, first)
      .andThen(model => Transition(model + 1, second ++ third))

    assert(executions == 0)
    assert(result.model == 1)
    assert(result.cmd == Cmd.Batch(first, second, third))
  }

  test("unchanged transitions retain the other transition's model, commands and outputs") {
    val cmd = Cmd.One.pure("message")
    val change = Transition(7, cmd, List("output"))
    val before = Transition.unchanged(0).andThen(_ => change)
    val after = change.andThen(model => Transition.unchanged(model))

    for (result <- List(before, after)) {
      assert(result.model == 7)
      assert(result.cmd == Cmd.Batch(cmd))
      assert(result.outputs == List("output"))
    }
  }

  test("andThen supports widening message and output types") {
    sealed trait Message
    case object FirstMessage extends Message
    case object SecondMessage extends Message
    sealed trait Output
    case object FirstOutput extends Output
    case object SecondOutput extends Output

    val first = Cmd.One.pure(FirstMessage)
    val second = Cmd.One.pure(SecondMessage)
    val result: Transition[Int, Message, Output] =
      Transition(1, first, List(FirstOutput))
        .andThen[Message, Output](model =>
          Transition(model + 1, second, List(SecondOutput))
        )

    assert(result.model == 2)
    assert(result.cmd == Cmd.Batch(first, second))
    assert(result.outputs == List(FirstOutput, SecondOutput))
  }
}

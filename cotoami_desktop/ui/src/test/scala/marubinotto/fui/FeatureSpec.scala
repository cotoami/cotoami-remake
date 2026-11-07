package marubinotto.fui

import org.scalatest.funsuite.AnyFunSuite
import org.scalajs.dom.URL
import marubinotto.react.html.div

object FeatureExamples {
  object Counter extends Feature[Int, Unit, Int, Int, Int] {
    def init(input: Int, context: Unit) =
      Transition(input, outputs = List(input))
    def update(msg: Int, model: Int, context: Unit) =
      Transition(model + msg, outputs = List(model + msg, msg))
    def view(model: Int, context: Unit, dispatch: Int => Unit) = div(
      model.toString
    )
  }
  object Left extends FeatureSlots[Unit] { val counter = slot(Counter) }
  object Right extends FeatureSlots[Unit] { val counter = slot(Counter) }
  case class Model(
      left: Left.State = Left.empty,
      right: Right.State = Right.empty,
      log: List[Int] = Nil
  )
  enum Msg {
    case LeftEvent(event: Left.Event)
    case RightEvent(event: Right.Event)
  }
  val left = Left.bind[Model, Msg, Nothing](
    Left.policy(),
    _.left,
    (m, s) => m.copy(left = s),
    Msg.LeftEvent.apply,
    { case Msg.LeftEvent(e) => Some(e); case _ => None },
    (o, m) =>
      o match {
        case Left.counter.output(value) =>
          Transition(m.copy(log = m.log :+ value))
        case _ => Transition.unchanged(m)
      }
  )
  val right = Right.bind[Model, Msg, Nothing](
    Right.policy(),
    _.right,
    (m, s) => m.copy(right = s),
    Msg.RightEvent.apply,
    { case Msg.RightEvent(e) => Some(e); case _ => None },
    (o, m) =>
      o match {
        case Right.counter.output(value) =>
          Transition(m.copy(log = m.log :+ value))
        case _ => Transition.unchanged(m)
      }
  )
  val feature: Feature[Unit, Unit, Model, Msg, Nothing] =
    new Feature[Unit, Unit, Model, Msg, Nothing] {
      def init(input: Unit, context: Unit) = {
        val first = left.init(Model(), Left.counter, 1)
        val second = right.init(first.model, Right.counter, 10)
        Transition(second.model, first.cmd ++ second.cmd)
      }
      def update(
          msg: Msg,
          model: Model,
          context: Unit
      ): Transition[Model, Msg, Nothing] =
        throw new AssertionError(
          "child messages must not reach the parent's update"
        )
      def view(model: Model, context: Unit, dispatch: Msg => Unit) =
        div("counters")
    }.withFeatures(left).withFeatures(right)
}

class FeatureSpec extends AnyFunSuite {
  import FeatureExamples.*

  test("withoutContext preserves transitions, rendering and dispatch") {
    val initial = Transition(2, Browser.send(3), List("initialized"))
    val changed = Transition(5, Browser.send(4), List("updated"))
    val rendered: marubinotto.react.ReactNode = div("counter")
    var received = 0
    val counter = Feature.withoutContext[Int, Int, Int, String](
      init = input => { assert(input == 2); initial },
      update = (msg, model) => {
        assert(msg == 3 && model == 2)
        changed
      },
      view = (model, dispatch) => {
        assert(model == 5)
        dispatch(7)
        rendered
      }
    )
    assert(counter.init(2) == initial)
    assert(counter.update(3, 2) == changed)
    assert(counter.view(5, msg => received = msg) == rendered)
    assert(received == 7)
    assert(counter.mapContextFrom[String](_ => ()).init(2, "context") == initial)
  }

  test("withoutContext composes bindings and routes child events") {
    val composed = Feature.withoutContext[Unit, Model, Msg, Nothing](
      init = _ => feature.init(()),
      update = (_, _) => throw new AssertionError("parent called"),
      view = (_, _) => div()
    ).withFeatures(left).withFeatures(right)
    val initial = composed.init(()).model
    val event = Msg.LeftEvent(Left.counter.event(initial.left, 3).get)
    val next = composed.update(event, initial).model
    assert(Left.counter.model(next.left).contains(4))
    assert(next.log == List(1, 10, 4, 3))
  }

  test(
    "initial outputs see installed state and child output order is preserved"
  ) {
    val initial = feature.init(())
    assert(initial.model.log == List(1, 10))
    val event = Left.counter.event(initial.model.left, 3).get
    val next = feature.update(Msg.LeftEvent(event), initial.model)
    assert(Left.counter.model(next.model.left).contains(4))
    assert(Right.counter.model(next.model.right).contains(10))
    assert(next.model.log == List(1, 10, 4, 3))
  }

  test(
    "clear preserves generations and reopening rejects previous instance events"
  ) {
    val initial = feature.init(()).model
    val old = Msg.LeftEvent(Left.counter.event(initial.left, 2).get)
    val cleared = left.clear(initial).model
    assert(feature.update(old, cleared) == Transition.unchanged(cleared))
    val reopened = left.init(cleared, Left.counter, 1).model
    assert(feature.update(old, reopened).model == reopened)
    val current = feature.update(
      Msg.LeftEvent(Left.counter.event(reopened.left, 2).get),
      reopened
    )
    assert(Left.counter.model(current.model.left).contains(3))
  }

  test("an initial output can remove its child from the resulting state") {
    val closing = Left.bind[Model, Msg, Nothing](
      Left.policy(),
      _.left,
      (m, s) => m.copy(left = s),
      Msg.LeftEvent.apply,
      { case Msg.LeftEvent(e) => Some(e); case _ => None },
      (_, m) => Transition(m.copy(left = Left.clear(m.left)))
    )
    val next = closing.init(Model(), Left.counter, 3)
    assert(Left.counter.model(next.model.left).isEmpty)
  }

  test("nested FeatureSlots groups reject events from a previous ancestor") {
    object Outer extends FeatureSlots[Unit] { val counters = slot(feature) }
    val first = Outer.init(Outer.empty, Outer.counters, (), Outer.policy())
    val inside = Outer.counters.model(first.model).get
    val childMsg = Msg.LeftEvent(Left.counter.event(inside.left, 1).get)
    val old = Outer.counters.event(first.model, childMsg).get
    val second = Outer.init(first.model, Outer.counters, (), Outer.policy())
    assert(!Outer.accepts(old, second.model))
    val current = Outer.counters.model(second.model).get
    val msg = Msg.LeftEvent(Left.counter.event(current.left, 4).get)
    val next =
      Outer.update(Outer.counters.event(second.model, msg).get, second.model)
    assert(
      Left.counter.model(Outer.counters.model(next.model).get.left).contains(5)
    )
    val changedInner = left.init(current, Left.counter, 20).model
    // Keep the outer visit, but replace an inner visit before its old message arrives.
    object Container extends FeatureSlots[Unit] {
      val item = slot(new Feature[Model, Unit, Model, Msg, Nothing] {
        def init(input: Model, context: Unit): Transition[Model, Msg, Nothing] =
          Transition(input)
        def update(msg: Msg, model: Model, context: Unit) =
          feature.update(msg, model)
        def view(model: Model, context: Unit, dispatch: Msg => Unit) = div()
      })
    }
    val container = Container.init(
      Container.empty,
      Container.item,
      changedInner,
      Container.policy()
    ).model
    val staleInner = Msg.LeftEvent(Left.counter.event(current.left, 999).get)
    val addressed = Container.item.event(container, staleInner).get
    val ignored = Container.update(addressed, container)
    assert(Container.item.model(ignored.model).contains(changedInner))
    assert(ignored.cmd == Cmd.none)
    assert(ignored.outputs.isEmpty)
  }

  test("updates retain new models even when equality ignores a changed field") {
    case class PartialModel(id: Int)(val text: String)
    val before = PartialModel(1)("before")
    val after = PartialModel(1)("after")
    assert(before == after)
    object Slot extends FeatureSlots[Unit] {
      val item = slot(new Feature[Unit, Unit, PartialModel, Unit, Nothing] {
        def init(
            input: Unit,
            context: Unit
        ): Transition[PartialModel, Unit, Nothing] =
          Transition(before)
        def update(
            msg: Unit,
            model: PartialModel,
            context: Unit
        ): Transition[PartialModel, Unit, Nothing] =
          Transition(after)
        def view(model: PartialModel, context: Unit, dispatch: Unit => Unit) =
          div(model.text)
      })
    }
    val initial = Slot.init(Slot.empty, Slot.item, (), Slot.policy()).model
    val next = Slot.update(Slot.item.event(initial, ()).get, initial)
    assert(Slot.item.model(next.model).get.text == "after")
  }

  test("a FeatureSlots group cannot be registered twice in the same parent") {
    intercept[IllegalArgumentException](feature.withFeatures(left))
  }

  test(
    "FeatureSlots addresses, states, children and inputs are statically distinct"
  ) {
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      def wrong(event: Left.Event): Right.Event = event
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      Left.init(Left.empty, Right.counter, 1, Left.policy())
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      Left.init(Left.empty, Left.counter, "wrong input", Left.policy())
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureExamples.*
      left.init(Model(), Right.counter, 1)
    """)
  }

  test("FeatureProgram adapts to the existing Program interface") {
    val program = FeatureProgram[Model, Msg](
      _ => feature.init(()),
      (_, _) => throw new AssertionError("parent called"),
      (_, _) => div("root")
    ).withFeatures(left).withFeatures(right).program
    val model = program.init(new URL("https://example.test/"))._1
    val event = Msg.RightEvent(Right.counter.event(model.right, 2).get)
    val next = program.update(event, model)._1
    assert(Right.counter.model(next.right).contains(12))
  }
}

package marubinotto.fui

import org.scalatest.funsuite.AnyFunSuite
import marubinotto.react.html.div

object FeatureSlotsExamples {
  object Count extends Feature[Int, Unit, Int, Int, Int] {
    def init(input: Int, context: Unit) =
      Transition(input, outputs = List(input))
    def update(msg: Int, model: Int, context: Unit) =
      Transition(model + msg, outputs = List(model + msg, msg))
    def view(model: Int, context: Unit, dispatch: Int => Unit) = div(
      model.toString
    )
  }
  object Text extends Feature[String, Unit, String, String, String] {
    def init(input: String, context: Unit) = Transition(input)
    def update(msg: String, model: String, context: Unit) =
      Transition(model + msg, outputs = List(msg))
    def view(model: String, context: Unit, dispatch: String => Unit) = div(
      model
    )
  }
  object Children extends FeatureSlots[Unit] {
    val a = slot(Count)
    val b = slot(Count)
    val c = slot(Count)
    val d = slot(Count)
    val text = slot(Text)
  }
  val rules = Children.policy(
    Children.exclusive(Children.a, Children.b),
    Children.exclusive(Children.c, Children.d)
  )
  case class Model(
      children: Children.State = Children.empty,
      log: List[String] = Nil
  )
  enum Msg {
    case ChildEvent(event: Children.Event)
  }
  def binding(policy: Children.Policy) = Children.bind[Model, Msg, Nothing](
    policy,
    _.children,
    (m, s) => m.copy(children = s),
    Msg.ChildEvent.apply,
    { case Msg.ChildEvent(event) => Some(event) },
    (output, model) =>
      output match {
        case Children.a.output(value) =>
          assert(Children.a.model(model.children).isDefined)
          Transition(model.copy(log = model.log :+ s"a:$value"))
        case Children.text.output(value) =>
          Transition(model.copy(log = model.log :+ s"text:$value"))
        case _ => Transition.unchanged(model)
      }
  )
}

class FeatureSlotsSpec extends AnyFunSuite {
  import FeatureSlotsExamples.*

  test(
    "default policy keeps differently typed children independent through one binding"
  ) {
    val connected = Children.bind[Model, Msg, Nothing](
      state = _.children,
      replace = (model, children) => model.copy(children = children),
      wrap = Msg.ChildEvent.apply,
      extract = { case Msg.ChildEvent(event) => Some(event) },
      onOutput = (output, model) => output match {
        case Children.a.output(value) =>
          Transition(model.copy(log = model.log :+ s"a:$value"))
        case Children.text.output(value) =>
          Transition(model.copy(log = model.log :+ s"text:$value"))
        case _ => Transition.unchanged(model)
      }
    )
    val first = connected.init(Model(), Children.a, 1).model
    val second = connected.init(first, Children.b, 10).model
    val both = connected.init(second, Children.text, "hello").model
    val result = connected.route(
      Msg.ChildEvent(Children.a.event(both.children, 2).get),
      both
    ).get.model
    assert(Children.a.model(result.children).contains(3))
    assert(Children.b.model(result.children).contains(10))
    assert(Children.text.model(result.children).contains("hello"))
    assert(result.log == List("a:1", "a:3", "a:2"))
    val text = connected.route(
      Msg.ChildEvent(Children.text.event(result.children, "!").get),
      result
    ).get.model
    assert(Children.text.model(text.children).contains("hello!"))
    assert(text.log.last == "text:!")
  }

  test(
    "exclusive groups replace only their own members, preserving other event destinations"
  ) {
    val connected = binding(rules)
    val first = connected.init(Model(), Children.a, 1).model
    val second = connected.init(first, Children.c, 3).model
    val all = connected.init(second, Children.text, "keep").model
    val staleA = Msg.ChildEvent(Children.a.event(all.children, 100).get)
    val currentC = Msg.ChildEvent(Children.c.event(all.children, 2).get)
    val b = connected.init(all, Children.b, 2).model
    assert(Children.a.model(b.children).isEmpty)
    assert(Children.b.model(b.children).contains(2))
    assert(connected.route(staleA, b).get == Transition.unchanged(b))
    val c = connected.route(currentC, b).get.model
    assert(Children.c.model(c.children).contains(5))
    val d = connected.init(c, Children.d, 4).model
    assert(Children.c.model(d.children).isEmpty)
    assert(Children.b.model(d.children).contains(2))
    assert(Children.text.model(d.children).contains("keep"))
    val again = connected.init(d, Children.a, 1).model
    assert(connected.route(staleA, again).get == Transition.unchanged(again))
  }

  for (operation <- List("reinitialize", "remove", "clear")) {
    test(
      s"$operation preserves history and rejects old events after reopening"
    ) {
      val connected = binding(Children.policy())
      val opened = connected.init(Model(), Children.a, 1).model
      val both = connected.init(opened, Children.text, "keep").model
      val stale = Msg.ChildEvent(Children.a.event(both.children, 100).get)
      val before = operation match {
        case "remove" => connected.remove(both, Children.a).model
        case "clear" => connected.clear(both).model
        case _       => both
      }
      if (operation != "reinitialize") {
        assert(
          connected.route(stale, before).get == Transition.unchanged(before)
        )
        assert(Children.a.event(before.children, 1).isEmpty)
      }
      val after = connected.init(before, Children.a, 1).model
      assert(connected.route(stale, after).get == Transition.unchanged(after))
      assert(
        Children.text.model(after.children) == (if (operation == "clear") None
                                                else Some("keep"))
      )
      val current = connected.route(
        Msg.ChildEvent(Children.a.event(after.children, 1).get),
        after
      ).get.model
      assert(Children.a.model(current.children).contains(2))
    }
  }

  test("invalid exclusive groups and overlapping membership are rejected") {
    intercept[IllegalArgumentException](Children.exclusive())
    intercept[IllegalArgumentException](Children.exclusive(Children.a))
    intercept[IllegalArgumentException](
      Children.exclusive(Children.a, Children.a)
    )
    intercept[IllegalArgumentException](
      Children.policy(
        Children.exclusive(Children.a, Children.b),
        Children.exclusive(Children.b, Children.c)
      )
    )
    val group = Children.exclusive(Children.a, Children.b)
    intercept[IllegalArgumentException](Children.policy(group, group))
    assert(Children.policy() != null)
  }

  test(
    "foreign children and policies, wrong inputs and messages are rejected statically"
  ) {
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] { val a = slot(Count) }
      Children.exclusive(Children.a, Foreign.a)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] { val a = slot(Count) }
      Children.policy(Foreign.exclusive(Foreign.a, Foreign.a))
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit]
      binding(Foreign.policy())
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      binding(rules).init(Model(), Children.a, "wrong")
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      Children.text.event(Children.empty, 123)
    """)
  }

  test(
    "initial output can open a sibling and output handlers see installed state in order"
  ) {
    lazy val connected: FeatureBinding[Unit, Model, Msg, Nothing] {
      val features: Children.type
    } =
      Children.bind[Model, Msg, Nothing](
        rules,
        _.children,
        (m, s) => m.copy(children = s),
        Msg.ChildEvent.apply,
        { case Msg.ChildEvent(event) => Some(event) },
        (output, model) =>
          output match {
            case Children.a.output(value) =>
              assert(Children.a.model(model.children).contains(value))
              connected.init(model, Children.b, value + 1)
            case Children.b.output(value) =>
              assert(Children.a.model(model.children).isEmpty)
              assert(Children.b.model(model.children).contains(value))
              Transition(model.copy(log = model.log :+ s"b:$value"))
            case _ => Transition.unchanged(model)
          }
      )
    val next = connected.init(Model(), Children.a, 3).model
    assert(Children.a.model(next.children).isEmpty)
    assert(Children.b.model(next.children).contains(4))
    assert(next.log == List("b:4"))
  }

  test(
    "view callbacks capture a child visit and empty views do not evaluate active fallbacks"
  ) {
    var callback: Int => Unit = _ => ()
    val clickable = new Feature[Int, Unit, Int, Int, Nothing] {
      def init(input: Int, context: Unit): Transition[Int, Int, Nothing] =
        Transition(input)
      def update(
          msg: Int,
          model: Int,
          context: Unit
      ): Transition[Int, Int, Nothing] =
        Transition(model + msg)
      def view(model: Int, context: Unit, dispatch: Int => Unit) = {
        callback = dispatch; div(model.toString)
      }
    }
    object Views extends FeatureSlots[Unit] {
      val one = slot(clickable); val two = slot(clickable)
    }
    case class ViewModel(state: Views.State = Views.empty)
    val connected = Views.bind[ViewModel, Views.Event, Nothing](
      Views.policy(Views.exclusive(Views.one, Views.two)),
      _.state,
      (m, s) => m.copy(state = s),
      identity,
      event => Some(event),
      (_, m) => Transition.unchanged(m)
    )
    var events = List.empty[Views.Event]
    val first = connected.init(ViewModel(), Views.one, 1).model
    connected.view(
      first,
      Views.one,
      event => events = events :+ event,
      throw new AssertionError("fallback")
    )
    val stale = callback
    val second = connected.init(first, Views.two, 2).model
    val again = connected.init(second, Views.one, 3).model
    stale(100)
    assert(
      connected.route(events.head, again).get == Transition.unchanged(again)
    )
    connected.view(again, Views.one, event => events = events :+ event, div())
    callback(4)
    val next = connected.route(events.last, again).get.model
    assert(Views.one.model(next.state).contains(7))
    var fallback = false
    connected.view(next, Views.two, _ => (), { fallback = true; div() })
    assert(fallback)
  }
}

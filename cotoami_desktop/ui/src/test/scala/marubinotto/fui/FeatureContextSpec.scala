package marubinotto.fui

import org.scalatest.funsuite.AnyFunSuite
import slinky.web.html.div

class FeatureContextSpec extends AnyFunSuite {
  private class Fixture {
    case class Context(offset: Int, label: String)
    var rendered = ""
    var callback: Int => Unit = _ => ()
    var projections = 0
    val counter = new Feature[Int, Int, Int, Int, Int] {
      def init(input: Int, context: Int): Transition[Int, Int, Int] =
        Transition(input + context)
      def update(
          msg: Int,
          model: Int,
          context: Int
      ): Transition[Int, Int, Int] =
        Transition(model + msg + context, outputs = List(context))
      def view(model: Int, context: Int, dispatch: Int => Unit) = {
        rendered = s"$model:$context"
        callback = dispatch
        div(rendered)
      }
    }
    object Children extends FeatureSlots[Context] {
      val count = slot(counter.mapContextFrom[Context](_.offset))
      val label = slot(new Feature[Unit, String, String, Unit, Nothing] {
        def init(
            input: Unit,
            context: String
        ): Transition[String, Unit, Nothing] =
          Transition(context)
        def update(
            msg: Unit,
            model: String,
            context: String
        ): Transition[String, Unit, Nothing] =
          Transition(context)
        def view(model: String, context: String, dispatch: Unit => Unit) =
          div(context)
      }.mapContextFrom[Context](_.label))
    }
    case class Model(
        children: Children.State = Children.empty,
        offset: Int = 1,
        log: List[String] = Nil
    )
    case class Msg(event: Children.Event)
    val binding = Children.bind[Context, Model, Msg, Nothing](
      Children.policy(),
      _.children,
      (m, s) => m.copy(children = s),
      Msg.apply,
      msg => Some(msg.event),
      (model, context) => {
        projections += 1
        context.copy(offset = model.offset + context.offset)
      },
      (out, model, context) =>
        out match {
          case Children.count.output(value) =>
            Transition(
              model.copy(log = model.log :+ s"$value:${context.label}")
            )
          case _ => Transition.unchanged(model)
        }
    )
    val feature = new Feature[Int, Context, Model, Msg, Nothing] {
      def init(input: Int, context: Context) =
        binding.init(Model(), Children.count, input, context)
      def update(
          msg: Msg,
          model: Model,
          context: Context
      ): Transition[Model, Msg, Nothing] =
        fail("child event reached the parent")
      def view(model: Model, context: Context, dispatch: Msg => Unit) =
        binding.view(model, Children.count, dispatch, div(), context)
    }.withFeatures(binding)
  }

  test(
    "init, render and delayed events use the context supplied for each call"
  ) {
    val f = new Fixture
    import f.*
    val opened = feature.init(10, Context(2, "initial")).model
    assert(Children.count.model(opened.children).contains(13))
    var pending: Option[Msg] = None
    feature.view(opened, Context(3, "first render"), msg => pending = Some(msg))
    assert(rendered == "13:4")
    val oldCallback = callback
    val current = opened.copy(offset = 20)
    feature.view(current, Context(5, "new render"), _ => ())
    assert(rendered == "13:25")
    assert(Children.count.model(current.children).contains(13))
    oldCallback(1)
    val next = feature.update(pending.get, current, Context(7, "reply")).model
    assert(Children.count.model(next.children).contains(41))
    assert(next.log == List("27:reply"))
    val withLabel =
      binding.init(next, Children.label, (), Context(0, "label")).model
    assert(Children.label.model(withLabel.children).contains("label"))
  }

  test(
    "binding messages capture a visit without evaluating or retaining context"
  ) {
    val f = new Fixture
    import f.*
    val opened = feature.init(10, Context(2, "initial")).model
    val before = projections
    val pending = binding.message(opened, Children.count, 1).get
    assert(binding.send(opened, Children.count, 1).isInstanceOf[Cmd.One[?]])
    assert(projections == before)
    val current = opened.copy(offset = 20)
    val next = feature.update(pending, current, Context(7, "delivery")).model
    assert(Children.count.model(next.children).contains(41))
    assert(next.log == List("27:delivery"))
  }

  test("stale events are rejected before projecting context") {
    val f = new Fixture
    import f.*
    val initial = feature.init(0, Context(0, "")).model
    val event = Msg(Children.count.event(initial.children, 1).get)
    val closed = binding.clear(initial).model
    val before = projections
    assert(
      feature.update(event, closed, Context(100, "")) == Transition.unchanged(
        closed
      )
    )
    assert(projections == before)
  }

  test(
    "nested bindings receive current context and adaptation preserves binding identity"
  ) {
    val f = new Fixture
    import f.*
    val adapted = feature.mapContextFrom[Int](n => Context(n, "nested"))
    object Outer extends FeatureSlots[Int] { val inner = slot(adapted) }
    val initial =
      Outer.init(Outer.empty, Outer.inner, 0, Outer.policy(), 2).model
    val inner = Outer.inner.model(initial).get
    val msg = Msg(Children.count.event(inner.children, 1).get)
    val event = Outer.inner.event(initial, msg).get
    val next = Outer.update(event, initial, 10).model
    val updated = Outer.inner.model(next).get
    assert(Children.count.model(updated.children).contains(15))
    assert(updated.log == List("11:nested"))
    intercept[IllegalArgumentException] {
      feature.mapContextFrom[Context](identity).withFeatures(binding)
    }
  }
}

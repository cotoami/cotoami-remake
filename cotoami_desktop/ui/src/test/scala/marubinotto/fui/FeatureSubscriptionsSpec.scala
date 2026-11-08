package marubinotto.fui

import cats.effect.IO
import fs2.Stream
import org.scalatest.funsuite.AnyFunSuite
import marubinotto.react.html.div
import scala.concurrent.duration.*

class FeatureSubscriptionsSpec extends AnyFunSuite {
  private def keys(cmd: Cmd[?]): Set[String] = cmd match {
    case Cmd.One(_, debounce) => debounce.map(_.key).toSet
    case Cmd.Batch(commands*) => commands.flatMap(c => keys(c)).toSet
  }

  private val leaf = Feature.withoutContext[Int, Int, Int, Nothing](
    input => Transition(input, Cmd.One.pure(1).debounce("same", 1.second)),
    (msg, model) => Transition(model + msg, Cmd.One.pure(1).debounce("same", 1.second)),
    (_, _) => div(),
    _ => Sub.Impl("same", Stream.emit(1))
  )

  test("key mapping preserves streams, IO, delays and unkeyed commands") {
    val stream = Stream.emit(1).covary[IO]
    val sub = Sub.Impl("", stream).combine(Sub.Impl("a:b", stream))
    val mapped = Sub.toMap(sub.mapIds(k => s"prefix:${k.length}:$k"))
    assert(mapped.keySet == Set("prefix:0:", "prefix:3:a:b"))
    assert(mapped.values.forall(_ eq stream))
    assert(Sub.Empty.mapIds(_ => fail("empty")) == Sub.Empty)
    val one = Cmd.One.pure(1).debounce("", 1.second)
    val changed: Cmd.One[Int] = one.mapDebounceKeys("prefix:" + _)
    assert(changed.io eq one.io)
    assert(changed.debounce.contains(Cmd.Debounce("prefix:", 1.second)))
    val plain = Cmd.One.pure(2)
    val batch = (one ++ plain).mapDebounceKeys("prefix:" + _)
    assert(keys(batch) == Set("prefix:"))
    assert(plain.mapDebounceKeys(_ => fail("unkeyed")) == plain)
    assert(Cmd.none.mapDebounceKeys(_ => fail("empty")) == Cmd.none)
  }

  test("slots and groups isolate keys, ordinary updates retain them, replacement changes them") {
    object Slots extends FeatureSlots[Unit] {
      val a = slot(leaf)
      val b = slot(leaf)
    }
    object Other extends FeatureSlots[Unit] { val a = slot(leaf) }
    val first = Slots.init(Slots.empty, Slots.a, 0, Slots.policy())
    val second = Slots.init(first.model, Slots.b, 0, Slots.policy())
    val original = keys(first.cmd)
    assert(original == Sub.toMap(Slots.subscriptions(first.model)).keySet)
    assert((original intersect keys(second.cmd)).isEmpty)
    assert(Sub.toMap(Slots.subscriptions(second.model)).size == 2)
    val updated = Slots.update(Slots.a.event(second.model, 1).get, second.model)
    assert(keys(updated.cmd) == original)
    assert(Sub.toMap(Slots.subscriptions(updated.model)).keySet ==
      Sub.toMap(Slots.subscriptions(second.model)).keySet)
    val replaced = Slots.init(updated.model, Slots.a, 0, Slots.policy())
    assert((keys(replaced.cmd) intersect original).isEmpty)
    assert(!Slots.accepts(Slots.a.event(first.model, 1).get, replaced.model))
    assert((keys(Other.init(Other.empty, Other.a, 0, Other.policy()).cmd) intersect original).isEmpty)
    assert(Sub.toMap(Slots.subscriptions(Slots.clear(replaced.model))).isEmpty)
    val exclusive = Slots.init(second.model, Slots.a, 0,
      Slots.policy(Slots.exclusive(Slots.a, Slots.b)))
    assert(Sub.toMap(Slots.subscriptions(exclusive.model)).size == 1)
  }

  test("nested reuse scopes ancestor identity and separates parent subscriptions") {
    object Children extends FeatureSlots[Unit] { val leafSlot = slot(leaf) }
    val binding = Children.bind[Children.State, Children.Event, Nothing](
      state = identity, replace = (_, state) => state, wrap = identity,
      extract = Some(_), onOutput = (_, state) => Transition(state)
    )
    val parent = Feature.withoutContext[Unit, Children.State, Children.Event, Nothing](
      _ => binding.init(Children.empty, Children.leafSlot, 0),
      (_, state) => Transition(state),
      (_, _) => div(),
      _ => Sub.Impl("same", Stream.never[IO])
    ).withFeatures(binding)
    object Outer extends FeatureSlots[Unit] {
      val left = slot(parent)
      val right = slot(parent)
    }
    val left = Outer.init(Outer.empty, Outer.left, (), Outer.policy())
    val both = Outer.init(left.model, Outer.right, (), Outer.policy())
    val before = Sub.toMap(Outer.subscriptions(both.model)).keySet
    assert(before.size == 4)
    assert((keys(left.cmd) intersect keys(both.cmd)).isEmpty)
    val reset = Outer.init(both.model, Outer.left, (), Outer.policy())
    val after = Sub.toMap(Outer.subscriptions(reset.model)).keySet
    assert((before intersect after).size == 2)
    assert((keys(left.cmd) intersect keys(reset.cmd)).isEmpty)
  }

  test("output handler commands belong to the parent rather than the emitting child") {
    val emitting = Feature.withoutContext[Unit, Unit, Unit, Unit](
      _ => Transition((), Cmd.One.pure(()).debounce("same", 1.second), List(())),
      (_, _) => Transition(()), (_, _) => div()
    )
    object Children extends FeatureSlots[Unit] { val child = slot(emitting) }
    val binding = Children.bind[Children.State, Children.Event, Nothing](
      identity, (_, state) => state, identity, Some(_),
      (_, state) => Transition(state, Cmd.exec(IO.unit).debounce("same", 1.second))
    )
    val parent = Feature.withoutContext[Unit, Children.State, Children.Event, Nothing](
      _ => binding.init(Children.empty, Children.child, ()),
      (_, state) => Transition(state), (_, _) => div(),
      _ => Sub.Impl("same", Stream.never[IO])
    ).withFeatures(binding)
    object Outer extends FeatureSlots[Unit] { val parentSlot = slot(parent) }
    val result = Outer.init(Outer.empty, Outer.parentSlot, (), Outer.policy())
    val parentKey = Sub.toMap(Outer.subscriptions(result.model)).keySet
    assert(keys(result.cmd).size == 2)
    assert(parentKey.subsetOf(keys(result.cmd)))
  }

  test("context projection, adapters, and multiple bindings compose subscriptions") {
    val contextual = new Feature[Int, String, Int, Int, Nothing] {
      def init(input: Int, context: String) = Transition(input)
      def update(msg: Int, model: Int, context: String) = Transition(model + msg)
      def view(model: Int, context: String, dispatch: Int => Unit) = div()
      override def subscriptions(model: Int, context: String) =
        Sub.Impl(s"$context:$model", Stream.emit(1))
    }.mapContextFrom[Int](_.toString)
    object A extends FeatureSlots[Int] { val child = slot(contextual) }
    object B extends FeatureSlots[Unit] { val child = slot(leaf) }
    case class Model(a: A.State = A.empty, b: B.State = B.empty, context: Int = 7)
    enum Msg {
      case AEvent(event: A.Event)
      case BEvent(event: B.Event)
    }
    val a = A.bind[Unit, Model, Msg, Nothing](
      _.a, (m, s) => m.copy(a = s), Msg.AEvent.apply,
      { case Msg.AEvent(e) => Some(e); case _ => None },
      (m, _) => m.context, (_, m, _) => Transition(m)
    )
    val b = B.bind[Model, Msg, Nothing](
      _.b, (m, s) => m.copy(b = s), Msg.BEvent.apply,
      { case Msg.BEvent(e) => Some(e); case _ => None },
      (_, m) => Transition(m)
    )
    val model = a.init(Model(), A.child, 2).andThen(m => b.init(m, B.child, 0)).model
    val program = FeatureProgram[Model, Msg](
      _ => Transition(model), (_, m) => Transition(m), (_, _) => div(),
      subscriptions = _ => Sub.Impl("root", Stream.never[IO])
    ).withFeatures(a).withFeatures(b).program
    val ids = Sub.toMap(program.subscriptions(model)).keySet
    assert(ids.size == 3 && ids.contains("root"))
    assert(ids.exists(_.endsWith(":7:2")))
    assert(Sub.toMap(program.subscriptions(model.copy(context = 9))).keys.exists(_.endsWith(":9:2")))
  }
}

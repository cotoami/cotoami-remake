package marubinotto.fui

import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.duration.*
import marubinotto.react.ReactNode
import marubinotto.react.html.div

class CollectionSlotSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  // Deliberately colliding hashes and labels: neither may be used as identity.
  case class Key(value: Int) {
    override def hashCode(): Int = 0
    override def toString: String = "same"
  }
  var renderedContext = 0
  object Count extends Feature[Int, Int, Int, Int, Int] {
    def init(input: Int, context: Int): Transition[Int, Int, Int] =
      Transition(input + context, outputs = List(input))
    def update(msg: Int, model: Int, context: Int): Transition[Int, Int, Int] =
      Transition(model + msg + context, Browser.send(msg).debounce("save", 1.second), List(msg))
    def view(model: Int, context: Int, dispatch: Int => Unit): ReactNode = {
      renderedContext = context
      div(s"$model:$context")
    }
    override def subscriptions(model: Int, context: Int): Sub[Int] =
      Sub.every(1.second, "tick").map(_ => context)
  }
  object Slots extends FeatureSlots[Int] {
    val counts = collectionSlot[Key](Count)
    val other = collectionSlot[Key](Count)
    val single = slot(Count)
  }
  case class Model(children: Slots.State = Slots.empty, external: Int = 0, outputs: Vector[(Key, Int)] = Vector.empty)
  enum Msg { case Child(event: Slots.Event) }
  val binding = Slots.bind[Unit, Model, Msg, Nothing](
    state = _.children,
    replace = (m, children) => m.copy(children = children),
    wrap = Msg.Child.apply,
    extract = { case Msg.Child(event) => Some(event) },
    context = (m, _) => m.external,
    onOutput = (out, m, _) => out match {
      case Slots.counts.output(key, value) => Transition(m.copy(outputs = m.outputs :+ (key -> value)))
      case _ => Transition.unchanged(m)
    }
  )
  val a = Key(1)
  val b = Key(2)
  def open(m: Model, key: Key, value: Int = 0): Model = binding.init(m, Slots.counts, key, value).model
  def step(m: Model, key: Key, amount: Int): Transition[Model, Msg, Nothing] =
    binding.route(binding.message(m, Slots.counts, key, amount).get, m).get

  test("typed instances and outputs stay independent across keys and registrations") {
    val first = open(Model(), a, 10)
    val both = open(first, b, 20)
    val other = binding.init(both, Slots.other, a, 30).model
    val single = binding.init(other, Slots.single, 40).model
    val changed = step(single, a, 2).model
    val value: Option[Int] = binding.model(changed, Slots.counts, a)
    assert(value.contains(12))
    assert(binding.model(changed, Slots.counts, b).contains(20))
    assert(binding.model(changed, Slots.other, a).contains(30))
    assert(binding.model(changed, Slots.single).contains(40))
    assert(changed.outputs == Vector(a -> 10, b -> 20, a -> 2))
    assert(binding.model(first, Slots.counts, a).contains(10))
  }

  test("active keys retain insertion order on update/reset and append on reopen") {
    val both = open(open(Model(), b), a)
    assert(Slots.counts.keys(both.children) == Vector(b, a))
    val reset = open(step(both, b, 1).model, b)
    assert(Slots.counts.keys(reset.children) == Vector(b, a))
    val reopened = open(binding.remove(reset, Slots.counts, b).model, b)
    assert(Slots.counts.keys(reopened.children) == Vector(a, b))
    assert(Slots.counts.keys(binding.clear(reopened, Slots.counts).model.children).isEmpty)
  }

  for (operation <- List("reset", "remove", "collection clear", "clear")) {
    test(s"$operation rejects captured messages after reopening the same key") {
      val original = open(open(Model(), a), b)
      val old = binding.message(original, Slots.counts, a, 100).get
      val closed = operation match {
        case "remove" => binding.remove(original, Slots.counts, a).model
        case "collection clear" => binding.clear(original, Slots.counts).model
        case "clear" => binding.clear(original).model
        case _ => original
      }
      val reopened = open(closed, a, 7)
      val ignored = binding.route(old, reopened).get.model
      assert(binding.model(ignored, Slots.counts, a).contains(7))
      assert(ignored.outputs == reopened.outputs)
      assert(binding.model(step(ignored, a, 1).model, Slots.counts, a).contains(8))
    }
  }

  test("inactive operations are harmless and collection clear preserves other registrations") {
    val initial = Model()
    assert(binding.model(initial, Slots.counts, a).isEmpty)
    assert(!binding.isActive(initial, Slots.counts, a))
    assert(binding.message(initial, Slots.counts, a, 1).isEmpty)
    assert(binding.send(initial, Slots.counts, a, 1) == Cmd.none)
    val absent = binding.remove(initial, Slots.counts, a).model
    assert(Slots.counts.keys(absent.children).isEmpty)
    var evaluatedEmpty = false
    binding.view(absent, Slots.counts, a, _ => (), { evaluatedEmpty = true; div() })
    assert(evaluatedEmpty)
    val both = binding.init(open(initial, a), Slots.other, a, 9).model
    val cleared = binding.clear(both, Slots.counts).model
    assert(!binding.isActive(cleared, Slots.counts, a))
    assert(binding.model(cleared, Slots.other, a).contains(9))
  }

  test("fresh context reaches init, update and view; send captures the current visit") {
    val initial = open(Model(external = 2), a, 10)
    assert(binding.model(initial, Slots.counts, a).contains(12))
    val changed = step(initial.copy(external = 5), a, 1).model
    assert(binding.model(changed, Slots.counts, a).contains(18))
    binding.view(changed, Slots.counts, a, _ => (), fail("active fallback evaluated"))
    assert(renderedContext == 5)
    val sent = binding.send(changed, Slots.counts, a, 100).asInstanceOf[Cmd.One[Msg]]
    val reopened = open(changed, a)
    sent.io.map { event =>
      val delivered = binding.route(event.get, reopened).get.model
      assert(binding.model(delivered, Slots.counts, a).contains(5))
    }.unsafeToFuture()
  }

  test("subscription and debounce scopes distinguish colliding keys and new visits") {
    val both = open(open(Model(), a), b)
    val all = binding.init(both, Slots.other, a, 0).model
    val subs = Sub.toMap(binding.subscriptions(all))
    assert(subs.size == 3)
    def debounce(m: Model, key: Key): String =
      step(m, key, 1).cmd match {
        case Cmd.One(_, debounce) => debounce.get.key
        case Cmd.Batch(commands*) => commands.flatMap(_.debounce.map(_.key)).head
      }
    val first = debounce(all, a)
    val second = debounce(all, b)
    assert(first != second)
    assert(first == debounce(step(all, a, 1).model, a))
    val reset = open(all, a)
    assert(debounce(reset, a) != first)
    assert(debounce(reset, b) == second)
    val retained = Sub.toMap(binding.subscriptions(binding.remove(all, Slots.counts, a).model)).keySet
    assert(retained.size == 2)
    assert(Sub.toMap(binding.subscriptions(reset)).keySet.intersect(subs.keySet) == retained)
    val changed = all.copy(external = 11)
    cats.effect.testkit.TestControl.executeEmbed {
      Sub.toMap(binding.subscriptions(changed)).values.head.take(1).compile.lastOrError.map { event =>
        val result = binding.route(event, changed).get.model
        // The tick's payload and the update context both use the latest external value.
        val values = List(binding.model(result, Slots.counts, a), binding.model(result, Slots.counts, b), binding.model(result, Slots.other, a))
        assert(values.flatten.sum == 22)
      }
    }.unsafeToFuture()
  }

  test("collections cannot be used as exclusive slots") {
    assertDoesNotCompile("""
      import marubinotto.fui.*
      object Group extends FeatureSlots[Unit] {
        val feature = Feature.withoutContext[Int, Int, Int, Nothing](i => Transition(i), (e, m) => Transition(m + e), (_, _) => marubinotto.react.html.div())
        val collection = collectionSlot[Int](feature)
        val single = slot(feature)
        val rules = policy(exclusive(collection, single))
      }
    """)
  }
  test("collection keys, inputs, messages and group state are checked statically") {
    assertDoesNotCompile("""
      import marubinotto.fui.*
      object A extends FeatureSlots[Unit] { val counts = collectionSlot[String](FeatureSlotsExamples.Count) }
      object B extends FeatureSlots[Unit]
      A.counts.model(B.empty, "one")
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.*
      object A extends FeatureSlots[Unit] { val counts = collectionSlot[String](FeatureSlotsExamples.Count) }
      A.init(A.empty, A.counts, 42, 1)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.*
      object A extends FeatureSlots[Unit] { val counts = collectionSlot[String](FeatureSlotsExamples.Count) }
      A.init(A.empty, A.counts, "one", "wrong input")
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.*
      object A extends FeatureSlots[Unit] { val counts = collectionSlot[String](FeatureSlotsExamples.Count) }
      A.counts.event(A.empty, "one", "wrong message")
    """)
  }

}

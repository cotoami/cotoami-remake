package marubinotto.fui

import org.scalatest.funsuite.AnyFunSuite

class FeatureBindingStateSpec extends AnyFunSuite {
  import FeatureSlotsExamples.*

  test("state queries preserve slot model types and use the supplied parent state") {
    val connected = binding(Children.policy())
    val initial = Model()
    assert(!connected.isActive(initial, Children.a))
    assert(connected.model(initial, Children.a).isEmpty)

    val counted = connected.init(initial, Children.a, 3).model
    val opened = connected.init(counted, Children.text, "hello").model
    val count: Option[Int] = connected.model(opened, Children.a)
    val text: Option[String] = connected.model(opened, Children.text)
    assert(count.contains(3))
    assert(text.contains("hello"))
    assert(connected.isActive(opened, Children.a))
    assert(!connected.isActive(opened, Children.b))
    assert(!connected.isActive(initial, Children.a))

    val event = connected.message(opened, Children.a, 2).get
    val updated = connected.route(event, opened).get.model
    assert(connected.model(updated, Children.a).contains(5))
    assert(connected.model(opened, Children.a).contains(3))
  }

  test("state queries reflect replacement, exclusion, removal and clear") {
    val connected = binding(rules)
    val opened = connected.init(Model(), Children.a, 1).model
    val replaced = connected.init(opened, Children.a, 7).model
    assert(connected.model(replaced, Children.a).contains(7))
    val excluded = connected.init(replaced, Children.b, 2).model
    assert(!connected.isActive(excluded, Children.a))
    assert(connected.model(excluded, Children.a).isEmpty)
    assert(connected.isActive(excluded, Children.b))
    val removed = connected.remove(excluded, Children.b).model
    assert(!connected.isActive(removed, Children.b))
    assert(connected.model(removed, Children.b).isEmpty)
    val cleared = connected.clear(replaced).model
    assert(!connected.isActive(cleared, Children.a))
    assert(connected.model(cleared, Children.a).isEmpty)
  }

  test("state queries reject slots from a different group") {
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] {
        val count = slot(Count)
      }
      binding(Children.policy()).model(Model(), Foreign.count)
    """)
    assertDoesNotCompile("""
      import marubinotto.fui.FeatureSlotsExamples.*
      object Foreign extends marubinotto.fui.FeatureSlots[Unit] {
        val count = slot(Count)
      }
      binding(Children.policy()).isActive(Model(), Foreign.count)
    """)
  }
}

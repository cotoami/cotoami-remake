package marubinotto.fui

import marubinotto.react.ReactNode
import marubinotto.react.html.div

class ChildFeatureViewSpec extends FeatureViewSpec(
  feature => new SingleSlotViewAdapter(feature),
  "at the same generation"
)

class SingleSlotViewAdapter(feature: Feature[Int, Unit, Int, Int, Nothing])
    extends FeatureViewAdapter {
  private object Children extends FeatureSlots[Unit] {
    val first = slot(feature)
    val second = slot(feature)
  }
  private val policy = Children.policy()
  private var state = Children.empty
  private def slotFor(child: Int) = child match {
    case 1 => Children.first
    case 2 => Children.second
    case _ => throw new IllegalArgumentException(s"Unknown test child: $child")
  }

  def init(child: Int, input: Int): Unit =
    state = Children.init(state, slotFor(child), input, policy).model
  def model(child: Int): Option[Int] = slotFor(child).model(state)
  def view(child: Int, afterUpdate: () => Unit): ReactNode =
    Children.view(state, slotFor(child), event => {
      state = Children.update(event, state).model
      afterUpdate()
    }, div("empty"))
  def remove(child: Int): Unit = state = Children.remove(state, slotFor(child))
  def clear(): Unit = state = Children.clear(state)
}

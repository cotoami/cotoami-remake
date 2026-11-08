package marubinotto.fui

import marubinotto.react.ReactNode
import marubinotto.react.html.div

class CollectionFeatureViewSpec extends FeatureViewSpec(
  feature => new CollectionSlotViewAdapter(feature),
  "within one collection"
) {
  testReopeningAfter("collection clear")(_.clearCollection())
}

class CollectionSlotViewAdapter(feature: Feature[Int, Unit, Int, Int, Nothing])
    extends FeatureViewAdapter {
  private object Children extends FeatureSlots[Unit] {
    val instances = collectionSlot[Int](feature)
  }
  private var state = Children.empty

  def init(child: Int, input: Int): Unit =
    state = Children.init(state, Children.instances, child, input).model
  def model(child: Int): Option[Int] = Children.instances.model(state, child)
  def view(child: Int, afterUpdate: () => Unit): ReactNode =
    Children.view(state, Children.instances, child, event => {
      state = Children.update(event, state).model
      afterUpdate()
    }, div("empty"))
  def remove(child: Int): Unit = state = Children.remove(state, Children.instances, child)
  def clear(): Unit = state = Children.clear(state)
  def clearCollection(): Unit = state = Children.clear(state, Children.instances)
}

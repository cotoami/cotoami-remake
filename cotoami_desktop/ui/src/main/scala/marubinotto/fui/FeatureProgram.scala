package marubinotto.fui

import org.scalajs.dom.URL
import marubinotto.react.ReactNode

/** Browser adapter for a composed feature; use .program with Browser/Runtime.
  */
final class FeatureProgram[Model, Msg] private (
    feature: Feature[URL, Unit, Model, Msg, Nothing],
    onUrlChange: Option[URL => Msg],
    subscriptions: Model => Sub[Msg]
) {
  def withFeatures(
      child: FeatureBinding[Unit, Model, Msg, Nothing]
  ): FeatureProgram[Model, Msg] =
    new FeatureProgram(feature.withFeatures(child), onUrlChange, subscriptions)

  def program: Program[Model, Msg] = Program(
    url => { val next = feature.init(url); (next.model, next.cmd) },
    (model, dispatch) => feature.view(model, (), dispatch),
    (msg, model) => {
      val next = feature.update(msg, model); (next.model, next.cmd)
    },
    subscriptions,
    onUrlChange
  )
}

object FeatureProgram {
  def apply[M, E](
      init: URL => Transition[M, E, Nothing],
      update: (E, M) => Transition[M, E, Nothing],
      view: (M, E => Unit) => ReactNode,
      subscriptions: M => Sub[E] = (_: M) => Sub.Empty,
      onUrlChange: Option[URL => E] = None
  ): FeatureProgram[M, E] = {
    val initialize = init
    val reduce = update
    val render = view
    new FeatureProgram(
      new Feature[URL, Unit, M, E, Nothing] {
        def init(url: URL, context: Unit) = initialize(url)
        def update(msg: E, model: M, context: Unit) = reduce(msg, model)
        def view(model: M, context: Unit, dispatch: E => Unit) =
          render(model, dispatch)
      },
      onUrlChange,
      subscriptions
    )
  }
}

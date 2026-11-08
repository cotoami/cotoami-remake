package marubinotto.fui

import marubinotto.react.ReactNode

/** A pure state transition. Outputs are delivered to the owner in list order.
  */
case class Transition[Model, +Msg, +Out](
    model: Model,
    cmd: Cmd[Msg] = Cmd.none,
    outputs: List[Out] = Nil
) {
  /** Apply the next transition to this model, retaining both commands and
    * outputs in order. This composes pure transitions immediately; commands
    * are combined with ++, not executed or awaited.
    */
  def andThen[NextMsg >: Msg, NextOut >: Out](
      next: Model => Transition[Model, NextMsg, NextOut]
  ): Transition[Model, NextMsg, NextOut] = {
    val result = next(model)
    Transition(result.model, cmd ++ result.cmd, outputs ++ result.outputs)
  }
}

object Transition {
  def unchanged[Model](model: Model): Transition[Model, Nothing, Nothing] =
    Transition(model)
}

/** A reusable definition, not a running instance or an independent store.
  * Input initializes a visit; Context supplies current read-only external data
  * on each call and is not retained by the child state container. Use Unit when
  * no context is needed. Context changes alone do not invoke update.
  */
trait Feature[Input, Context, Model, Msg, Out] { self =>
  def init(input: Input, context: Context): Transition[Model, Msg, Out]
  def update(
      msg: Msg,
      model: Model,
      context: Context
  ): Transition[Model, Msg, Out]
  def view(model: Model, context: Context, dispatch: Msg => Unit): ReactNode

  /** Declare sources for this instance; unchanged IDs retain their existing streams. */
  def subscriptions(model: Model, context: Context): Sub[Msg] = Sub.Empty

  final def subscriptions(model: Model)(using unit: Unit =:= Context): Sub[Msg] =
    subscriptions(model, unit(()))

  /** Adapt the caller's context without capturing a context value in state. */
  final def mapContextFrom[C](
      project: C => Context
  ): Feature[Input, C, Model, Msg, Out] =
    new Feature[Input, C, Model, Msg, Out] {
      def init(input: Input, context: C) = self.init(input, project(context))
      def update(msg: Msg, model: Model, context: C) =
        self.update(msg, model, project(context))
      def view(model: Model, context: C, dispatch: Msg => Unit) =
        self.view(model, project(context), dispatch)
      override def subscriptions(model: Model, context: C): Sub[Msg] =
        self.subscriptions(model, project(context))
      override private[fui] def childFeatureGroups = self.childFeatureGroups
    }

  final def init(input: Input)(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] =
    init(input, unit(()))
  final def update(msg: Msg, model: Model)(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] =
    update(msg, model, unit(()))
  final def view(model: Model, dispatch: Msg => Unit)(using
      unit: Unit =:= Context
  ): ReactNode =
    view(model, unit(()), dispatch)

  private[fui] def childFeatureGroups: Set[FeatureSlots[?]] = Set.empty

  final def withFeatures(
      child: FeatureBinding[Context, Model, Msg, Out]
  ): Feature[Input, Context, Model, Msg, Out] = {
    require(
      !childFeatureGroups.contains(child.features),
      "A FeatureSlots group can only be bound once within a feature"
    )
    new Feature[Input, Context, Model, Msg, Out] {
      def init(input: Input, context: Context) = self.init(input, context)
      def update(msg: Msg, model: Model, context: Context) =
        child.route(msg, model, context).getOrElse(
          self.update(msg, model, context)
        )
      def view(model: Model, context: Context, dispatch: Msg => Unit) =
        self.view(model, context, dispatch)
      override def subscriptions(model: Model, context: Context): Sub[Msg] =
        self.subscriptions(model, context).combine(child.subscriptions(model, context))
      override private[fui] def childFeatureGroups =
        self.childFeatureGroups + child.features
    }
  }
}

object Feature {
  /** Define a feature whose callbacks need no external context. */
  def withoutContext[I, M, E, O](
      init: I => Transition[M, E, O],
      update: (E, M) => Transition[M, E, O],
      view: (M, E => Unit) => ReactNode,
      subscriptions: M => Sub[E] = (_: M) => Sub.Empty
  ): Feature[I, Unit, M, E, O] = {
    val initialize = init
    val reduce = update
    val render = view
    val subscribe = subscriptions
    new Feature[I, Unit, M, E, O] {
      override def subscriptions(model: M, context: Unit): Sub[E] = subscribe(model)
      def init(input: I, context: Unit) = initialize(input)
      def update(msg: E, model: M, context: Unit) = reduce(msg, model)
      def view(model: M, context: Unit, dispatch: E => Unit) =
        render(model, dispatch)
    }
  }
}

/** A typed connection between one FeatureSlots group and its parent's
  * state/messages.
  */
trait FeatureBinding[Context, Model, Msg, Out] {
  val features: FeatureSlots[?]
  protected def childContext(
      model: Model,
      context: Context
  ): features.ContextValue
  protected def policy: features.Policy
  protected def state(model: Model): features.State
  protected def replace(model: Model, state: features.State): Model
  protected def wrap(event: features.Event): Msg
  protected def extract(msg: Msg): Option[features.Event]
  protected def onOutput(
      output: features.Output,
      model: Model,
      context: Context
  ): Transition[Model, Msg, Out]

  final def subscriptions(model: Model, context: Context): Sub[Msg] =
    features.subscriptions(state(model), childContext(model, context)).map(wrap)

  final def subscriptions(model: Model)(using unit: Unit =:= Context): Sub[Msg] =
    subscriptions(model, unit(()))

  /** Read the active slot's model without evaluating its context. */
  final def model[I, M, E, O](
      parent: Model,
      slot: features.Slot[I, M, E, O]
  ): Option[M] =
    slot.model(state(parent))

  /** Whether the slot has an active instance in this parent state. */
  final def isActive(
      parent: Model,
      slot: features.Slot[?, ?, ?, ?]
  ): Boolean =
    slot.model(state(parent)).isDefined

  final def init[I, M, E, O](
      model: Model,
      child: features.Slot[I, M, E, O],
      input: I,
      context: Context
  ): Transition[Model, Msg, Out] =
    lift(
      model,
      context,
      features.init(
        state(model),
        child,
        input,
        policy,
        childContext(model, context)
      )
    )

  final def remove(
      model: Model,
      child: features.Slot[?, ?, ?, ?]
  ): Transition[Model, Msg, Out] =
    Transition(replace(model, features.remove(state(model), child)))

  final def clear(model: Model): Transition[Model, Msg, Out] =
    Transition(replace(model, features.clear(state(model))))

  /** Capture a parent message addressed to the child's current visit. Returns
    * None when inactive. Routing after replacement ignores the message;
    * creating it neither updates the child nor evaluates its context.
    */
  final def message[I, M, E, O](
      model: Model,
      child: features.Slot[I, M, E, O],
      msg: E
  ): Option[Msg] =
    child.event(state(model), msg).map(wrap)

  /** Send to the visit captured now, not the visit active when the command
    * runs. An inactive child produces Cmd.none, even if initialized before
    * execution.
    */
  final def send[I, M, E, O](
      model: Model,
      child: features.Slot[I, M, E, O],
      msg: E
  ): Cmd[Msg] =
    message(model, child, msg).fold[Cmd[Msg]](Cmd.none)(Browser.send(_))

  final def view(
      model: Model,
      child: features.Slot[?, ?, ?, ?],
      dispatch: Msg => Unit,
      empty: => ReactNode,
      context: Context
  ): ReactNode =
    features.view(
      state(model),
      child,
      event => dispatch(wrap(event)),
      empty,
      childContext(model, context)
    )

  final def init[I, M, E, O](
      model: Model,
      child: features.Slot[I, M, E, O],
      input: I
  )(using
      unit: Unit =:= Context
  ): Transition[Model, Msg, Out] = init(model, child, input, unit(()))

  final def view(
      model: Model,
      child: features.Slot[?, ?, ?, ?],
      dispatch: Msg => Unit,
      empty: => ReactNode
  )(using
      unit: Unit =:= Context
  ): ReactNode = view(model, child, dispatch, empty, unit(()))

  private[fui] final def route(msg: Msg, model: Model)(using
      unit: Unit =:= Context
  ): Option[Transition[Model, Msg, Out]] =
    route(msg, model, unit(()))

  private def lift(
      model: Model,
      context: Context,
      change: Transition[features.State, features.Event, features.Output]
  ) = {
    val initial: Transition[Model, Msg, Out] =
      Transition(replace(model, change.model), change.cmd.map(wrap))
    change.outputs.foldLeft(initial) { (acc, output) =>
      val next = onOutput(output, acc.model, context)
      Transition(next.model, acc.cmd ++ next.cmd, acc.outputs ++ next.outputs)
    }
  }

  private[fui] final def route(
      msg: Msg,
      model: Model,
      context: Context
  ): Option[Transition[Model, Msg, Out]] =
    extract(msg).map { event =>
      if (features.accepts(event, state(model)))
        lift(
          model,
          context,
          features.update(event, state(model), childContext(model, context))
        )
      else Transition.unchanged(model)
    }
}

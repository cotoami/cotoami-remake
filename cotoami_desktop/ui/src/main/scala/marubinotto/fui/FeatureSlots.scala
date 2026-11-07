package marubinotto.fui

import marubinotto.react.FunctionalComponent
import marubinotto.react.ReactNode

/** Typed logical slots for child features, independent of DOM placement.
  * Each slot holds at most one active instance of its registered feature.
  * Coexistence rules are supplied separately at binding time.
  */
abstract class FeatureSlots[Context] {
  type ContextValue = Context
  final class State private[FeatureSlots] (
      private[FeatureSlots] val generations: Map[AnyRef, Long],
      private[FeatureSlots] val entries: Map[AnyRef, Entry]
  )
  final class Event private[FeatureSlots] (
      private[FeatureSlots] val generation: Long,
      private[FeatureSlots] val child: AnyRef,
      private[FeatureSlots] val message: Any
  )
  final class Output private[FeatureSlots] (
      private[FeatureSlots] val child: AnyRef,
      private[FeatureSlots] val value: Any
  )

  /** Initial state only. remove/clear retain generation history on existing
    * states.
    */
  val empty: State = new State(Map.empty, Map.empty)

  final class Exclusive private[FeatureSlots] (
      private[FeatureSlots] val members: Set[AnyRef]
  )
  final class Policy private[FeatureSlots] (
      private[FeatureSlots] val peers: Map[AnyRef, Set[AnyRef]]
  )

  final def exclusive(children: Slot[?, ?, ?, ?]*): Exclusive = {
    require(
      children.size >= 2,
      "An exclusive group must contain at least two children"
    )
    val members = children.toSet[AnyRef]
    require(
      members.size == children.size,
      "A child cannot occur twice in an exclusive group"
    )
    new Exclusive(members)
  }

  /** Unlisted children coexist. Exclusive groups must not overlap. */
  final def policy(groups: Exclusive*): Policy = {
    val members = groups.flatMap(_.members)
    require(
      members.distinct.size == members.size,
      "A child can belong to only one exclusive group"
    )
    new Policy(
      groups.flatMap(group =>
        group.members.map(key => key -> (group.members - key))
      ).toMap
    )
  }

  // Type erasure stays here. Child identity is checked before payload/model casts.
  private trait Entry {
    def model: Any
    def update(
        event: Event,
        state: State,
        context: Context
    ): Transition[State, Event, Output]
    def view(
        generation: Long,
        dispatch: Event => Unit,
        context: Context
    ): ReactNode
  }

  final class Slot[I, M, E, O] private[FeatureSlots] (
      feature: Feature[I, Context, M, E, O]
  ) {
    child =>
    private case class ViewProps(content: ReactNode)
    private val viewId = FeatureSlots.allocateViewId()

    // The component type identifies this child; its key identifies the visit.
    // Include the child ID so concurrently rendered siblings have distinct keys.
    // Keep the type stable across updates, which recreate Entry instances.
    private val boundary = FunctionalComponent[ViewProps] { props =>
      props.content
    }

    object output {
      def unapply(out: Output): Option[O] =
        Option.when(out.child eq child)(out.value.asInstanceOf[O])
    }

    def model(state: State): Option[M] =
      state.entries.get(child).map(_.model.asInstanceOf[M])

    /** Capture the destination of this particular visit. */
    def event(state: State, msg: E): Option[Event] =
      state.entries.get(child).map(_ =>
        new Event(state.generations(child), child, msg)
      )

    private[FeatureSlots] def initialize(
        input: I,
        generation: Long,
        state: State,
        context: Context
    ) =
      transition(feature.init(input, context), generation, state)

    private def transition(
        change: Transition[M, E, O],
        generation: Long,
        state: State
    ): Transition[State, Event, Output] =
      Transition(
        new State(
          state.generations.updated(child, generation),
          state.entries.updated(child, entry(change.model))
        ),
        change.cmd.map(msg => new Event(generation, child, msg)),
        change.outputs.map(out => new Output(child, out))
      )

    private def entry(value: M): Entry = new Entry {
      def model: Any = value
      def update(event: Event, state: State, context: Context) =
        transition(
          feature.update(event.message.asInstanceOf[E], value, context),
          event.generation,
          state
        )
      def view(generation: Long, dispatch: Event => Unit, context: Context) =
        boundary(
          ViewProps(
            feature.view(
              value,
              context,
              msg => dispatch(new Event(generation, child, msg))
            )
          )
        ).withKey(s"$viewId:$generation")
    }
  }

  final def slot[I, M, E, O](
      feature: Feature[I, Context, M, E, O]
  ): Slot[I, M, E, O] =
    new Slot(feature)

  /** Initialize a fresh child generation, replacing its state and removing exclusive peers. */
  final def init[I, M, E, O](
      state: State,
      child: Slot[I, M, E, O],
      input: I,
      policy: Policy,
      context: Context
  ): Transition[State, Event, Output] = {
    val generation = Math.addExact(state.generations.getOrElse(child, 0L), 1L)
    val prepared = new State(
      state.generations,
      state.entries -- policy.peers.getOrElse(child, Set.empty)
    )
    child.initialize(input, generation, prepared, context)
  }

  /** Remove child state while retaining generation history; running commands are not cancelled. */
  final def remove(state: State, child: Slot[?, ?, ?, ?]): State =
    new State(state.generations, state.entries - child)

  final def clear(state: State): State = new State(state.generations, Map.empty)

  final def view(
      state: State,
      child: Slot[?, ?, ?, ?],
      dispatch: Event => Unit,
      empty: => ReactNode,
      context: Context
  ): ReactNode =
    state.entries.get(child).fold(empty)(
      _.view(state.generations(child), dispatch, context)
    )

  /** Project fresh child context from the parent's current model and context.
    */
  final def bind[C, M, E, O](
      policy: Policy,
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      context: (M, C) => Context,
      onOutput: (Output, M, C) => Transition[M, E, O]
  ): FeatureBinding[C, M, E, O] { val features: FeatureSlots.this.type } = {
    val rules = policy
    val get = state
    val set = replace
    val encode = wrap
    val decode = extract
    val project = context
    val output = onOutput
    new FeatureBinding[C, M, E, O] {
      val features: FeatureSlots.this.type = FeatureSlots.this
      protected def policy = rules
      protected def state(model: M) = get(model)
      protected def replace(model: M, value: State) = set(model, value)
      protected def wrap(event: Event) = encode(event)
      protected def extract(msg: E) = decode(msg)
      protected def childContext(model: M, context: C) = project(model, context)
      protected def onOutput(out: Output, model: M, context: C) =
        output(out, model, context)
    }
  }

  /** Convenience for groups and parents that need no external context. */
  final def bind[M, E, O](
      policy: Policy,
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      onOutput: (Output, M) => Transition[M, E, O]
  )(using
      unit: Unit =:= Context
  ): FeatureBinding[Unit, M, E, O] { val features: FeatureSlots.this.type } =
    bind[Unit, M, E, O](
      policy,
      state,
      replace,
      wrap,
      extract,
      (_, _) => unit(()),
      (out, model, _) => onOutput(out, model)
    )

  final def init[I, M, E, O](
      state: State,
      child: Slot[I, M, E, O],
      input: I,
      policy: Policy
  )(using
      unit: Unit =:= Context
  ): Transition[State, Event, Output] =
    init(state, child, input, policy, unit(()))

  final def view(
      state: State,
      child: Slot[?, ?, ?, ?],
      dispatch: Event => Unit,
      empty: => ReactNode
  )(using
      unit: Unit =:= Context
  ): ReactNode = view(state, child, dispatch, empty, unit(()))

  private[fui] final def update(event: Event, state: State)(using
      unit: Unit =:= Context
  ): Transition[State, Event, Output] =
    update(event, state, unit(()))

  private[fui] def accepts(event: Event, state: State): Boolean =
    state.entries.contains(event.child) && state.generations.get(
      event.child
    ).contains(event.generation)

  private[fui] def update(
      event: Event,
      state: State,
      context: Context
  ): Transition[State, Event, Output] =
    if (accepts(event, state))
      state.entries(event.child).update(event, state, context)
    else Transition.unchanged(state)
}

object FeatureSlots {
  // Allocated at registration, not during pure state transitions or rendering.
  // Shared across groups so their children can also be rendered as siblings.
  private var lastViewId = 0L
  private def allocateViewId(): Long = {
    lastViewId = Math.addExact(lastViewId, 1L)
    lastViewId
  }
}

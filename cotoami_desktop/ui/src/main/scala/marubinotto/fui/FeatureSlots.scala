package marubinotto.fui

import scala.collection.immutable.VectorMap

import marubinotto.react.FunctionalComponent
import marubinotto.react.ReactNode

/** Typed logical slots for child features, independent of DOM placement.
  * A slot holds one instance; a collection slot holds instances identified by keys.
  * Coexistence rules are supplied separately at binding time.
  */
abstract class FeatureSlots[Context] {
  type ContextValue = Context
  final class State private[FeatureSlots] (
      private[FeatureSlots] val generations: Map[AnyRef, Long],
      private[FeatureSlots] val entries: VectorMap[AnyRef, Entry]
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
  val empty: State = new State(Map.empty, VectorMap.empty)

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
    def subscriptions(generation: Long, context: Context): Sub[Event]
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
        context: Context,
        address: AnyRef = child
    ) =
      transition(feature.init(input, context), generation, state, address)

    private def transition(
        change: Transition[M, E, O],
        generation: Long,
        state: State,
        address: AnyRef
    ): Transition[State, Event, Output] =
      Transition(
        new State(
          state.generations.updated(address, generation),
          state.entries.updated(address, entry(change.model, address))
        ),
        change.cmd
          .mapDebounceKeys(FeatureSlots.InstanceScope(viewId, generation).encode)
          .map(msg => new Event(generation, address, msg)),
        change.outputs.map(out => new Output(address, out))
      )

    private def entry(value: M, address: AnyRef): Entry = new Entry {
      def subscriptions(generation: Long, context: Context): Sub[Event] =
        feature.subscriptions(value, context)
          .mapIds(FeatureSlots.InstanceScope(viewId, generation).encode)
          .map(msg => new Event(generation, address, msg))
      def model: Any = value
      def update(event: Event, state: State, context: Context) =
        transition(
          feature.update(event.message.asInstanceOf[E], value, context),
          event.generation,
          state,
          address
        )
      def view(generation: Long, dispatch: Event => Unit, context: Context) =
        boundary(
          ViewProps(
            feature.view(
              value,
              context,
              msg => dispatch(new Event(generation, address, msg))
            )
          )
        ).withKey(s"$viewId:$generation")
    }
  }

  private case class CollectionAddress(collection: AnyRef, key: Any)

  /** A registered family of instances. Keys must have stable equality/hashCode.
    * Collections coexist with every slot and cannot join exclusive groups.
    */
  final class CollectionSlot[K, I, M, E, O] private[FeatureSlots] (
      feature: Feature[I, Context, M, E, O]
  ) { collection =>
    private[FeatureSlots] val definition = new Slot(feature)
    private[FeatureSlots] def address(key: K): AnyRef = CollectionAddress(collection, key)

    def model(state: State, key: K): Option[M] =
      state.entries.get(address(key)).map(_.model.asInstanceOf[M])

    def event(state: State, key: K, msg: E): Option[Event] = {
      val destination = address(key)
      state.entries.get(destination).map(_ =>
        new Event(state.generations(destination), destination, msg)
      )
    }

    /** Active keys in insertion order; reinitializing an active key keeps its position. */
    def keys(state: State): Vector[K] =
      state.entries.keysIterator.collect {
        case CollectionAddress(owner, key) if owner eq collection => key.asInstanceOf[K]
      }.toVector

    object output {
      def unapply(out: Output): Option[(K, O)] = out.child match {
        case CollectionAddress(owner, key) if owner eq collection =>
          Some((key.asInstanceOf[K], out.value.asInstanceOf[O]))
        case _ => None
      }
    }
  }

  final class CollectionSlotBuilder[K] private[FeatureSlots] () {
    def apply[I, M, E, O](feature: Feature[I, Context, M, E, O]): CollectionSlot[K, I, M, E, O] =
      new CollectionSlot(feature)
  }

  final def collectionSlot[K]: CollectionSlotBuilder[K] = new CollectionSlotBuilder[K]()

  /** Every initialization gets a fresh family-wide generation, including after clear.
    * This avoids retaining a generation tombstone for each removed key.
    */
  final def init[K, I, M, E, O](
      state: State, child: CollectionSlot[K, I, M, E, O], key: K,
      input: I, context: Context
  ): Transition[State, Event, Output] = {
    val generation = Math.addExact(state.generations.getOrElse(child, 0L), 1L)
    val prepared = new State(state.generations.updated(child, generation), state.entries)
    child.definition.initialize(input, generation, prepared, context, child.address(key))
  }

  final def init[K, I, M, E, O](
      state: State, child: CollectionSlot[K, I, M, E, O], key: K, input: I
  )(using unit: Unit =:= Context): Transition[State, Event, Output] =
    init(state, child, key, input, unit(()))

  final def remove[K](state: State, child: CollectionSlot[K, ?, ?, ?, ?], key: K): State = {
    val address = child.address(key)
    new State(state.generations - address, state.entries - address)
  }

  final def clear[K](state: State, child: CollectionSlot[K, ?, ?, ?, ?]): State =
    child.keys(state).foldLeft(state)((current, key) => remove(current, child, key))

  final def view[K](
      state: State, child: CollectionSlot[K, ?, ?, ?, ?], key: K,
      dispatch: Event => Unit, empty: => ReactNode, context: Context
  ): ReactNode = {
    val address = child.address(key)
    state.entries.get(address).fold(empty)(_.view(state.generations(address), dispatch, context))
  }

  final def view[K](
      state: State, child: CollectionSlot[K, ?, ?, ?, ?], key: K,
      dispatch: Event => Unit, empty: => ReactNode
  )(using unit: Unit =:= Context): ReactNode = view(state, child, key, dispatch, empty, unit(()))

  /** Collect sources from active instances, independently of view placement. */
  final def subscriptions(state: State, context: Context): Sub[Event] =
    state.entries.foldLeft[Sub[Event]](Sub.Empty) { case (acc, (child, entry)) =>
      acc.combine(entry.subscriptions(state.generations(child), context))
    }

  final def subscriptions(state: State)(using unit: Unit =:= Context): Sub[Event] =
    subscriptions(state, unit(()))

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

  final def clear(state: State): State = new State(
    state.generations.filterNot { case (address, _) => address.isInstanceOf[CollectionAddress] },
    VectorMap.empty
  )

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

  /** Bind with the default policy: all children may coexist. */
  final def bind[C, M, E, O](
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      context: (M, C) => Context,
      onOutput: (Output, M, C) => Transition[M, E, O]
  ): FeatureBinding[C, M, E, O] { val features: FeatureSlots.this.type } =
    bind[C, M, E, O](
      policy(), state, replace, wrap, extract, context, onOutput
    )

  /** Bind without external context, allowing all children to coexist. */
  final def bind[M, E, O](
      state: M => State,
      replace: (M, State) => M,
      wrap: Event => E,
      extract: E => Option[Event],
      onOutput: (Output, M) => Transition[M, E, O]
  )(using
      unit: Unit =:= Context
  ): FeatureBinding[Unit, M, E, O] { val features: FeatureSlots.this.type } =
    bind[M, E, O](policy(), state, replace, wrap, extract, onOutput)

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
  private case class InstanceScope(slotId: Long, generation: Long) {
    def encode(key: String): String =
      s"@feature:$slotId:$generation:${key.length}:$key"
  }

  // Allocated at registration, not during pure state transitions or rendering.
  // Shared across groups so their children can also be rendered as siblings.
  private var lastViewId = 0L
  private def allocateViewId(): Long = {
    lastViewId = Math.addExact(lastViewId, 1L)
    lastViewId
  }
}

package marubinotto.react

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.language.implicitConversions
import org.scalajs.dom

/** A value React can render. Scala containers are normalized at this boundary.
  */
trait ReactNode {
  private[react] def raw: js.Any
}
object ReactNode {
  private[react] def wrap(value: js.Any): ReactNode = new ReactNode {
    def raw = value
  }

  /** Import a React node supplied by a JavaScript library. */
  def fromJs(value: js.Any): ReactNode = wrap(value)
  def toJs(value: ReactNode): js.Any = value.raw
  val empty: ReactNode = wrap(null)
  given Conversion[String, ReactNode] = value => wrap(value)
  given Conversion[Int, ReactNode] = value => wrap(value)
  given Conversion[Double, ReactNode] = value => wrap(value)
  given Conversion[Boolean, ReactNode] = value => wrap(value)
  given [A](using
      convert: Conversion[A, ReactNode]
  ): Conversion[Option[A], ReactNode] =
    value => value.fold(empty)(convert.apply)
  given [A <: ReactNode]: Conversion[Option[A], ReactNode] = value =>
    value.getOrElse(empty)
  given [A](using
      convert: Conversion[A, ReactNode]
  ): Conversion[Iterable[A], ReactNode] =
    values => wrap(js.Array(values.map(v => convert(v).raw).toSeq*))
  given [A <: ReactNode]: Conversion[Iterable[A], ReactNode] =
    values => wrap(js.Array(values.map(_.raw).toSeq*))
}
trait ReactElement extends ReactNode
private[react] object ReactElement {
  def wrap(value: js.Any): ReactElement = new ReactElement { def raw = value }
}

@js.native
@JSImport("react", JSImport.Namespace)
private[react] object RawReact extends js.Object {
  def createElement(kind: js.Any, props: js.Any, children: js.Any*): js.Any =
    js.native
  val Fragment: js.Any = js.native
  val StrictMode: js.Any = js.native
  def createContext(value: js.Any): js.Dynamic = js.native
  def useContext(context: js.Any): js.Any = js.native
  def useState(
      initial: js.Function0[js.Any]
  ): js.Tuple2[js.Any, js.Function1[js.Any, Unit]] = js.native
  def useRef(value: js.Any): js.Any = js.native
  def useEffect(
      effect: js.Function0[js.UndefOr[js.Function0[Unit]]],
      deps: js.UndefOr[js.Array[js.Any]]
  ): Unit = js.native
  def useLayoutEffect(
      effect: js.Function0[js.UndefOr[js.Function0[Unit]]],
      deps: js.UndefOr[js.Array[js.Any]]
  ): Unit = js.native
  def useCallback(callback: js.Any, deps: js.Array[js.Any]): js.Any = js.native
}
object React {
  def component[P <: js.Any](render: P => ReactNode): js.Function1[P, js.Any] =
    props => render(props).raw
  def createElement(
      kind: String,
      props: js.Object | js.Dictionary[js.Any],
      children: ReactNode*
  ): ReactElement =
    ReactElement.wrap(RawReact.createElement(kind, props, children.map(_.raw)*))
  def createElement(
      kind: js.Object,
      props: js.Object | js.Dictionary[js.Any],
      children: ReactNode*
  ): ReactElement =
    ReactElement.wrap(RawReact.createElement(kind, props, children.map(_.raw)*))
  def createContext[A](initial: A): Context[A] = new Context(
    RawReact.createContext(initial.asInstanceOf[js.Any])
  )
}
final class Context[A] private[react] (private[react] val raw: js.Dynamic) {
  object Provider {
    def apply(value: A)(children: ReactNode*): ReactElement =
      ReactElement.wrap(
        RawReact.createElement(
          raw.Provider,
          js.Dynamic.literal(value = value.asInstanceOf[js.Any]),
          children.map(_.raw)*
        )
      )
  }
}
object Fragment {
  def apply(children: ReactNode*): ReactElement =
    ReactElement.wrap(
      RawReact.createElement(RawReact.Fragment, null, children.map(_.raw)*)
    )
}

/** The builder is immutable and can be rendered or keyed more than once. */
final class ComponentElement private[react] (
    kind: js.Any,
    props: js.Object,
    children: Seq[ReactNode] = Seq.empty
) extends ReactElement {
  lazy val raw: js.Any =
    RawReact.createElement(kind, props, children.map(_.raw)*)
  def apply(children: ReactNode*): ComponentElement =
    new ComponentElement(kind, props, children)
  def withKey(key: String): ComponentElement = {
    val copy = js.Object.assign(js.Dynamic.literal(), props)
    copy.asInstanceOf[js.Dynamic].updateDynamic("key")(key)
    new ComponentElement(kind, copy, children)
  }
}
final class FunctionalComponent[P] private (render: P => ReactNode) {
  private val component: js.Function1[js.Dynamic, js.Any] = props =>
    render(props.__.asInstanceOf[P]).raw
  def apply(props: P): ComponentElement = new ComponentElement(
    component,
    js.Dynamic.literal(__ = props.asInstanceOf[js.Any])
  )
}
object FunctionalComponent {
  def apply[P](render: P => ReactNode): FunctionalComponent[P] =
    new FunctionalComponent(render)
}
abstract class ExternalComponent {
  type Props
  val component: js.Object
  def apply(props: Props)(using
      encoder: PropsEncoder[Props]
  ): ComponentElement =
    new ComponentElement(component, encoder.encode(props))
}

@js.native
trait ReactRef[A] extends js.Object { var current: A = js.native }
final class DomRef[A <: dom.Element] private[react] (
    private[react] val raw: ReactRef[A | Null]
) {
  def value: Option[A] = Option(raw.current.asInstanceOf[A])
}
final class SetStateHookCallback[A] private[react] (
    set: js.Function1[js.Any, Unit]
) extends (A => Unit) {
  def apply(value: A): Unit = set(
    (() => value.asInstanceOf[js.Any]): js.Function0[js.Any]
  )
  def update(f: A => A): Unit = set(
    (
        (value: js.Any) => f(value.asInstanceOf[A]).asInstanceOf[js.Any]
    ): js.Function1[js.Any, js.Any]
  )
}
object Hooks {
  def useState[A](initial: => A): (A, SetStateHookCallback[A]) = {
    val pair = RawReact.useState(() => initial.asInstanceOf[js.Any])
    // React's setter is stable; keep the Scala adapter stable as well so it
    // can safely appear in effect/callback dependency arrays.
    val adapter = useRef(Option.empty[SetStateHookCallback[A]])
    val setter = adapter.current.getOrElse {
      val created = new SetStateHookCallback[A](pair._2)
      adapter.current = Some(created)
      created
    }
    (pair._1.asInstanceOf[A], setter)
  }
  def useRef[A](initial: A): ReactRef[A] =
    RawReact.useRef(initial.asInstanceOf[js.Any]).asInstanceOf[ReactRef[A]]
  def useDomRef[A <: dom.Element](): DomRef[A] = {
    val raw = RawReact.useRef(null).asInstanceOf[ReactRef[A | Null]]
    val adapter = useRef(Option.empty[DomRef[A]])
    adapter.current.getOrElse {
      val created = new DomRef(raw)
      adapter.current = Some(created)
      created
    }
  }
  def useContext[A](context: Context[A]): A =
    RawReact.useContext(context.raw).asInstanceOf[A]
  def useCallback[A <: js.Function](callback: A, deps: Seq[Any]): A =
    RawReact.useCallback(callback, dependencies(deps)).asInstanceOf[A]
  private def dependencies(deps: Seq[Any]): js.Array[js.Any] =
    js.Array(deps.map(_.asInstanceOf[js.Any])*)
  private def effect(
      f: () => (Unit | (() => Unit))
  ): js.Function0[js.UndefOr[js.Function0[Unit]]] = () => {
    f() match {
      case cleanup: Function0[?] =>
        (() => { cleanup(); () }): js.Function0[Unit]
      case _ => js.undefined
    }
  }
  def useEffect(f: () => (Unit | (() => Unit))): Unit =
    RawReact.useEffect(effect(f), js.undefined)
  def useEffect(f: () => (Unit | (() => Unit)), deps: Seq[Any]): Unit =
    RawReact.useEffect(effect(f), dependencies(deps))
  def useLayoutEffect(f: () => (Unit | (() => Unit))): Unit =
    RawReact.useLayoutEffect(effect(f), js.undefined)
  def useLayoutEffect(f: () => (Unit | (() => Unit)), deps: Seq[Any]): Unit =
    RawReact.useLayoutEffect(effect(f), dependencies(deps))
}
@js.native
private[react] trait RawRoot extends js.Object {
  def render(node: js.Any): Unit = js.native
  def unmount(): Unit = js.native
}
final class Root private[react] (raw: RawRoot) {
  def render(node: ReactNode): Unit = raw.render(node.raw)
  def unmount(): Unit = raw.unmount()
}
@js.native
@JSImport("react-dom/client", JSImport.Namespace)
private object RawDOMClient extends js.Object {
  def createRoot(container: dom.Element): RawRoot = js.native
}
object ReactDOMClient {
  def createRoot(container: dom.Element): Root = new Root(
    RawDOMClient.createRoot(container)
  )
}

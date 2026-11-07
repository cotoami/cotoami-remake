package marubinotto.react

import org.scalatest.funsuite.AsyncFunSuite
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import html.*

@js.native
@JSImport("react-dom", "flushSync")
private object Flush extends js.Function1[js.Function0[Unit], Unit] {
  def apply(f: js.Function0[Unit]): Unit = js.native
}
@js.native
@JSImport("jsdom", "JSDOM")
private class TestDOM(@scala.annotation.unused markup: String)
    extends js.Object {
  val window: js.Dynamic = js.native
}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.DurationInt

class ReactSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def mounted(test: (Root, dom.Element) => Unit) = {
    Resource.make(IO {
      val globals = js.Dynamic.global.globalThis
      val previousWindow = globals.window
      val previousDocument = globals.document
      val env = new TestDOM("<div id='app'></div>")
      globals.window = env.window
      globals.document = env.window.document
      val container = dom.document.getElementById("app")
      (
        ReactDOMClient.createRoot(container),
        container,
        env,
        previousWindow,
        previousDocument
      )
    }) { case (root, _, env, previousWindow, previousDocument) =>
      IO(Flush(() => root.unmount())) *> IO.sleep(20.millis) *> IO {
        env.window.close()
        val globals = js.Dynamic.global.globalThis
        globals.window = previousWindow
        globals.document = previousDocument
      }
    }.use { case (root, container, _, _, _) =>
      IO { test(root, container); succeed }
    }.unsafeToFuture()
  }
  test(
    "children normalize strings, numbers, Options and sequences; builders are reusable"
  ) {
    mounted { (root, container) =>
      val reusable = div(className := "shared")("base")
      Flush(() =>
        root.render(
          div(
            reusable,
            reusable.withKey("second"),
            Option("optional"),
            Seq("a", "b"),
            3,
            false,
            Option.empty[String]
          )
        )
      )
      assert(container.textContent == "basebaseoptionalab3")
      assert(container.querySelectorAll(".shared").length == 2)
      Flush(() => root.render(reusable))
      assert(container.textContent == "base")
    }
  }
  test("optional attributes and event currentTarget retain their meaning") {
    mounted { (root, container) =>
      var target: dom.EventTarget = null
      var current: dom.HTMLButtonElement = null
      Flush(() =>
        root.render(
          button(
            disabled := false,
            title := Option.empty[String],
            onClick := (e => {
              target = e.target
              current = e.currentTarget
            })
          )(span("click"))
        )
      )
      val child = container.querySelector("span")
      val event = js.Dynamic.newInstance(
        dom.window.asInstanceOf[js.Dynamic].MouseEvent
      )("click", js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event]
      child.dispatchEvent(event)
      assert(target eq child)
      assert(current eq container.firstElementChild)
      assert(!current.hasAttribute("title"))
    }
  }
  test("state values, updater functions, context, refs and cleanup") {
    mounted { (root, container) =>
      val context = React.createContext("default")
      var change: SetStateHookCallback[Int] = null
      var mountedRef: DomRef[dom.HTMLDivElement] = null
      var mounts = 0
      var cleanups = 0
      val component = FunctionalComponent[Unit] { _ =>
        val (state, set) = Hooks.useState(1)
        change = set
        mountedRef = Hooks.useDomRef[dom.HTMLDivElement]()
        val label = Hooks.useContext(context)
        Hooks.useLayoutEffect(
          () => {
            mounts += 1
            () => { cleanups += 1 }
          },
          Seq.empty
        )
        div(ref := mountedRef)(s"$label:$state")
      }
      Flush(() => root.render(context.Provider("provided")(component(()))))
      assert(container.textContent == "provided:1")
      assert(mountedRef.value.contains(container.firstElementChild))
      val initialSetter = change
      Flush(() => change.update(_ + 1))
      assert(change eq initialSetter)
      assert(container.textContent == "provided:2")
      Flush(() => change(9))
      assert(container.textContent == "provided:9")
      assert(mounts == 1)
      Flush(() => root.render(ReactNode.empty))
      assert(cleanups == 1)
      assert(mountedRef.value.isEmpty)
    }
  }
  test("function-valued state is stored without invoking it") {
    mounted { (root, _) =>
      var calls = 0
      var set: SetStateHookCallback[() => Int] = null
      var current: () => Int = null
      val component = FunctionalComponent[Unit] { _ =>
        val (value, setter) = Hooks.useState[() => Int](() => { calls += 1; 7 })
        current = value
        set = setter
        div("function")
      }
      Flush(() => root.render(component(())))
      assert(calls == 0)
      Flush(() => set(() => { calls += 1; 8 }))
      assert(calls == 0)
      assert(current() == 8)
      assert(calls == 1)
    }
  }
  test("React 19 calls callback ref cleanup") {
    mounted { (root, _) =>
      var attached = 0
      var cleaned = 0
      Flush(() =>
        root.render(div(ref.cleanup[dom.HTMLDivElement] { _ =>
          attached += 1
          () => { cleaned += 1 }
        }))
      )
      assert(attached == 1)
      Flush(() => root.render(ReactNode.empty))
      assert(cleaned == 1)
    }
  }
  test("effect dependencies and callback identity follow React semantics") {
    mounted { (root, _) =>
      var every = 0
      var once = 0
      var changed = 0
      var cleaned = 0
      var lastCallback: js.Function0[Int] = null
      val callbacks =
        scala.collection.mutable.ArrayBuffer.empty[js.Function0[Int]]
      val component = FunctionalComponent[Int] { value =>
        Hooks.useEffect(() => { every += 1; () })
        Hooks.useEffect(() => { once += 1; () }, Seq.empty)
        Hooks.useEffect(
          () => { changed += 1; () => { cleaned += 1 } },
          Seq(value)
        )
        lastCallback =
          Hooks.useCallback[js.Function0[Int]](() => value, Seq(value))
        callbacks += lastCallback
        div(value)
      }
      Flush(() => root.render(component(1)))
      Flush(() => root.render(component(1)))
      assert(every == 2)
      assert(once == 1)
      assert(changed == 1)
      assert(callbacks(0) eq callbacks(1))
      Flush(() => root.render(component(2)))
      assert(changed == 2)
      assert(cleaned == 1)
      assert(!(callbacks(1) eq callbacks(2)))
      assert(lastCallback() == 2)
      Flush(() => root.render(ReactNode.empty))
      assert(cleaned == 2)
    }
  }
  test("StrictMode balances effect setup and teardown") {
    mounted { (root, _) =>
      var active = 0
      val component = FunctionalComponent[Unit] { _ =>
        Hooks.useLayoutEffect(
          () => { active += 1; () => { active -= 1 } },
          Seq.empty
        )
        div("strict")
      }
      Flush(() =>
        root.render(
          ReactNode.fromJs(
            RawReact.createElement(
              RawReact.StrictMode,
              null,
              ReactNode.toJs(component(()))
            )
          )
        )
      )
      assert(active == 1)
      Flush(() => root.render(ReactNode.empty))
      assert(active == 0)
    }
  }
  test("attributes and events reject incompatible types") {
    assertDoesNotCompile(
      """import marubinotto.react.html.*; input(disabled := "yes")"""
    )
    assertDoesNotCompile(
      """import marubinotto.react.*; import html.*; button(onClick := ((e: SyntheticKeyboardEvent[org.scalajs.dom.HTMLButtonElement]) => ()))"""
    )
    assertDoesNotCompile(
      """import marubinotto.react.*; import html.*; div(onClick := ((e: SyntheticMouseEvent[org.scalajs.dom.HTMLInputElement]) => ()))"""
    )
    assertDoesNotCompile("""import marubinotto.react.html.*; div(new Object)""")
  }
}

package marubinotto.fui

import cats.effect.{IO, Resource}
import scala.concurrent.duration.*
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import marubinotto.react.FunctionalComponent
import marubinotto.react.{Hooks, ReactNode}
import marubinotto.react.ReactDOMClient
import marubinotto.react.html.div

@js.native
@JSImport("react-dom", "flushSync")
private object FlushFeatureView extends js.Function1[js.Function0[Unit], Unit] {
  def apply(callback: js.Function0[Unit]): Unit = js.native
}

/** Shared DOM/lifecycle checks; each adapter exercises its real slot API. */
abstract class FeatureViewSpec[A <: FeatureViewAdapter](
    createAdapter: Feature[Int, Unit, Int, Int, Nothing] => A,
    instanceDescription: String
) extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private def environment = Resource.make(IO {
    val globals = js.Dynamic.global.globalThis
    val previousWindow = globals.window
    val previousDocument = globals.document
    val env = new TestDOM(
      "<div id='app'></div>",
      js.Dynamic.literal(url = "https://example.test/")
    )
    globals.window = env.window
    globals.document = env.window.document
    (env, previousWindow, previousDocument)
  }) { case (env, previousWindow, previousDocument) =>
    IO.sleep(20.millis) *> IO {
      env.window.close()
      val globals = js.Dynamic.global.globalThis
      globals.window = previousWindow
      globals.document = previousDocument
    }
  }

  private class View {
    var mounts = 0
    var cleanups = 0
    var editDraft: () => Unit = () => ()
    var lastDispatch: Int => Unit = _ => ()

    case class Props(model: Int, dispatch: Int => Unit)
    val component = FunctionalComponent[Props] { props =>
      val (draft, setDraft) = Hooks.useState(0)
      editDraft = () => setDraft(7)
      lastDispatch = props.dispatch
      Hooks.useLayoutEffect(
        () => {
          mounts += 1
          () => { cleanups += 1 }
        },
        Seq.empty
      )
      div(s"${props.model}:$draft")
    }
    val feature = new Feature[Int, Unit, Int, Int, Nothing] {
      def init(input: Int, context: Unit): Transition[Int, Int, Nothing] =
        Transition(input)
      def update(
          msg: Int,
          model: Int,
          context: Unit
      ): Transition[Int, Int, Nothing] =
        Transition(model + msg)
      def view(model: Int, context: Unit, dispatch: Int => Unit): ReactNode =
        component(Props(model, dispatch))
    }
    val children = createAdapter(feature)
    children.init(1, 1)
    var selected = 1
    val container = dom.document.getElementById("app")
    val root = ReactDOMClient.createRoot(container)

    def draw(): Unit = FlushFeatureView(() =>
      root.render(
        children.view(selected, () => draw())
      )
    )
    def drawPair(reverse: Boolean): Unit = FlushFeatureView(() => {
      val order =
        if (reverse) List(2, 1) else List(1, 2)
      root.render(
        div(
          order.map(child => children.view(child, () => ()))*
        )
      )
    })

    def edit(): Unit = FlushFeatureView(() => editDraft())
    def node = container.firstChild
    def text = container.textContent
    def close(): Unit = FlushFeatureView(() => root.unmount())
  }

  test(
    "ordinary updates retain local state and DOM identity without adding a DOM wrapper"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.draw()
          val node = view.node
          view.edit()
          assert(view.text == "1:7")
          view.lastDispatch(2)
          assert(view.text == "3:7")
          assert(view.node eq node)
          assert(view.container.childElementCount == 1)
          assert(view.node.asInstanceOf[dom.Element].childElementCount == 0)
          assert(view.mounts == 1)
          assert(view.cleanups == 0)
        } finally view.close()
      }
    }.unsafeToFuture()
  }

  test(
    "reopening with identical input remounts the view and rejects the old callback"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.draw()
          view.edit()
          val node = view.node
          val oldDispatch = view.lastDispatch
          view.children.init(1, 1)
          view.draw()
          assert(view.text == "1:0")
          assert(!(view.node eq node))
          assert(view.mounts == 2)
          assert(view.cleanups == 1)
          oldDispatch(100)
          assert(view.text == "1:0")
          view.lastDispatch(2)
          assert(view.text == "3:0")
          assert(view.mounts == 2)
        } finally view.close()
      }
    }.unsafeToFuture()
  }

  test(
    s"different children of the same feature do not share local state $instanceDescription"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.draw()
          view.edit()
          val node = view.node
          view.children.init(2, 1)
          view.selected = 2
          view.draw()
          assert(view.text == "1:0")
          assert(!(view.node eq node))
          assert(view.mounts == 2)
          assert(view.cleanups == 1)
          assert(view.children.model(1).contains(1))
        } finally view.close()
      }
    }.unsafeToFuture()
  }

  test(
    s"concurrent siblings retain their own state when reordered $instanceDescription"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.children.init(2, 2)
          view.drawPair(false)
          val first = view.node.firstChild
          val second = view.node.lastChild
          view.edit()
          assert(view.text == "1:02:7")
          view.drawPair(true)
          assert(view.text == "2:71:0")
          assert(view.node.firstChild eq second)
          assert(view.node.lastChild eq first)
          assert(view.mounts == 2)
          assert(view.cleanups == 0)
        } finally view.close()
      }
    }.unsafeToFuture()
  }

  protected final def testReopeningAfter(operation: String)(remove: A => Unit): Unit = {
    test(
      s"$operation and reopen remount even without an intervening empty render"
    ) {
      environment.use { _ =>
        IO {
          val view = new View
          try {
            view.draw()
            view.edit()
            remove(view.children)
            view.children.init(1, 1)
            view.draw()
            assert(view.text == "1:0")
            assert(view.mounts == 2)
            assert(view.cleanups == 1)
          } finally view.close()
        }
      }.unsafeToFuture()
    }
  }

  testReopeningAfter("remove")(_.remove(1))
  testReopeningAfter("clear")(_.clear())
}

/** Mutable fixture state is isolated to one test; no events or state are cast. */
trait FeatureViewAdapter {
  def init(child: Int, input: Int): Unit
  def model(child: Int): Option[Int]
  def view(child: Int, afterUpdate: () => Unit): ReactNode
  def remove(child: Int): Unit
  def clear(): Unit
}

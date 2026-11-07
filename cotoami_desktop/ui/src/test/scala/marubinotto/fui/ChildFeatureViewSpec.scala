package marubinotto.fui

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.scalajs.dom
import org.scalatest.funsuite.AsyncFunSuite
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import slinky.core.FunctionalComponent
import slinky.core.facade.{Hooks, ReactElement}
import slinky.web.ReactDOMClient
import slinky.web.html.div

@js.native
@JSImport("react-dom", "flushSync")
private object FlushFeatureView extends js.Function1[js.Function0[Unit], Unit] {
  def apply(callback: js.Function0[Unit]): Unit = js.native
}

class ChildFeatureViewSpec extends AsyncFunSuite {
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
    IO {
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
      def view(model: Int, context: Unit, dispatch: Int => Unit): ReactElement =
        component(Props(model, dispatch))
    }
    object Children extends FeatureSlots[Unit] {
      val first = slot(feature)
      val second = slot(feature)
    }
    val policy = Children.policy()
    var state = Children.init(Children.empty, Children.first, 1, policy).model
    var selected = Children.first
    val container = dom.document.getElementById("app")
    val root = ReactDOMClient.createRoot(container)

    def draw(): Unit = FlushFeatureView(() =>
      root.render(
        Children.view(
          state,
          selected,
          event => {
            state = Children.update(event, state).model
            draw()
          },
          div("empty")
        )
      )
    )
    def drawPair(reverse: Boolean): Unit = FlushFeatureView(() => {
      val children =
        if (reverse) List(Children.second, Children.first)
        else List(Children.first, Children.second)
      root.render(
        div(
          children.map(child =>
            Children.view(state, child, _ => (), div("empty"))
          )*
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
          view.state = view.Children.init(
            view.state,
            view.Children.first,
            1,
            view.policy
          ).model
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
    "different children of the same feature do not share local state at the same generation"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.draw()
          view.edit()
          val node = view.node
          view.state = view.Children.init(
            view.state,
            view.Children.second,
            1,
            view.policy
          ).model
          view.selected = view.Children.second
          view.draw()
          assert(view.text == "1:0")
          assert(!(view.node eq node))
          assert(view.mounts == 2)
          assert(view.cleanups == 1)
          assert(view.Children.first.model(view.state).contains(1))
        } finally view.close()
      }
    }.unsafeToFuture()
  }

  test(
    "concurrent siblings retain their own state when reordered at the same generation"
  ) {
    environment.use { _ =>
      IO {
        val view = new View
        try {
          view.state = view.Children.init(
            view.state,
            view.Children.second,
            2,
            view.policy
          ).model
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

  for (operation <- List("remove", "clear")) {
    test(
      s"$operation and reopen remount even without an intervening empty render"
    ) {
      environment.use { _ =>
        IO {
          val view = new View
          try {
            view.draw()
            view.edit()
            view.state =
              if (operation == "remove")
                view.Children.remove(view.state, view.Children.first)
              else view.Children.clear(view.state)
            view.state = view.Children.init(
              view.state,
              view.Children.first,
              1,
              view.policy
            ).model
            view.draw()
            assert(view.text == "1:0")
            assert(view.mounts == 2)
            assert(view.cleanups == 1)
          } finally view.close()
        }
      }.unsafeToFuture()
    }
  }
}

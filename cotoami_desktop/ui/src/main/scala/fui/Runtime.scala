package fui

import scala.collection.mutable.{Map => MutableMap}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.Event

import slinky.web.ReactDOM

class Runtime[Model, Msg](
    container: Element,
    val program: Program[Model, Msg]
) {
  private val init = program.init(new URL(dom.window.location.href))
  private var state = init._1
  private val subs: MutableMap[String, Option[Sub.Unsubscribe]] = MutableMap()

  def dispatch(msg: Msg): Unit = apply(program.update(msg, state))

  def apply(change: (Model, Seq[Cmd[Msg]])): Unit = {
    import cats.effect.unsafe.implicits.global

    val (model, cmds) = change
    state = model

    ReactDOM.render(program.view(model, dispatch), container)

    // Run side effects
    for (cmd <- cmds) {
      cmd.io.unsafeRunAsync {
        case Right(optionMsg) => optionMsg.map(dispatch)
        case Left(e) => throw e // IO should return Right even when it fails
      }
    }
    updateSubs(state)
  }

  def updateSubs(model: Model): Unit = {
    val nextSubs = Sub.toMap(program.subscriptions(model))
    val keysToAdd = nextSubs.keySet.diff(subs.keySet)
    val keysToRemove = subs.keySet.diff(nextSubs.keySet)
    keysToAdd.foreach(key => {
      // Register the key first to avoid multiple subscriptions
      subs.update(key, None)

      // subscribe and keep the `unsubscribe` function
      nextSubs.get(key) match {
        case Some(subscribe) => subscribe(dispatch, subs.update(key, _))
        case None            => subs.update(key, None)
      }
    })
    keysToRemove.foreach(key => {
      // remove and unsubscribe
      subs.remove(key).map(_.map(_()))
    })
  }

  def onPushUrl(url: URL): Unit =
    program.onUrlChange.map(_(url)).map(dispatch)

  program.onUrlChange.map(onUrlChange => {
    dom.window.addEventListener(
      "popstate",
      (e: Event) => dispatch(onUrlChange(new URL(dom.window.location.href)))
    )
  })

  apply(init)
}

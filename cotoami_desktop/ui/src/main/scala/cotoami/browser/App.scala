package cotoami.browser

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.FunctionalComponent
import slinky.core.facade.Hooks._
import slinky.core.facade.ReactElement
import slinky.web.ReactDOMClient

object App {

  case class Model(url: String, title: Option[String])

  sealed trait Msg

  object Msg {
    case class BrowserStateChanged(url: String, title: Option[String]) extends Msg
  }

  private case class Props(contentLabel: String, initialUrl: String)

  def isCurrentWindow(url: URL): Boolean =
    propsFromUrl(url).nonEmpty

  def mount(container: dom.Element): Unit =
    propsFromUrl(new URL(dom.window.location.href)).foreach(props =>
      ReactDOMClient.createRoot(container).render(component(props))
    )

  private def init(props: Props): Model =
    Model(props.initialUrl, None)

  private def update(msg: Msg, model: Model): Model =
    msg match {
      case Msg.BrowserStateChanged(url, title) =>
        model.copy(url = url, title = title)
    }

  private def view(
      props: Props,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    BrowserShell.component(
      BrowserShell.Props(
        contentLabel = props.contentLabel,
        initialUrl = props.initialUrl,
        model = model,
        onStateChange = (url, title) =>
          dispatch(Msg.BrowserStateChanged(url, title))
      )
    )

  private val component = FunctionalComponent[Props] { props =>
    val (model, setModel) = useState(init(props))

    def dispatch(msg: Msg): Unit =
      setModel(current => update(msg, current))

    view(props, model, dispatch)
  }

  private def propsFromUrl(url: URL): Option[Props] = {
    val params = queryParams(url)
    for {
      browserShell <- params.get("browserShell")
      if browserShell == "1"
      contentLabel <- params.get("contentLabel")
      initialUrl <- params.get("initialUrl")
    } yield Props(contentLabel, initialUrl)
  }

  private def queryParams(url: URL): Map[String, String] =
    Option(url.search).toSeq
      .flatMap(_.stripPrefix("?").split("&"))
      .filter(_.nonEmpty)
      .flatMap(part =>
        part.split("=", 2).toList match {
          case key :: value :: Nil =>
            Some(decodeQueryPart(key) -> decodeQueryPart(value))
          case key :: Nil =>
            Some(decodeQueryPart(key) -> "")
          case _ => None
        }
      )
      .toMap

  private def decodeQueryPart(value: String): String =
    js.URIUtils.decodeURIComponent(value.replace("+", " "))
}

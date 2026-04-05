package cotoami.browser

import scala.scalajs.js
import scala.util.{Failure, Success}

import org.scalajs.dom
import org.scalajs.dom.URL
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import slinky.core.FunctionalComponent
import slinky.core.facade.Hooks._
import slinky.core.facade.ReactElement
import slinky.web.ReactDOMClient

import marubinotto.facade.Nullable
import marubinotto.libs.tauri

import cotoami.backend.SystemInfoJson
import cotoami.models.I18n

object App {

  case class Model(
      url: String,
      title: Option[String],
      i18n: I18n = I18n()
  )

  sealed trait Msg

  object Msg {
    case class BrowserStateChanged(url: String, title: Option[String]) extends Msg
    case class LocaleLoaded(locale: Option[String]) extends Msg
  }

  private case class Props(
      contentLabel: String,
      initialUrl: String,
      locale: Option[String]
  )

  def isCurrentWindow(url: URL): Boolean =
    propsFromUrl(url).nonEmpty

  def mount(container: dom.Element): Unit =
    propsFromUrl(new URL(dom.window.location.href)).foreach(props =>
      ReactDOMClient.createRoot(container).render(component(props))
    )

  private def init(props: Props): Model =
    Model(
      url = props.initialUrl,
      title = None,
      i18n = props.locale.map(I18n.fromBcp47).getOrElse(I18n())
    )

  private def update(msg: Msg, model: Model): Model =
    msg match {
      case Msg.BrowserStateChanged(url, title) =>
        model.copy(url = url, title = title)
      case Msg.LocaleLoaded(locale) =>
        model.copy(i18n = locale.map(I18n.fromBcp47).getOrElse(model.i18n))
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
        text = model.i18n.text,
        onStateChange = (url, title) =>
          dispatch(Msg.BrowserStateChanged(url, title))
      )
    )

  private val component = FunctionalComponent[Props] { props =>
    val (model, setModel) = useState(init(props))

    def dispatch(msg: Msg): Unit =
      setModel(current => update(msg, current))

    useEffect(
      () => {
        if (props.locale.isEmpty) {
          tauri.core.invoke[SystemInfoJson]("system_info").toFuture.onComplete {
            case Success(info) =>
              dispatch(Msg.LocaleLoaded(Nullable.toOption(info.locale)))
            case Failure(_) => ()
          }
        }
        () => ()
      },
      Seq.empty
    )

    view(props, model, dispatch)
  }

  private def propsFromUrl(url: URL): Option[Props] = {
    val params = queryParams(url)
    for {
      browserShell <- params.get("browserShell")
      if browserShell == "1"
      contentLabel <- params.get("contentLabel")
      initialUrl <- params.get("initialUrl")
    } yield Props(contentLabel, initialUrl, params.get("locale"))
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

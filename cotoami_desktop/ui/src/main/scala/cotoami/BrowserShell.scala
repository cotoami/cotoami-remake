package cotoami

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import scala.scalajs.js.Thenable.Implicits._
import scala.util.{Failure, Success}

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import slinky.core._
import slinky.core.facade.Hooks._
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.web.ReactDOMClient
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.Browser
import marubinotto.libs.tauri

object BrowserShell {

  private val ToolbarHeight = 58.0
  private val ResizeDebounceMs = 50.0

  @js.native
  trait BrowserViewStateJson extends js.Object {
    val url: String = js.native
    val title: js.UndefOr[String] = js.native
    val is_loading: Boolean = js.native
  }

  @js.native
  trait BrowserStateEventJson extends BrowserViewStateJson {
    val content_label: String = js.native
  }

  private case class Props(contentLabel: String, initialUrl: String)

  def isCurrentWindow: Boolean =
    queryParams
      .get("browserShell")
      .contains("1") && queryParams.contains("contentLabel") &&
      queryParams.contains("initialUrl")

  def mount(container: dom.Element): Unit =
    propsFromLocation.foreach(props =>
      ReactDOMClient.createRoot(container).render(component(props))
    )

  private def propsFromLocation: Option[Props] =
    for {
      contentLabel <- queryParams.get("contentLabel")
      initialUrl <- queryParams.get("initialUrl")
    } yield Props(contentLabel, initialUrl)

  private def queryParams: Map[String, String] =
    Option(dom.window.location.search).toSeq
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

  private def normalizeUrl(input: String): Option[String] = {
    val trimmed = input.trim
    if (trimmed.isEmpty()) None
    else {
      val candidate =
        if (trimmed.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*$")) trimmed
        else s"https://${trimmed}"
      try {
        val normalized = new dom.URL(candidate).href
        Option.when(tauri.isSupportedBrowserUrl(normalized))(normalized)
      } catch {
        case _: Throwable => None
      }
    }
  }

  private def applyState(
      state: BrowserViewStateJson,
      setActualUrl: String => Unit,
      setDraftUrl: String => Unit,
      setLoading: Boolean => Unit,
      editingRef: ReactRef[Boolean]
  ): Unit = {
    setActualUrl(state.url)
    if (!editingRef.current)
      setDraftUrl(state.url)
    setLoading(state.is_loading)
  }

  private def component = FunctionalComponent[Props] { props =>
    val (actualUrl, setActualUrlRaw) = useState(props.initialUrl)
    val (draftUrl, setDraftUrlRaw) = useState(props.initialUrl)
    val (loading, setLoadingRaw) = useState(true)
    val (error, setErrorRaw) = useState(Option.empty[String])
    val browserAttachedRef = useRef(false)
    val editingRef = useRef(false)
    val toolbarRef = useRef[html.Element](null)
    val windowViewportInsetTopRef = useRef(0.0)
    val (toolbarHeight, setToolbarHeightRaw) = useState(ToolbarHeight)

    def setActualUrl(url: String): Unit =
      setActualUrlRaw(_ => url)

    def setDraftUrl(url: String): Unit =
      setDraftUrlRaw(_ => url)

    def setLoading(value: Boolean): Unit =
      setLoadingRaw(_ => value)

    def setError(value: Option[String]): Unit =
      setErrorRaw(_ => value)

    def setToolbarHeight(height: Double): Unit =
      setToolbarHeightRaw(current =>
        if ((current - height).abs < 0.5) current else height
      )

    def currentToolbarHeight: Double =
      Option(toolbarRef.current)
        .map(_.getBoundingClientRect().height)
        .filter(_ > 0)
        .getOrElse(toolbarHeight)

    def currentWindowViewportInsetTop: Double =
      windowViewportInsetTopRef.current

    def refreshWindowViewportInset(onComplete: => Unit = ()): Unit = {
      val currentWindow = tauri.webviewWindow.getCurrentWebviewWindow()
      currentWindow.scaleFactor().toFuture.flatMap(scaleFactor =>
        currentWindow.innerSize().toFuture.map(inner => {
          val innerLogical = inner.toLogical(scaleFactor)
          (innerLogical.height - dom.window.innerHeight.toDouble).max(0.0)
        })
      ).onComplete {
        case Success(viewportInsetTop) =>
          windowViewportInsetTopRef.current = viewportInsetTop
          onComplete
        case Failure(_) =>
          onComplete
      }
    }

    def browserBoundsArgs: js.Object = {
      val measuredToolbarHeight = currentToolbarHeight
      jso(
        contentLabel = props.contentLabel,
        x = 0,
        y = measuredToolbarHeight + currentWindowViewportInsetTop,
        width = dom.window.innerWidth.toDouble,
        height =
          (dom.window.innerHeight.toDouble - measuredToolbarHeight).max(1.0)
      )
    }

    def handleFailure(context: String)(throwable: Throwable): Unit = {
      setLoading(false)
      setError(Some(s"${context}: ${throwable.getMessage()}"))
    }

    def resizeBrowserView(): Unit =
      if (browserAttachedRef.current) {
        tauri.core
          .invoke[BrowserViewStateJson]("browser_resize", browserBoundsArgs)
          .toFuture
          .onComplete {
            case Success(state) =>
              applyState(
                state,
                setActualUrl,
                setDraftUrl,
                setLoading,
                editingRef
              )
            case Failure(throwable) =>
              handleFailure("Couldn't resize the browser view")(throwable)
          }
      }

    def attachBrowserView(): Unit =
      tauri.core
        .invoke[BrowserViewStateJson](
          "browser_attach",
          js.Object.assign(
            browserBoundsArgs,
            jso(initialUrl = props.initialUrl)
          )
        )
        .toFuture
        .onComplete {
          case Success(state) =>
            browserAttachedRef.current = true
            setError(None)
            applyState(
              state,
              setActualUrl,
              setDraftUrl,
              setLoading,
              editingRef
            )
          case Failure(throwable) =>
            handleFailure("Couldn't open the browser view")(throwable)
        }

    def invokeBrowserCommand(command: String, args: js.Object = jso()): Unit =
      tauri.core
        .invoke[BrowserViewStateJson](
          command,
          js.Object.assign(jso(contentLabel = props.contentLabel), args)
        )
        .toFuture
        .onComplete {
          case Success(state) =>
            setError(None)
            applyState(
              state,
              setActualUrl,
              setDraftUrl,
              setLoading,
              editingRef
            )
          case Failure(throwable) =>
            handleFailure("Browser command failed")(throwable)
        }

    def submitAddressBar(): Unit =
      normalizeUrl(draftUrl) match {
        case Some(url) =>
          setActualUrl(url)
          setDraftUrl(url)
          setLoading(true)
          setError(None)
          invokeBrowserCommand("browser_navigate", jso(url = url))
        case None =>
          setError(Some("Enter a valid http:// or https:// URL."))
      }

    useEffect(
      () => {
        val toolbar = toolbarRef.current
        if (toolbar == null) () => ()
        else {
          val updateToolbarHeight = () => {
            val measuredHeight = toolbar.getBoundingClientRect().height
            if (measuredHeight > 0)
              setToolbarHeight(measuredHeight)
          }

          updateToolbarHeight()

          val observer =
            new dom.ResizeObserver((_, _) => updateToolbarHeight())
          observer.observe(toolbar)

          () => observer.disconnect()
        }
      },
      Seq.empty
    )

    useEffect(
      () => {
        var unlisten: Option[js.Function0[Unit]] = None

        tauri.event
          .listen[BrowserStateEventJson](
            "browser-state",
            event =>
              Option(event.payload)
                .filter(_.content_label == props.contentLabel)
                .foreach(payload => {
                  setError(None)
                  applyState(
                    payload,
                    setActualUrl,
                    setDraftUrl,
                    setLoading,
                    editingRef
                  )
                })
          )
          .toFuture
          .onComplete {
            case Success(unlistenFn) => unlisten = Some(unlistenFn)
            case Failure(throwable)  =>
              handleFailure("Couldn't subscribe to browser events")(throwable)
          }

        val onResize: js.Function1[dom.Event, Unit] =
          Browser.debounce(
            (_: dom.Event) =>
              refreshWindowViewportInset {
                resizeBrowserView()
              },
            ResizeDebounceMs
          )

        dom.window.addEventListener("resize", onResize)
        refreshWindowViewportInset {
          attachBrowserView()
        }

        () => {
          browserAttachedRef.current = false
          dom.window.removeEventListener("resize", onResize)
          unlisten.foreach(_())
        }
      },
      Seq(props.contentLabel)
    )

    useEffect(
      () => {
        resizeBrowserView()
        () => ()
      },
      Seq(props.contentLabel, toolbarHeight)
    )

    val secure = actualUrl.startsWith("https://")
    val addressIcon = if (secure) "lock" else "language"

    div(className := "browser-shell")(
      header(className := "browser-toolbar", ref := toolbarRef)(
        div(className := "browser-toolbar-main")(
          div(className := "browser-actions")(
            button(
              className := "browser-action",
              `type` := "button",
              title := "Back",
              onClick := (_ => {
                setLoading(true)
                invokeBrowserCommand("browser_go_back")
              })
            )(materialSymbol("arrow_back")),
            button(
              className := "browser-action",
              `type` := "button",
              title := "Forward",
              onClick := (_ => {
                setLoading(true)
                invokeBrowserCommand("browser_go_forward")
              })
            )(materialSymbol("arrow_forward")),
            button(
              className := "browser-action",
              `type` := "button",
              title := "Reload",
              onClick := (_ => {
                setLoading(true)
                invokeBrowserCommand("browser_reload")
              })
            )(
              materialSymbol(
                if (loading) "progress_activity" else "refresh",
                if (loading) "loading" else ""
              )
            )
          ),
          form(
            className := "browser-address-bar",
            onSubmit := (e => {
              e.preventDefault()
              submitAddressBar()
            })
          )(
            span(className := s"address-status ${
                if (secure) "secure" else "insecure"
              }")(
              materialSymbol(addressIcon)
            ),
            input(
              className := "browser-address-input",
              `type` := "text",
              value := draftUrl,
              spellCheck := false,
              onFocus := (_ => editingRef.current = true),
              onBlur := (_ => {
                editingRef.current = false
                setDraftUrl(actualUrl)
              }),
              onChange := (e => setDraftUrl(e.target.value))
            ),
            button(
              className := "browser-action go",
              `type` := "submit",
              title := "Go"
            )(materialSymbol("arrow_outward"))
          )
        ),
        error.map(message => div(className := "browser-error")(message))
      ),
      main(className := "browser-surface")(
        div(className := "browser-webview-placeholder")(
          if (loading) "Loading page..." else "Page ready."
        )
      )
    )
  }
}

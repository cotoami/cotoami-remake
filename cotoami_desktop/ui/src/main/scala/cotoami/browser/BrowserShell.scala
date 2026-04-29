package cotoami.browser

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import scala.scalajs.js.URIUtils
import scala.util.{Failure, Success}

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import slinky.core._
import slinky.core.facade.Hooks._
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.{
  CollapseDirection,
  SplitPane,
  materialSymbol,
  paneToggle
}
import marubinotto.libs.tauri

import cotoami.i18n.Text

object BrowserShell {

  final val TimelinePaneName = "BrowserTimeline"
  final val DefaultTimelineWidth = 380

  private val ToolbarHeight = 54.0
  private val ResizeSettleMs = 50.0
  private val ExplicitSchemePattern = "^[A-Za-z][A-Za-z0-9+.-]*:.*$".r
  private val Ipv4AddressPattern = raw"^\d{1,3}(?:\.\d{1,3}){3}$$".r

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

  case class Props(
      contentLabel: String,
      initialUrl: String,
      model: App.Model,
      text: Text,
      timeline: Option[ReactElement],
      cotonomaSelect: Option[ReactElement],
      timelineOpened: Boolean,
      timelineWidth: Int,
      onTimelineOpenChange: Boolean => Unit,
      onTimelineWidthChange: Int => Unit,
      onStateChange: (String, Option[String]) => Unit
  )

  private def normalizeUrl(input: String): Option[String] = {
    val trimmed = input.trim
    if (trimmed.isEmpty()) None
    else {
      val candidate =
        if (ExplicitSchemePattern.matches(trimmed)) trimmed
        else s"https://${trimmed}"
      try {
        val normalized = new dom.URL(candidate).href
        Option.when(tauri.isSupportedBrowserUrl(normalized))(normalized)
      } catch {
        case _: Throwable => None
      }
    }
  }

  private def duckDuckGoSearchUrl(query: String): String =
    s"https://duckduckgo.com/?q=${URIUtils.encodeURIComponent(query)}"

  private def looksLikeUrlHost(hostname: String): Boolean = {
    val trimmed = hostname.trim
    trimmed.equalsIgnoreCase("localhost") ||
    trimmed.contains(".") ||
    trimmed.contains(":") ||
    Ipv4AddressPattern.matches(trimmed)
  }

  private def resolveAddressBarInput(input: String): Option[String] = {
    val trimmed = input.trim
    if (trimmed.isEmpty()) None
    else if (ExplicitSchemePattern.matches(trimmed))
      normalizeUrl(trimmed)
    else if (trimmed.exists(_.isWhitespace))
      Some(duckDuckGoSearchUrl(trimmed))
    else
      normalizeUrl(trimmed)
        .filter(url =>
          try {
            looksLikeUrlHost(new dom.URL(url).hostname)
          } catch {
            case _: Throwable => false
          }
        )
        .orElse(Some(duckDuckGoSearchUrl(trimmed)))
  }

  private def optionString(value: js.UndefOr[String]): Option[String] =
    value.toOption.flatMap(Option(_))

  private def windowTitle(url: String, pageTitle: Option[String]): String =
    pageTitle
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse {
        try {
          Option(new dom.URL(url).host).filter(_.nonEmpty)
        } catch {
          case _: Throwable => None
        }
      }
      .getOrElse(url)

  private def applyState(
      state: BrowserViewStateJson,
      setActualUrl: String => Unit,
      setDraftUrl: String => Unit,
      setTitle: Option[String] => Unit,
      setLoading: Boolean => Unit,
      editingRef: ReactRef[Boolean],
      currentWindowTitleRef: ReactRef[String]
  ): Unit = {
    val title = optionString(state.title)
    setActualUrl(state.url)
    if (!editingRef.current)
      setDraftUrl(state.url)
    setTitle(title)
    setLoading(state.is_loading)
    val nextWindowTitle = windowTitle(state.url, title)
    if (currentWindowTitleRef.current != nextWindowTitle) {
      currentWindowTitleRef.current = nextWindowTitle
      tauri.window.getCurrentWindow().setTitle(nextWindowTitle)
      ()
    }
  }

  val component = FunctionalComponent[Props] { props =>
    val (actualUrl, setActualUrlRaw) = useState(props.initialUrl)
    val (draftUrl, setDraftUrlRaw) = useState(props.initialUrl)
    val (pageTitle, setTitleRaw) = useState(props.model.title)
    val (loading, setLoadingRaw) = useState(true)
    val (error, setErrorRaw) = useState(Option.empty[String])
    val browserAttachedRef = useRef(false)
    val resizeInFlightRef = useRef(false)
    val pendingResizeRef = useRef(false)
    val editingRef = useRef(false)
    val windowTitleRef = useRef(windowTitle(props.initialUrl, props.model.title))
    val toolbarRef = useRef[html.Element](null)
    val webviewSlotRef = useRef[html.Element](null)
    val windowViewportInsetTopRef = useRef(0.0)
    val (toolbarHeight, setToolbarHeightRaw) = useState(ToolbarHeight)

    def setActualUrl(url: String): Unit =
      setActualUrlRaw(_ => url)

    def setDraftUrl(url: String): Unit =
      setDraftUrlRaw(_ => url)

    def setTitle(title: Option[String]): Unit =
      setTitleRaw(_ => title)

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

    def refreshWindowViewportInset(onComplete: => Unit): Unit = {
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
      val slotBounds =
        Option(webviewSlotRef.current).map(_.getBoundingClientRect())
      jso(
        contentLabel = props.contentLabel,
        x = slotBounds.map(_.left).getOrElse(0.0),
        y = slotBounds
          .map(_.top + currentWindowViewportInsetTop)
          .getOrElse(measuredToolbarHeight + currentWindowViewportInsetTop),
        width = slotBounds.map(_.width).getOrElse(dom.window.innerWidth.toDouble),
        height = slotBounds
          .map(_.height)
          .getOrElse(
            (dom.window.innerHeight.toDouble - measuredToolbarHeight).max(1.0)
          )
      )
    }

    def handleFailure(context: String)(throwable: Throwable): Unit = {
      setLoading(false)
      setError(
        Some(
          Option(throwable.getMessage())
            .filter(_.trim.nonEmpty)
            .map(message => s"${context}: ${message}")
            .getOrElse(context)
        )
      )
    }

    def flushBrowserResize(): Unit =
      if (browserAttachedRef.current && !resizeInFlightRef.current) {
        resizeInFlightRef.current = true
        tauri.core
          .invoke[BrowserViewStateJson]("browser_resize", browserBoundsArgs)
          .toFuture
          .onComplete {
            case Success(state) =>
              resizeInFlightRef.current = false
              applyState(
                state,
                setActualUrl,
                setDraftUrl,
                setTitle,
                setLoading,
                editingRef,
                windowTitleRef
              )
              if (pendingResizeRef.current) {
                pendingResizeRef.current = false
                flushBrowserResize()
              }
            case Failure(throwable) =>
              resizeInFlightRef.current = false
              pendingResizeRef.current = false
              handleFailure("Couldn't resize the browser view")(throwable)
          }
      }

    def resizeBrowserView(): Unit =
      if (browserAttachedRef.current) {
        pendingResizeRef.current = true
        flushBrowserResize()
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
              setTitle,
              setLoading,
              editingRef,
              windowTitleRef
            )
            resizeBrowserView()
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
              setTitle,
              setLoading,
              editingRef,
              windowTitleRef
            )
          case Failure(throwable) =>
            handleFailure("Browser command failed")(throwable)
        }

    def submitAddressBar(): Unit =
      resolveAddressBarInput(draftUrl) match {
        case Some(url) =>
          setActualUrl(url)
          setDraftUrl(url)
          setLoading(true)
          setError(None)
          invokeBrowserCommand("browser_navigate", jso(url = url))
        case None =>
          setError(Some(props.text.BrowserShell_invalidUrl))
      }

    useEffect(
      () => {
        props.onStateChange(actualUrl, pageTitle)
        () => ()
      },
      Seq(actualUrl, pageTitle.orNull)
    )

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
        var disposed = false
        var resizeAnimationFrameId: Option[Int] = None
        var resizeSettleTimeoutId: Option[Int] = None

        val scheduleBrowserResize = () =>
          if (resizeAnimationFrameId.isEmpty) {
            resizeAnimationFrameId = Some(
              dom.window.requestAnimationFrame(_ => {
                resizeAnimationFrameId = None
                resizeBrowserView()
              })
            )
          }

        val scheduleViewportInsetRefresh = () => {
          resizeSettleTimeoutId.foreach(dom.window.clearTimeout)
          resizeSettleTimeoutId = Some(
            dom.window.setTimeout(
              () => {
                resizeSettleTimeoutId = None
                refreshWindowViewportInset {
                  resizeBrowserView()
                }
              },
              ResizeSettleMs
            )
          )
        }

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
                    setTitle,
                    setLoading,
                    editingRef,
                    windowTitleRef
                  )
                })
          )
          .toFuture
          .onComplete {
            case Success(unlistenFn) =>
              if (disposed)
                unlistenFn()
              else {
                unlisten = Some(unlistenFn)
                refreshWindowViewportInset {
                  attachBrowserView()
                }
              }
            case Failure(throwable) =>
              handleFailure("Couldn't subscribe to browser events")(throwable)
          }

        val onResize: js.Function1[dom.Event, Unit] =
          (_: dom.Event) => {
            scheduleBrowserResize()
            scheduleViewportInsetRefresh()
          }

        dom.window.addEventListener("resize", onResize)

        () => {
          disposed = true
          browserAttachedRef.current = false
          resizeInFlightRef.current = false
          pendingResizeRef.current = false
          resizeAnimationFrameId.foreach(dom.window.cancelAnimationFrame)
          resizeSettleTimeoutId.foreach(dom.window.clearTimeout)
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
      Seq(
        props.contentLabel,
        toolbarHeight,
        props.timelineOpened,
        props.timelineWidth
      )
    )

    useEffect(
      () => {
        val slot = webviewSlotRef.current
        if (slot == null) () => ()
        else {
          var resizeAnimationFrameId: Option[Int] = None

          val scheduleBrowserResize = () =>
            if (resizeAnimationFrameId.isEmpty) {
              resizeAnimationFrameId = Some(
                dom.window.requestAnimationFrame(_ => {
                  resizeAnimationFrameId = None
                  resizeBrowserView()
                })
              )
            }

          val observer =
            new dom.ResizeObserver((_, _) => scheduleBrowserResize())
          observer.observe(slot)
          scheduleBrowserResize()

          () => {
            resizeAnimationFrameId.foreach(dom.window.cancelAnimationFrame)
            observer.disconnect()
          }
        }
      },
      Seq(props.contentLabel, props.timelineOpened, props.timelineWidth)
    )

    val secure = actualUrl.startsWith("https://")
    val addressIcon = if (secure) "lock" else "language"

    val browserWebviewSlot =
      div(className := "browser-webview-slot", ref := webviewSlotRef)(
        div(className := "browser-webview-placeholder")(
          if (loading) props.text.BrowserShell_loadingPage
          else props.text.BrowserShell_pageReady
        )
      )

    val browserSurfaceContent =
      props.timeline.map(timeline =>
        SplitPane(
          vertical = true,
          reverse = true,
          initialPrimarySize = props.timelineWidth,
          resizable = props.timelineOpened,
          className = Some("browser-main"),
          onResizeEnd = Some(() => resizeBrowserView()),
          onPrimarySizeChanged = Some(props.onTimelineWidthChange),
          primary = SplitPane.Primary.Props(
            className = Some(
              optionalClasses(
                Seq(
                  ("browser-timeline", true),
                  ("pane", true),
                  ("folded", !props.timelineOpened)
                )
              )
            ),
            onClick = Option.when(!props.timelineOpened) { () =>
              props.onTimelineOpenChange(true)
            }
          )(
            paneToggle(
              onFoldClick = () => props.onTimelineOpenChange(false),
              onUnfoldClick = () => props.onTimelineOpenChange(true),
              direction = CollapseDirection.ToRight
            ),
            props.cotonomaSelect.map(select =>
              div(className := "browser-timeline-focus")(select)
            ),
            timeline
          ),
          secondary = SplitPane.Secondary.Props(
            className = Some("browser-content")
          )(
            browserWebviewSlot
          )
        ).withKey(s"${props.timelineOpened}-${props.timelineWidth}")
      ).getOrElse(browserWebviewSlot)

    div(className := "browser-shell")(
      header(className := "browser-toolbar", ref := toolbarRef)(
        div(className := "browser-toolbar-main")(
          div(className := "browser-actions")(
            button(
              className := "browser-action",
              `type` := "button",
              title := props.text.Back,
              onClick := (_ => {
                invokeBrowserCommand("browser_go_back")
              })
            )(materialSymbol("arrow_back")),
            button(
              className := "browser-action",
              `type` := "button",
              title := props.text.BrowserShell_forward,
              onClick := (_ => {
                invokeBrowserCommand("browser_go_forward")
              })
            )(materialSymbol("arrow_forward")),
            button(
              className := "browser-action",
              `type` := "button",
              title := props.text.BrowserShell_reload,
              onClick := (_ => {
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
              title := props.text.BrowserShell_go,
              onMouseDown := (e => e.preventDefault())
            )(materialSymbol("arrow_outward"))
          ),
          Option.when(props.timeline.nonEmpty && !props.timelineOpened) {
            button(
              className := "browser-action cotoami-timeline-toggle",
              `type` := "button",
              title := "Cotoami timeline",
              onClick := (_ => props.onTimelineOpenChange(true))
            )(
              img(
                alt := "",
                src := "/images/logo/logomark.svg"
              )
            )
          }
        ),
        error.map(message => div(className := "browser-error")(message))
      ),
      main(className := "browser-surface")(
        browserSurfaceContent
      )
    )
  }
}

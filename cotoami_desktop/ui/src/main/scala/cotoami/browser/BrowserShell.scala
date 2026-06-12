package cotoami.browser

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import scala.scalajs.js.JSConverters._
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

  enum Mode {
    case Standalone, Embedded
  }

  final val TimelinePaneName = "BrowserTimeline"
  final val DefaultTimelineWidth = 380
  private val DefaultTrailWidth = 340

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

  @js.native
  trait SelectionRectJson extends js.Object {
    val x: Double = js.native
    val y: Double = js.native
    val width: Double = js.native
    val height: Double = js.native
  }

  @js.native
  trait SelectionStateEventJson extends js.Object {
    val content_label: String = js.native
    val url: String = js.native
    val has_selection: Boolean = js.native
    val rect: js.UndefOr[SelectionRectJson] = js.native
  }

  @js.native
  trait ScrollStateEventJson extends js.Object {
    val content_label: String = js.native
    val url: String = js.native
    val x: Double = js.native
    val y: Double = js.native
  }

  @js.native
  trait SelectionCaptureEventJson extends js.Object {
    val content_label: String = js.native
    val request_id: js.UndefOr[String] = js.native
    val action: js.UndefOr[String] = js.native
    val url: String = js.native
    val title: js.UndefOr[String] = js.native
    val selected_text: String = js.native
    val selected_html: String = js.native
    val has_selection: Boolean = js.native
    val rect: js.UndefOr[SelectionRectJson] = js.native
  }

  case class SelectionRect(x: Double, y: Double, width: Double, height: Double)
  case class SelectionState(url: String, rect: SelectionRect)
  case class SelectionCapture(
      url: String,
      title: Option[String],
      selectedText: String,
      selectedHtml: String,
      action: String
  )
  private case class PendingScrollRestore(url: String, x: Double, y: Double)
  case class InitialScrollPosition(x: Double, y: Double)

  case class Props(
      contentLabel: String,
      initialUrl: Option[String],
      app: cotoami.Model,
      title: Option[String],
      mode: Mode,
      layoutKey: String,
      text: Text,
      timeline: Option[ReactElement],
      cotonomaSelect: Option[ReactElement],
      trail: (
          String,
          () => Unit,
          BrowserTrail.NavigationRequest => Unit
      ) => ReactElement,
      downloads: (() => Unit) => ReactElement,
      downloadsVisible: Boolean,
      downloadsBusy: Boolean,
      downloadsOpenRequest: Int,
      navigationRequest: Int,
      nativeDetachRequest: Int,
      nativeAttachRequest: Int,
      initialScrollPosition: Option[InitialScrollPosition],
      timelineOpened: Boolean,
      timelineWidth: Int,
      onTimelineOpenChange: Boolean => Unit,
      onTimelineWidthChange: Int => Unit,
      onStateChange: (String, Option[String]) => Unit,
      onScrollPositionChange: (String, Double, Double) => Unit,
      canClipSelection: Boolean,
      onClipSelection: SelectionCapture => Unit,
      onPostSelection: SelectionCapture => Unit,
      onOpenAsWindow: Option[() => Unit],
      onClose: Option[() => Unit]
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

  private def isSecureUrl(url: String): Boolean =
    try {
      new dom.URL(url).protocol == "https:"
    } catch {
      case _: Throwable => false
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
    if (url.isBlank())
      "Browser"
    else
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

  private def applyStateUrl(url: String): String =
    if (url == "about:blank" || url.endsWith("/browser-blank.html")) ""
    else url

  private def applyState(
      state: BrowserViewStateJson,
      setActualUrl: String => Unit,
      setDraftUrl: String => Unit,
      setTitle: Option[String] => Unit,
      setLoading: Boolean => Unit,
      editingRef: ReactRef[Boolean],
      currentWindowTitleRef: ReactRef[String],
      updateWindowTitle: Boolean,
      onStateChange: (String, Option[String]) => Unit
  ): Unit = {
    val title = optionString(state.title)
    val url = applyStateUrl(state.url)
    setActualUrl(url)
    if (!editingRef.current)
      setDraftUrl(url)
    setTitle(title)
    setLoading(state.is_loading)
    val nextWindowTitle = windowTitle(url, title)
    if (updateWindowTitle && currentWindowTitleRef.current != nextWindowTitle) {
      currentWindowTitleRef.current = nextWindowTitle
      tauri.window.getCurrentWindow().setTitle(nextWindowTitle)
      ()
    }
    onStateChange(url, title)
  }

  val component = FunctionalComponent[Props] { props =>
    val initialUrl = props.initialUrl.getOrElse("")
    val (actualUrl, setActualUrlRaw) = useState(initialUrl)
    val (draftUrl, setDraftUrlRaw) = useState(initialUrl)
    val (_, setTitleRaw) = useState(props.title)
    val (loading, setLoadingRaw) = useState(props.initialUrl.nonEmpty)
    val (error, setErrorRaw) = useState(Option.empty[String])
    val (_, setSelectionStateRaw) =
      useState(Option.empty[SelectionState])
    val (trailOpen, setTrailOpenRaw) = useState(false)
    val (downloadsOpen, setDownloadsOpenRaw) = useState(false)
    val browserAttachedRef = useRef(false)
    val browserClosingRef = useRef(false)
    val resizeInFlightRef = useRef(false)
    val pendingResizeRef = useRef(false)
    val pendingSelectionCaptureRef = useRef(Option.empty[String])
    val pendingScrollRestoreRef =
      useRef(Option.empty[PendingScrollRestore])
    val editingRef = useRef(false)
    val windowTitleRef = useRef(windowTitle(initialUrl, props.title))
    val toolbarRef = useRef[html.Element](null)
    val webviewSlotRef = useRef[html.Element](null)
    val windowViewportInsetTopRef = useRef(0.0)
    val (toolbarHeight, setToolbarHeightRaw) = useState(ToolbarHeight)
    val currentTheme = props.app.uiState.map(_.theme).getOrElse("dark")
    val standalone = props.mode == Mode.Standalone

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

    def setSelectionState(value: Option[SelectionState]): Unit =
      setSelectionStateRaw(_ => value)

    def setContentClipOverlay(
        visible: Boolean,
        rect: Option[SelectionRect] = None
    ): Unit =
      tauri.core
        .invoke[Unit](
          "browser_set_selection_clip_overlay",
          jso(
            contentLabel = props.contentLabel,
            visible = visible,
            clipLabel = props.text.BrowserShell_clipSelection,
            postLabel = props.text.Post,
            rect = rect
              .map(rect =>
                jso(
                  x = rect.x,
                  y = rect.y,
                  width = rect.width,
                  height = rect.height
                )
              )
              .orUndefined
          )
        )
        .toFuture
        .failed
        .foreach(handleFailure("Couldn't update the browser clip button"))

    def clearSelectionState(): Unit = {
      setContentClipOverlay(false)
      setSelectionState(None)
      pendingSelectionCaptureRef.current = None
    }

    def setTrailOpen(value: Boolean): Unit =
      setTrailOpenRaw(_ => value)

    def setDownloadsOpen(value: Boolean): Unit =
      setDownloadsOpenRaw(_ => value)

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
                windowTitleRef,
                standalone,
                props.onStateChange
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

    def resizeBrowserViewAfterLayout(): Unit =
      dom.window.requestAnimationFrame(_ =>
        dom.window.requestAnimationFrame(_ => resizeBrowserView())
      )

    def attachBrowserView(): Unit =
      tauri.core
        .invoke[BrowserViewStateJson](
          "browser_attach",
          js.Object.assign(
            browserBoundsArgs,
            jso(
              initialUrl = props.initialUrl.orUndefined,
              theme = currentTheme
            )
          )
        )
        .toFuture
        .onComplete {
          case Success(state) =>
            browserAttachedRef.current = true
            browserClosingRef.current = false
            setError(None)
            val stateUrl = applyStateUrl(state.url)
            if (initialUrl.nonEmpty && stateUrl == initialUrl)
              props.initialScrollPosition.foreach(scroll =>
                pendingScrollRestoreRef.current =
                  Some(PendingScrollRestore(stateUrl, scroll.x, scroll.y))
              )
            applyState(
              state,
              setActualUrl,
              setDraftUrl,
              setTitle,
              setLoading,
              editingRef,
              windowTitleRef,
              standalone,
              props.onStateChange
            )
            restorePendingScroll(state)
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
              windowTitleRef,
              standalone,
              props.onStateChange
            )
          case Failure(throwable) =>
            handleFailure("Browser command failed")(throwable)
        }

    def closeBrowserView(reportFailure: Boolean = true): Unit =
      if (browserAttachedRef.current && !browserClosingRef.current) {
        browserAttachedRef.current = false
        browserClosingRef.current = true
        tauri.core
          .invoke[Unit](
            "browser_close",
            jso(contentLabel = props.contentLabel)
          )
          .toFuture
          .failed
          .foreach(throwable =>
            if (reportFailure) {
              browserClosingRef.current = false
              handleFailure("Couldn't close the browser view")(throwable)
            }
          )
      }

    def restoreScrollPosition(x: Double, y: Double): Unit =
      tauri.core
        .invoke[Unit](
          "browser_restore_scroll",
          jso(
            contentLabel = props.contentLabel,
            x = x,
            y = y
          )
        )
        .toFuture
        .failed
        .foreach(handleFailure("Couldn't restore the browser scroll position"))

    def restorePendingScroll(state: BrowserViewStateJson): Unit =
      pendingScrollRestoreRef.current.foreach { pending =>
        val url = applyStateUrl(state.url)
        if (!state.is_loading && url == pending.url) {
          pendingScrollRestoreRef.current = None
          dom.window.requestAnimationFrame(_ =>
            dom.window.requestAnimationFrame(_ =>
              restoreScrollPosition(pending.x, pending.y)
            )
          )
        }
      }

    def submitAddressBar(): Unit =
      resolveAddressBarInput(draftUrl) match {
        case Some(url) =>
          setActualUrl(url)
          clearSelectionState()
          setDraftUrl(url)
          setLoading(true)
          setError(None)
          invokeBrowserCommand("browser_navigate", jso(url = url))
        case None =>
          setError(Some(props.text.BrowserShell_invalidUrl))
      }

    def navigateToTrail(request: BrowserTrail.NavigationRequest): Unit = {
      pendingScrollRestoreRef.current =
        Some(PendingScrollRestore(request.url, request.scrollX, request.scrollY))
      setActualUrl(request.url)
      clearSelectionState()
      setDraftUrl(request.url)
      setLoading(true)
      setError(None)
      invokeBrowserCommand("browser_navigate", jso(url = request.url))
    }

    useEffect(
      () => {
        props.initialUrl
          .filter(url => props.navigationRequest > 0 && url.nonEmpty)
          .filter(tauri.isSupportedBrowserUrl)
          .foreach(url => {
            setActualUrl(url)
            clearSelectionState()
            setDraftUrl(url)
            setLoading(true)
            setError(None)
            invokeBrowserCommand("browser_navigate", jso(url = url))
          })
        () => ()
      },
      Seq(props.navigationRequest)
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
        if (props.nativeDetachRequest > 0)
          closeBrowserView()
        () => ()
      },
      Seq(props.nativeDetachRequest)
    )

    useEffect(
      () => {
        if (props.nativeAttachRequest > 0 && !browserAttachedRef.current)
          refreshWindowViewportInset {
            attachBrowserView()
          }
        () => ()
      },
      Seq(props.nativeAttachRequest)
    )

    useEffect(
      () => {
        val closeOnUnload: js.Function1[dom.Event, Unit] =
          (_: dom.Event) => closeBrowserView(reportFailure = false)

        dom.window.addEventListener("pagehide", closeOnUnload)
        dom.window.addEventListener("beforeunload", closeOnUnload)

        () => {
          dom.window.removeEventListener("pagehide", closeOnUnload)
          dom.window.removeEventListener("beforeunload", closeOnUnload)
        }
      },
      Seq(props.contentLabel)
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
                  clearSelectionState()
                  applyState(
                    payload,
                    setActualUrl,
                    setDraftUrl,
                    setTitle,
                    setLoading,
                    editingRef,
                    windowTitleRef,
                    standalone,
                    props.onStateChange
                  )
                  restorePendingScroll(payload)
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
            clearSelectionState()
            scheduleBrowserResize()
            scheduleViewportInsetRefresh()
          }

        dom.window.addEventListener("resize", onResize)

        () => {
          disposed = true
          closeBrowserView()
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
        var unlistenScrollState: Option[js.Function0[Unit]] = None
        var disposed = false

        tauri.event
          .listen[ScrollStateEventJson](
            "browser-scroll-state",
            event =>
              Option(event.payload)
                .filter(payload =>
                  payload.content_label == props.contentLabel &&
                    payload.url == actualUrl
                )
                .foreach(payload =>
                  props.onScrollPositionChange(payload.url, payload.x, payload.y)
                )
          )
          .toFuture
          .onComplete {
            case Success(unlistenFn) =>
              if (disposed)
                unlistenFn()
              else
                unlistenScrollState = Some(unlistenFn)
            case Failure(throwable) =>
              handleFailure("Couldn't subscribe to browser scroll events")(
                throwable
              )
          }

        () => {
          disposed = true
          unlistenScrollState.foreach(_())
        }
      },
      Seq(props.contentLabel, actualUrl)
    )

    useEffect(
      () => {
        var unlistenSelectionState: Option[js.Function0[Unit]] = None
        var unlistenSelectionCapture: Option[js.Function0[Unit]] = None
        var disposed = false

        tauri.event
          .listen[SelectionStateEventJson](
            "browser-selection-state",
            event =>
              Option(event.payload)
                .filter(_.content_label == props.contentLabel)
                .foreach(payload => {
                  val rect = payload.rect.toOption
                  if (
                    props.canClipSelection &&
                    payload.has_selection &&
                    payload.url == actualUrl &&
                    rect.isDefined
                  ) {
                    val selectionRect =
                      SelectionRect(
                        rect.get.x,
                        rect.get.y,
                        rect.get.width,
                        rect.get.height
                      )
                    setSelectionState(
                      Some(SelectionState(payload.url, selectionRect))
                    )
                    setContentClipOverlay(true, Some(selectionRect))
                  } else
                    clearSelectionState()
                })
          )
          .toFuture
          .onComplete {
            case Success(unlistenFn) =>
              if (disposed)
                unlistenFn()
              else
                unlistenSelectionState = Some(unlistenFn)
            case Failure(throwable) =>
              handleFailure("Couldn't subscribe to browser selection events")(
                throwable
              )
          }

        tauri.event
          .listen[SelectionCaptureEventJson](
            "browser-selection-capture",
            event =>
              Option(event.payload)
                .filter(_.content_label == props.contentLabel)
                .foreach(payload => {
                  val pending = pendingSelectionCaptureRef.current
                  if (
                    pending.isEmpty ||
                    pending.exists(id => payload.request_id.toOption.contains(id))
                  ) {
                    pendingSelectionCaptureRef.current = None
                    if (
                      props.canClipSelection &&
                      payload.has_selection &&
                      payload.url == actualUrl &&
                      !payload.selected_text.trim.isEmpty
                    ) {
                      clearSelectionState()
                      val capture = SelectionCapture(
                        payload.url,
                        optionString(payload.title),
                        payload.selected_text,
                        payload.selected_html,
                        payload.action.toOption.getOrElse("clip")
                      )
                      capture.action match {
                        case "post" => props.onPostSelection(capture)
                        case _      => props.onClipSelection(capture)
                      }
                    } else
                      clearSelectionState()
                  }
                })
          )
          .toFuture
          .onComplete {
            case Success(unlistenFn) =>
              if (disposed)
                unlistenFn()
              else
                unlistenSelectionCapture = Some(unlistenFn)
            case Failure(throwable) =>
              handleFailure("Couldn't subscribe to browser clip events")(
                throwable
              )
          }

        () => {
          disposed = true
          unlistenSelectionState.foreach(_())
          unlistenSelectionCapture.foreach(_())
        }
      },
      Seq(props.contentLabel, props.canClipSelection, actualUrl)
    )

    useEffect(
      () => {
        resizeBrowserViewAfterLayout()
        () => ()
      },
      Seq(
        props.contentLabel,
        props.layoutKey,
        toolbarHeight,
        trailOpen,
        downloadsOpen,
        props.timelineOpened,
        props.timelineWidth
      )
    )

    useEffect(
      () => {
        if (!props.downloadsVisible)
          setDownloadsOpen(false)
        () => ()
      },
      Seq(props.downloadsVisible)
    )

    useEffect(
      () => {
        if (props.downloadsOpenRequest > 0 && props.downloadsVisible) {
          setTrailOpen(false)
          setDownloadsOpen(true)
        }
        () => ()
      },
      Seq(props.downloadsOpenRequest, props.downloadsVisible)
    )

    useEffect(
      () => {
        if (actualUrl.isBlank())
          tauri.core
            .invoke[Unit](
              "browser_set_blank_theme",
              jso(
                contentLabel = props.contentLabel,
                theme = currentTheme
              )
            )
            .toFuture
            .onComplete(_ => ())
        () => ()
      },
      Seq(props.contentLabel, actualUrl, currentTheme)
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
      Seq(
        props.contentLabel,
        trailOpen,
        downloadsOpen,
        props.timelineOpened,
        props.timelineWidth
      )
    )

    val displayedUrl = if (editingRef.current) draftUrl else actualUrl
    val secure = isSecureUrl(displayedUrl)
    val addressIcon = if (secure) "lock" else "language"

    val browserWebviewSlot =
      div(className := "browser-webview-slot", ref := webviewSlotRef)(
        div(className := "browser-webview-placeholder")(
          if (loading) props.text.BrowserShell_loadingPage
          else props.text.BrowserShell_pageReady
        )
      )

    val browserContent =
      props.timeline.map(timeline =>
        SplitPane(
          vertical = true,
          reverse = true,
          initialPrimarySize = props.timelineWidth,
          resizable = props.timelineOpened,
          className = Some("browser-main"),
          onResizing = Some(_ => resizeBrowserViewAfterLayout()),
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

    val browserSurfaceContent =
      if (trailOpen || downloadsOpen)
        SplitPane(
          vertical = true,
          initialPrimarySize = DefaultTrailWidth,
          resizable = true,
          className = Some("browser-with-trail"),
          onResizing = Some(_ => resizeBrowserViewAfterLayout()),
          onResizeEnd = Some(() => resizeBrowserView()),
          primary = SplitPane.Primary.Props(
            className = Some("browser-trail-sidebar")
          )(
            if (downloadsOpen)
              props.downloads(() => setDownloadsOpen(false))
            else
              props.trail(actualUrl, () => setTrailOpen(false), navigateToTrail)
          ),
          secondary = SplitPane.Secondary.Props(
            className = Some("browser-trail-secondary")
          )(browserContent)
        ).withKey("trail-open")
      else
        browserContent

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
            ),
            div(
              className := "browser-trail-tool"
            )(
              button(
                className := optionalClasses(
                  Seq(
                    ("browser-action", true),
                    ("active", trailOpen)
                  )
                ),
                `type` := "button",
                title := props.text.BrowserShell_trail,
                onClick := (_ => {
                  setDownloadsOpen(false)
                  setTrailOpen(!trailOpen)
                })
              )(materialSymbol("route"))
            ),
            Option.when(props.downloadsVisible) {
              button(
                className := optionalClasses(
                  Seq(
                    ("browser-action", true),
                    ("browser-downloads-button", true),
                    ("active", downloadsOpen),
                    ("busy", props.downloadsBusy)
                  )
                ),
                `type` := "button",
                title := props.text.BrowserShell_downloads,
                onClick := (_ => {
                  setTrailOpen(false)
                  setDownloadsOpen(!downloadsOpen)
                })
              )(
                materialSymbol(
                  if (props.downloadsBusy) "progress_activity" else "download",
                  if (props.downloadsBusy) "loading" else ""
                )
              )
            }
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
              placeholder := props.text.BrowserShell_addressPlaceholder,
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
          Option
            .when(props.onOpenAsWindow.nonEmpty || props.onClose.nonEmpty) {
              div(className := "browser-actions browser-window-actions")(
                props.onOpenAsWindow.map(open =>
                  button(
                    className := "browser-action open-as-window",
                    `type` := "button",
                    title := props.text.BrowserShell_openInWindow,
                    disabled := actualUrl.isBlank(),
                    onClick := (_ => open())
                  )(materialSymbol("open_in_new"))
                ),
                props.onClose.map(close =>
                  button(
                    className := "browser-action close-browser",
                    `type` := "button",
                    title := props.text.BrowserShell_close,
                    onClick := (_ => {
                      closeBrowserView()
                      close()
                    })
                  )(materialSymbol("close"))
                )
              )
            },
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

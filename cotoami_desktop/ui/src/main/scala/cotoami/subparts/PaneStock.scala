package cotoami.subparts

import scala.util.chaining._
import com.softwaremill.quicklens._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.{toolButton, ScrollArea, SplitPane}

import cotoami.browser.{
  BrowserDownloads,
  BrowserShell,
  BrowserTrail,
  WebClipMarkdown
}
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Geolocation, UiState}
import cotoami.subparts.EditorCoto.CotoForm
import cotoami.subparts.modeless.ModelessGeomap
import cotoami.updates

object PaneStock {

  final val PaneId = "stock-pane"
  final val PaneName = "PaneStock"
  final val DefaultWidth = 650
  final val BrowserContentLabel = "browser-content-main-embedded"

  final val PaneMapName = "PaneMap"
  final val PaneMapDefaultSize = 400

  case class BrowserModel(
      opened: Boolean = false,
      url: String = "",
      title: Option[String] = None,
      trail: BrowserTrail.Model = BrowserTrail.Model(),
      downloads: BrowserDownloads.Model = BrowserDownloads.Model(),
      downloadsOpenRequest: Int = 0,
      navigationRequest: Int = 0,
      nativeDetachRequest: Int = 0,
      nativeAttachRequest: Int = 0
  )

  def currentWidth: Double = dom.document.getElementById(PaneId) match {
    case element: HTMLElement => element.offsetWidth
    case _                    => 0
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.PaneStockMsg(this)
  }

  object Msg {
    case object OpenGeomap extends Msg
    case object OpenModelessGeomap extends Msg
    case object CloseMap extends Msg
    case class SetMapOrientation(vertical: Boolean) extends Msg
    case class FocusGeolocation(location: Geolocation) extends Msg
    case object DisplayGeolocationInFocus extends Msg
    case class OpenBrowser(url: String) extends Msg
    case object CloseBrowser extends Msg
    case object DetachNativeBrowser extends Msg
    case object AttachNativeBrowser extends Msg
    case object OpenBrowserAsWindow extends Msg
    case class BrowserStateChanged(url: String, title: Option[String])
        extends Msg
    case class BrowserScrollPositionChanged(url: String, x: Double, y: Double)
        extends Msg
    case class BrowserSelectionClipped(capture: BrowserShell.SelectionCapture)
        extends Msg
    case class BrowserSelectionPosted(capture: BrowserShell.SelectionCapture)
        extends Msg
    case class BrowserTrailMsg(msg: BrowserTrail.Msg) extends Msg
    case class BrowserDownloadsMsg(msg: BrowserDownloads.Msg) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.OpenGeomap =>
        model
          .closeModelessDialog(ModelessGeomap.DialogId)
          .modify(_.modeless.geomap).setTo(None)
          .pipe(updates.uiState(_.openGeomap))
          .pipe(
            updates.addCmd((_: Model) =>
              Browser.send(AppMain.Msg.SetPaneStockOpen(true).into)
            )
          )

      case Msg.OpenModelessGeomap =>
        model
          .pipe(updates.uiState(_.closeMap))
          .pipe(
            updates.addCmd((_: Model) => ModelessGeomap.open)
          )

      case Msg.CloseMap =>
        model
          .modify(_.geomap).using(_.unfocus)
          .pipe(updates.uiState(_.closeMap))

      case Msg.SetMapOrientation(vertical) =>
        model.pipe(
          updates.uiState(
            _.setMapOrientation(vertical)
              .resizePane(PaneMapName, PaneMapDefaultSize)
          )
        )

      case Msg.FocusGeolocation(location) =>
        update(Msg.OpenGeomap, model)
          .pipe { case (model, cmd) =>
            (model.modify(_.geomap).using(_.focus(location)), cmd)
          }

      case Msg.DisplayGeolocationInFocus =>
        model.repo.geolocationInFocus
          .map(location =>
            model
              .pipe(update(Msg.OpenGeomap, _))
              .pipe { case (model, cmd) =>
                (model.modify(_.geomap).using(_.moveTo(location)), cmd)
              }
          )
          .getOrElse((model, Cmd.none))

      case Msg.OpenBrowser(url) =>
        val browserOpened = model
          .modify(_.stockBrowser)
          .using(browser =>
            browser.copy(
              opened = true,
              url = url,
              title = None,
              navigationRequest =
                if (browser.opened) browser.navigationRequest + 1
                else browser.navigationRequest
            )
          )
        browserOpened.uiState
          .map(AppMain.update(AppMain.Msg.SetPaneStockOpen(true)))
          .map { case (uiState, cmd) =>
            (browserOpened.copy(uiState = Some(uiState)), cmd)
          }
          .getOrElse((browserOpened, Cmd.none))

      case Msg.CloseBrowser =>
        (
          model.modify(_.stockBrowser).setTo(BrowserModel()),
          Cmd.none
        )

      case Msg.DetachNativeBrowser =>
        (
          model.modify(_.stockBrowser).using(browser =>
            browser.copy(nativeDetachRequest = browser.nativeDetachRequest + 1)
          ),
          Cmd.none
        )

      case Msg.AttachNativeBrowser =>
        (
          model.modify(_.stockBrowser).using(browser =>
            browser.copy(nativeAttachRequest = browser.nativeAttachRequest + 1)
          ),
          Cmd.none
        )

      case Msg.OpenBrowserAsWindow =>
        model.stockBrowser.url.trim match {
          case url if url.nonEmpty =>
            cotoami.browser.openUrlInNewWindow(
              url,
              Some(model.i18n.locale.toLanguageTag()),
              model.databaseFolder,
              model.repo.nodes.focusedId.map(_.uuid),
              model.repo.cotonomas.focusedId.map(_.uuid),
              model.uiState.map(_.theme)
            )
            update(Msg.CloseBrowser, model)
          case _ =>
            (model, Cmd.none)
        }

      case Msg.BrowserStateChanged(url, title) =>
        (
          model.modify(_.stockBrowser).using(browser =>
            browser.copy(
              url = url,
              title = title,
              trail = browser.trail.remember(url, title)
            )
          ),
          Cmd.none
        )

      case Msg.BrowserScrollPositionChanged(url, x, y) =>
        (
          model.modify(_.stockBrowser.trail).using(
            _.saveScrollPosition(url, x, y)
          ),
          Cmd.none
        )

      case Msg.BrowserSelectionClipped(capture) =>
        clipSelectionToDraft(capture, model)

      case Msg.BrowserSelectionPosted(capture) =>
        postSelection(capture, model)

      case Msg.BrowserTrailMsg(submsg) =>
        val (trail, cmd) = BrowserTrail.update(submsg, model.stockBrowser.trail)
        (
          model.modify(_.stockBrowser.trail).setTo(trail),
          cmd.map(msg => Msg.BrowserTrailMsg(msg).into)
        )

      case Msg.BrowserDownloadsMsg(submsg) =>
        val (downloads, cmd) =
          BrowserDownloads.update(submsg, model.stockBrowser.downloads)
        val downloadsOpenRequest =
          submsg match {
            case _: BrowserDownloads.Msg.DownloadStarted =>
              model.stockBrowser.downloadsOpenRequest + 1
            case _ =>
              model.stockBrowser.downloadsOpenRequest
          }
        (
          model.modify(_.stockBrowser).using(
            _.copy(
              downloads = downloads,
              downloadsOpenRequest = downloadsOpenRequest
            )
          ),
          cmd.map(msg => Msg.BrowserDownloadsMsg(msg).into)
        )
    }

  private def clipSelectionToDraft(
      capture: BrowserShell.SelectionCapture,
      model: Model
  ): (Model, Cmd[AppMsg]) = {
    val content = WebClipMarkdown.fromSelection(
      capture.selectedHtml,
      capture.selectedText,
      WebClipMarkdown.Source(capture.title, capture.url)
    )
    given Context = model
    val (flowInput, geomap, waitingPosts, cmd) =
      SectionFlowInput.update(
        SectionFlowInput.Msg.ReplaceCotoDraft(content, preview = true),
        model.flowInput,
        model.timeline.waitingPosts
      )
    val timeline = model.timeline.copy(waitingPosts = waitingPosts)
    (
      model.copy(flowInput = flowInput, geomap = geomap, timeline = timeline),
      cmd
    )
  }

  private def postSelection(
      capture: BrowserShell.SelectionCapture,
      model: Model
  ): (Model, Cmd[AppMsg]) = {
    val content = WebClipMarkdown.fromSelection(
      capture.selectedHtml,
      capture.selectedText,
      WebClipMarkdown.Source(capture.title, capture.url)
    )
    val flowInput = model.flowInput.copy(
      form = CotoForm.Model(contentInput = content),
      folded = false,
      focused = false,
      interacting = false,
      posting = false
    )
    given Context = model.copy(flowInput = flowInput)
    val (updatedFlowInput, geomap, waitingPosts, cmd) =
      SectionFlowInput.update(
        SectionFlowInput.Msg.Post,
        flowInput,
        model.timeline.waitingPosts
      )
    val timeline = model.timeline.copy(waitingPosts = waitingPosts)
    (
      model.copy(
        flowInput = updatedFlowInput,
        geomap = geomap,
        timeline = timeline
      ),
      cmd
    )
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(id := PaneId, className := "stock fill")(
      if (uiState.mapOpened)
        SplitPane(
          vertical = uiState.mapVertical,
          initialPrimarySize = uiState.paneSizes.getOrElse(
            PaneMapName,
            PaneMapDefaultSize
          ),
          onPrimarySizeChanged = Some((newSize) =>
            dispatch(AppMsg.ResizePane(PaneMapName, newSize))
          ),
          primary = SplitPane.Primary.Props()(
            divMap(model, uiState)
          ),
          secondary = SplitPane.Secondary.Props()(
            stockMainContent(model, uiState)
          )
          // Re-create the component on orientation change
        ).withKey(uiState.mapVertical.toString())
      else
        stockMainContent(model, uiState)
    )

  private def stockMainContent(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    if (model.stockBrowser.opened)
      embeddedBrowser(model, uiState)
    else
      sectionCotoGraph(model, uiState)

  private def embeddedBrowser(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    BrowserShell.component(
      BrowserShell.Props(
        contentLabel = BrowserContentLabel,
        initialUrl = Option(model.stockBrowser.url).filter(_.nonEmpty),
        app = model,
        title = model.stockBrowser.title,
        mode = BrowserShell.Mode.Embedded,
        layoutKey =
          if (uiState.reverseMainPanes) "main-reversed" else "main-normal",
        text = context.i18n.text,
        timeline = None,
        cotonomaSelect = None,
        trail = (currentUrl, onClose, onNavigate) =>
          BrowserTrail.view(
            model = model.stockBrowser.trail,
            currentUrl = currentUrl,
            paneTitle = context.i18n.text.BrowserShell_trail,
            emptyText = context.i18n.text.BrowserShell_trailEmpty,
            onClose = onClose,
            onNavigate = onNavigate,
            dispatch = msg => dispatch(Msg.BrowserTrailMsg(msg))
          ),
        downloads = onClose =>
          BrowserDownloads.view(
            model = model.stockBrowser.downloads,
            paneTitle = context.i18n.text.BrowserShell_downloads,
            emptyText = context.i18n.text.BrowserShell_downloadsEmpty,
            deleteTitle = context.i18n.text.Delete,
            onClose = onClose,
            dispatch = msg => dispatch(Msg.BrowserDownloadsMsg(msg))
          ),
        downloadsVisible = model.stockBrowser.downloads.nonEmpty,
        downloadsBusy = model.stockBrowser.downloads.downloading,
        downloadsOpenRequest = model.stockBrowser.downloadsOpenRequest,
        navigationRequest = model.stockBrowser.navigationRequest,
        nativeDetachRequest = model.stockBrowser.nativeDetachRequest,
        nativeAttachRequest = model.stockBrowser.nativeAttachRequest,
        nativeSuppressed = model.modalStack.top.isDefined,
        initialScrollPosition =
          model.stockBrowser.trail.entryForUrl(model.stockBrowser.url)
            .map(entry =>
              BrowserShell.InitialScrollPosition(entry.scrollX, entry.scrollY)
            ),
        timelineOpened = false,
        timelineWidth = BrowserShell.DefaultTimelineWidth,
        onTimelineOpenChange = _ => (),
        onTimelineWidthChange = _ => (),
        onStateChange =
          (url, title) => dispatch(Msg.BrowserStateChanged(url, title)),
        onScrollPositionChange =
          (url, x, y) => dispatch(Msg.BrowserScrollPositionChanged(url, x, y)),
        canClipSelection =
          context.databaseFolder.isDefined && context.repo.canPostCoto,
        onClipSelection =
          capture => dispatch(Msg.BrowserSelectionClipped(capture)),
        onPostSelection =
          capture => dispatch(Msg.BrowserSelectionPosted(capture)),
        onOpenAsWindow = Some(() => dispatch(Msg.OpenBrowserAsWindow)),
        onClose = Some(() => dispatch(Msg.CloseBrowser))
      )
    )

  private def divMap(model: Model, uiState: UiState)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "map fill")(
      Option.when(uiState.geomapOpened) {
        SectionGeomap(model.geomap)
      },
      toolButton(
        classes = "default change-split-orientation overlay",
        symbol =
          if (uiState.mapVertical)
            "splitscreen_top"
          else
            "splitscreen_left",
        tip =
          if (uiState.mapVertical)
            Some(context.i18n.text.PaneStock_map_dockTop)
          else
            Some(context.i18n.text.PaneStock_map_dockLeft),
        tipPlacement = "right",
        onClick = _ => dispatch(Msg.SetMapOrientation(!uiState.mapVertical))
      ),
      toolButton(
        classes = "default open-modeless-geomap overlay",
        symbol = "open_in_new",
        tip = Some(context.i18n.text.PaneStock_map_openModeless),
        tipPlacement = "right",
        onClick = _ => dispatch(Msg.OpenModelessGeomap)
      )
    )

  final val CotoGraphScrollableElementId = "scrollable-coto-graph"

  private def sectionCotoGraph(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val sectionTraversals = SectionTraversals(model.traversals)
    val contents = Fragment(
      SectionPins(uiState),
      sectionTraversals
    )
    section(
      className := optionalClasses(
        Seq(
          ("coto-graph", true),
          ("fill", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      if (sectionTraversals.isDefined)
        ScrollArea(
          scrollableClassName = Some("scrollable-coto-graph"),
          scrollableElementId = Some(CotoGraphScrollableElementId),
          scrollToRightWhen = Some(
            model.traversals.traversals.map(_.id).mkString(",")
          )
        )(contents)
      else
        contents
    )
  }
}

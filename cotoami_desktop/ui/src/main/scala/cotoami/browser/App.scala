package cotoami.browser

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.util.chaining._

import cats.effect.IO
import cats.effect.std.Dispatcher

import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.ReactElement
import slinky.web.ReactDOMClient
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd, Program, Sub}
import marubinotto.libs.tauri

import cotoami.{Context, Into, Model => CotoamiModel, Msg => AppMsg}
import cotoami.backend.{
  ChangelogEntryJson,
  DatabaseInfo,
  ErrorJson,
  SystemInfoJson
}
import cotoami.models.{Cotonoma, I18n, Id, Node, UiState}
import cotoami.repository.Root
import cotoami.subparts.{
  SectionFlowInput,
  SectionGeomap,
  SectionTimeline
}
import cotoami.updates.{Changelog, DatabaseFocus}

object App {

  private val DatabaseFocusEvent = "browser-database-focus"
  private val ThemeEvent = "browser-theme"

  case class BrowserDatabaseFocus(
      databaseFolder: String,
      focusedNodeId: Option[Id[Node]],
      focusedCotonomaId: Option[Id[Cotonoma]]
  )

  @js.native
  trait BrowserDatabaseFocusJson extends js.Object {
    val databaseFolder: String = js.native
    val focusedNodeId: js.UndefOr[String] = js.native
    val focusedCotonomaId: js.UndefOr[String] = js.native
  }

  @js.native
  trait BrowserThemeJson extends js.Object {
    val theme: String = js.native
  }

  case class Model(
      url: String,
      title: Option[String],
      databaseFolder: Option[String],
      initialTheme: Option[String],
      app: CotoamiModel,
      cotonomaSelect: CotonomaSelect.Model,
      trail: BrowserTrail.Model,
      pendingFocus: Option[BrowserDatabaseFocus],
      currentFocus: Option[BrowserDatabaseFocus]
  )

  sealed trait Msg

  object Msg {
    case class BrowserStateChanged(url: String, title: Option[String])
        extends Msg
    case class BrowserTrailMsg(msg: BrowserTrail.Msg) extends Msg
    case class SystemInfoFetched(result: Either[Unit, SystemInfoJson])
        extends Msg
    case class UiStateRestored(uiState: Option[UiState]) extends Msg
    case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfo])
        extends Msg
    case class DatabaseFocusChanged(focus: BrowserDatabaseFocus) extends Msg
    case class ThemeChanged(theme: String) extends Msg
    case class BackendChange(log: ChangelogEntryJson) extends Msg
    case class AppMsg(msg: cotoami.Msg) extends Msg
    case class CotonomaSelectMsg(msg: CotonomaSelect.Msg) extends Msg
  }

  private case class Props(
      contentLabel: String,
      initialUrl: Option[String],
      locale: Option[String],
      databaseFolder: Option[String],
      focusedNodeId: Option[String],
      focusedCotonomaId: Option[String],
      theme: Option[String]
  ) {
    def initialFocus: Option[BrowserDatabaseFocus] =
      databaseFolder.map(folder =>
        BrowserDatabaseFocus(
          folder,
          focusedNodeId.map(Id[Node](_)),
          focusedCotonomaId.map(Id[Cotonoma](_))
        )
      )
  }

  def isCurrentWindow(url: URL): Boolean =
    propsFromUrl(url).nonEmpty

  def run(container: dom.Element, dispatcher: Dispatcher[IO]): IO[Unit] =
    propsFromUrl(new URL(dom.window.location.href))
      .map(props =>
        Browser.runProgram(
          container,
          Program(
            init(props),
            (model: Model, dispatch: Msg => Unit) =>
              view(props, model, dispatch),
            update,
            subscriptions
          ),
          dispatcher
        )
      )
      .getOrElse(IO(ReactDOMClient.createRoot(container).render(div())))

  def emitDatabaseFocus(model: CotoamiModel): Cmd.One[AppMsg] =
    Cmd(IO {
      model.databaseFolder.foreach(folder =>
        tauri.event.emit(
          DatabaseFocusEvent,
          js.Dynamic.literal(
            databaseFolder = folder,
            focusedNodeId = model.repo.nodes.focusedId.map(_.uuid).orUndefined,
            focusedCotonomaId =
              model.repo.cotonomas.focusedId.map(_.uuid).orUndefined
          )
        )
      )
      None
    })

  def emitTheme(theme: String): Cmd.One[AppMsg] =
    Cmd(IO {
      tauri.event.emit(
        ThemeEvent,
        js.Dynamic.literal(theme = theme)
      )
      None
    })

  private def init(props: Props)(url: URL): (Model, Cmd[Msg]) = {
    val i18n = props.locale.map(I18n.fromBcp47).getOrElse(I18n())
    val initialTheme = props.theme.getOrElse(UiState.DefaultTheme)
    val app = CotoamiModel(
      url = url,
      i18n = i18n,
      databaseFolder = props.databaseFolder,
      uiState = Some(UiState(theme = initialTheme)),
      flowInput = SectionFlowInput.Model(),
      geomap = SectionGeomap.Model(SectionGeomap.DefaultRemotePmtilesUrl)
    )
    (
      Model(
        url = props.initialUrl.getOrElse(""),
        title = None,
        databaseFolder = props.databaseFolder,
        initialTheme = props.theme,
        app = app,
        cotonomaSelect = CotonomaSelect.Model(),
        trail = BrowserTrail.Model(),
        pendingFocus = props.initialFocus,
        currentFocus = None
      ),
      Cmd.Batch(
        UiState.restore.map(Msg.UiStateRestored.apply),
        SystemInfoJson.fetch().map(Msg.SystemInfoFetched.apply),
        props.databaseFolder
          .map(DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened.apply))
          .getOrElse(Cmd.none)
      )
    )
  }

  private def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case Msg.BrowserStateChanged(url, title) =>
        (
          model.copy(
            url = url,
            title = title,
            trail = model.trail.remember(url, title)
          ),
          Cmd.none
        )

      case Msg.BrowserTrailMsg(msg) =>
        val (trail, cmd) = BrowserTrail.update(msg, model.trail)
        (model.copy(trail = trail), cmd.map(Msg.BrowserTrailMsg.apply))

      case Msg.SystemInfoFetched(Right(info)) =>
        (model.copy(app = model.app.setSystemInfo(info)), Cmd.none)

      case Msg.SystemInfoFetched(Left(_)) =>
        (model, Cmd.none)

      case Msg.UiStateRestored(uiState) => {
        val restored = model.initialTheme
          .map(theme => uiState.getOrElse(UiState()).copy(theme = theme))
          .getOrElse(uiState.getOrElse(UiState()))
        (
          model.copy(app = model.app.copy(uiState = Some(restored))),
          Browser.setHtmlTheme(restored.theme)
        )
      }

      case Msg.DatabaseOpened(Right(info)) => {
        val app = model.app.copy(
          databaseFolder = Some(info.folder),
          repo = Root(info.initialDataset, info.localNodeId)
        )
        applyPendingFocus(model.copy(app = app))
      }

      case Msg.DatabaseOpened(Left(_)) =>
        (model, Cmd.none)

      case Msg.DatabaseFocusChanged(focus)
          if model.databaseFolder.contains(focus.databaseFolder) =>
        applyFocus(model.copy(pendingFocus = Some(focus)), focus)

      case Msg.DatabaseFocusChanged(_) =>
        (model, Cmd.none)

      case Msg.ThemeChanged(theme) =>
        updateUiState(model, _.copy(theme = theme)).pipe { case (model, cmd) =>
          (model, Browser.setHtmlTheme[Msg](theme) ++ cmd)
        }

      case Msg.BackendChange(log) =>
        Changelog.apply(log, model.app).pipe { case (app, cmd) =>
          (model.copy(app = app), cmd.map(Msg.AppMsg.apply))
        }

      case Msg.AppMsg(appMsg) =>
        updateApp(appMsg, model)

      case Msg.CotonomaSelectMsg(submsg) =>
        updateCotonomaSelect(submsg, model)
    }

  private def applyPendingFocus(model: Model): (Model, Cmd[Msg]) =
    model.pendingFocus
      .map(applyFocus(model, _))
      .getOrElse((model, Cmd.none))

  private def applyFocus(
      model: Model,
      focus: BrowserDatabaseFocus
  ): (Model, Cmd[Msg]) = {
    val focusedRepo = focus.focusedCotonomaId match {
      case Some(cotonomaId) =>
        model.app.repo.focusCotonoma(focus.focusedNodeId, cotonomaId)
      case None =>
        model.app.repo.focusNode(focus.focusedNodeId)
    }
    val focusedApp = model.app.copy(repo = focusedRepo)
    given Context = focusedApp
    val (timeline, cmd) = focusedApp.timeline.onFocusChange
    val cotonomaSelect = focus.focusedCotonomaId
      .flatMap(focusedApp.repo.cotonomas.get)
      .map(model.cotonomaSelect.remember)
      .getOrElse(model.cotonomaSelect)
    (
      model.copy(
        app = focusedApp.copy(timeline = timeline),
        cotonomaSelect = cotonomaSelect,
        pendingFocus = None,
        currentFocus = Some(focus)
      ),
      cmd.map(Msg.AppMsg.apply)
    )
  }

  private def updateApp(appMsg: AppMsg, model: Model): (Model, Cmd[Msg]) =
    appMsg match {
      case AppMsg.SectionTimelineMsg(submsg) => {
        given Context = model.app
        val (timeline, repo, cmd) =
          SectionTimeline.update(submsg, model.app.timeline)
        (
          model.copy(app = model.app.copy(timeline = timeline, repo = repo)),
          cmd.map(Msg.AppMsg.apply)
        )
      }

      case AppMsg.FocusCotonoma(cotonoma) =>
        focusCotonoma(model, cotonoma)

      case AppMsg.SetPaneOpen(name, open) =>
        updateUiState(model, _.setPaneOpen(name, open))

      case AppMsg.ResizePane(name, newSize) =>
        updateUiState(model, _.resizePane(name, newSize))

      case AppMsg.AddMessage(_, _, _) =>
        (model, Cmd.none)

      case _ =>
        (model, Cmd.none)
    }

  private def updateCotonomaSelect(
      msg: CotonomaSelect.Msg,
      model: Model
  ): (Model, Cmd[Msg]) = {
    given Context = model.app
    val (select, cmd, effect) =
      CotonomaSelect.update(msg, model.cotonomaSelect)
    val nextModel = model.copy(cotonomaSelect = select)
    val nextCmd = cmd.map(Msg.CotonomaSelectMsg.apply)
    effect match {
      case Some(effect: CotonomaSelect.Effect.FocusCotonoma) =>
        val cotonoma = effect.cotonoma
        val select = effect.select
        focusCotonoma(nextModel, cotonoma, Some(select)).pipe {
          case (model, cmd) => (model, nextCmd ++ cmd)
        }
      case Some(CotonomaSelect.Effect.ClearCotonoma(select)) =>
        nextModel.app
          .pipe(DatabaseFocus.node(nextModel.app.repo.nodes.focusedId))
          .pipe { case (app, cmd) =>
            (
              nextModel.copy(app = app, cotonomaSelect = select),
              nextCmd ++ (cmd ++ emitDatabaseFocus(app)).map(Msg.AppMsg.apply)
            )
          }
      case None =>
        (nextModel, nextCmd)
    }
  }

  private def focusCotonoma(
      model: Model,
      cotonoma: Cotonoma,
      nextSelect: Option[CotonomaSelect.Model] = None
  ): (Model, Cmd[Msg]) = {
    val appWithCotonoma = model.app.copy(
      repo = model.app.repo.copy(
        cotonomas = model.app.repo.cotonomas.put(cotonoma)
      )
    )
    appWithCotonoma
      .pipe(DatabaseFocus.cotonoma(Some(cotonoma.nodeId), cotonoma.id))
      .pipe { case (app, cmd) =>
        val cotonomaSelect =
          nextSelect.getOrElse(model.cotonomaSelect).remember(cotonoma)
        (
          model.copy(
            app = app,
            cotonomaSelect = cotonomaSelect
          ),
          (cmd ++ emitDatabaseFocus(app)).map(Msg.AppMsg.apply)
        )
      }
  }

  private def updateUiState(
      model: Model,
      update: UiState => UiState
  ): (Model, Cmd[Msg]) = {
    val uiState = update(model.app.uiState.getOrElse(UiState()))
    (
      model.copy(app = model.app.copy(uiState = Some(uiState))),
      uiState.save.map(Msg.AppMsg.apply)
    )
  }

  private def view(
      props: Props,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement = {
    val uiState = model.app.uiState.getOrElse(UiState())
    val timelineOpened = uiState.paneOpened(BrowserShell.TimelinePaneName)
    val timelineWidth = uiState.paneSizes.getOrElse(
      BrowserShell.TimelinePaneName,
      BrowserShell.DefaultTimelineWidth
    )
    val appDispatch: Into[AppMsg] => Unit =
      msg => dispatch(Msg.AppMsg(msg.into))
    given Context = model.app
    given (Into[AppMsg] => Unit) = appDispatch

    BrowserShell.component(
      BrowserShell.Props(
        contentLabel = props.contentLabel,
        initialUrl = props.initialUrl,
        model = model,
        text = model.app.i18n.text,
        timeline = props.databaseFolder.map(_ =>
          SectionTimeline(
            model.app.timeline,
            SectionTimeline.Options(showItoTraversalParts = false)
          )
            .getOrElse(div(className := "browser-timeline-empty")())
        ),
        cotonomaSelect = props.databaseFolder.map(_ =>
          CotonomaSelect.view(
            model.cotonomaSelect,
            model.app,
            msg => dispatch(Msg.CotonomaSelectMsg(msg))
          )
        ),
        trail = (currentUrl, onClose, onNavigate) =>
          BrowserTrail.view(
            model = model.trail,
            currentUrl = currentUrl,
            paneTitle = model.app.i18n.text.BrowserShell_trail,
            emptyText = model.app.i18n.text.BrowserShell_trailEmpty,
            onClose = onClose,
            onNavigate = onNavigate,
            dispatch = msg => dispatch(Msg.BrowserTrailMsg(msg))
          ),
        timelineOpened = timelineOpened,
        timelineWidth = timelineWidth,
        onTimelineOpenChange = open =>
          dispatch(
            Msg.AppMsg(AppMsg.SetPaneOpen(BrowserShell.TimelinePaneName, open))
          ),
        onTimelineWidthChange = width =>
          dispatch(
            Msg.AppMsg(AppMsg.ResizePane(BrowserShell.TimelinePaneName, width))
          ),
        onStateChange =
          (url, title) => dispatch(Msg.BrowserStateChanged(url, title))
      )
    )
  }

  private def subscriptions(model: Model): Sub[Msg] =
    databaseFocusSubscription(model).combine(
      tauri.listen[ChangelogEntryJson]("backend-change")
        .map(Msg.BackendChange.apply)
    ).combine(
      tauri.listen[BrowserThemeJson](ThemeEvent)
        .map(payload => Msg.ThemeChanged(payload.theme))
    )

  private def databaseFocusSubscription(model: Model): Sub[Msg] =
    model.databaseFolder
      .map(_ =>
        tauri.listen[BrowserDatabaseFocusJson](DatabaseFocusEvent)
          .map(payload =>
            Msg.DatabaseFocusChanged(
              BrowserDatabaseFocus(
                payload.databaseFolder,
                payload.focusedNodeId.toOption.map(Id[Node](_)),
                payload.focusedCotonomaId.toOption.map(Id[Cotonoma](_))
              )
            )
          )
      )
      .getOrElse(Sub.Empty)

  private def propsFromUrl(url: URL): Option[Props] = {
    val params = queryParams(url)
    for {
      browserShell <- params.get("browserShell")
      if browserShell == "1"
      contentLabel <- params.get("contentLabel")
    } yield Props(
      contentLabel,
      params.get("initialUrl").filter(_.nonEmpty),
      params.get("locale"),
      params.get("databaseFolder"),
      params.get("focusedNodeId"),
      params.get("focusedCotonomaId"),
      params.get("theme")
    )
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

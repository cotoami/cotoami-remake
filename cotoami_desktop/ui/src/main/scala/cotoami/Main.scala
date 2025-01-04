package cotoami

import scala.util.chaining._
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot

import com.softwaremill.quicklens._

import cats.effect.IO
import cats.syntax.all._

import fui._
import cotoami.utils.facade.Nullable
import cotoami.libs.tauri
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.updates
import cotoami.updates._
import cotoami.subparts._

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(
        init,
        (model: Model, dispatch: Msg => Unit) =>
          view(model, msg => dispatch(msg.into)),
        update,
        subscriptions,
        Some(Msg.UrlChanged)
      )
    )
  }

  object DatabaseFolder {
    val SessionStorageKey = "DatabaseFolder"

    def save(folder: String): Cmd.One[Msg] = Cmd(IO {
      dom.window.sessionStorage.setItem(SessionStorageKey, folder)
      None
    })

    def restore: Cmd.One[Option[String]] = Cmd(IO {
      Some(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
    })
  }

  def init(url: URL): (Model, Cmd[Msg]) = {
    val (flowInput, flowInputCmd) = SectionFlowInput.init
    (
      Model(url = url, flowInput = flowInput),
      Cmd.Batch(
        UiState.restore(Msg.UiStateRestored),
        cotoami.backend.SystemInfoJson.fetch().map(Msg.SystemInfoFetched),
        DatabaseFolder.restore.flatMap(
          _.map(DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened))
            .getOrElse(Modal.open(Modal.Welcome()))
        ),
        flowInputCmd
      )
    )
  }

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) = {
    if (LinkingInfo.developmentMode) {
      println(s"Main.update: ${msg.getClass()}")
    }

    implicit val context: Context = model
    msg match {
      case Msg.UrlChanged(url) => applyUrlChange(url, model.changeUrl(url))

      case Msg.AddLogEntry(level, message, details) =>
        (
          model.modify(_.log).using(_.log(level, message, details)),
          Cmd.none
        )

      case Msg.LogEvent(event) =>
        (
          model.modify(_.log).using(
            _.addEntry(LogEventJson.toLogEntry(event))
          ),
          Cmd.none
        )

      case Msg.BackendChange(log) => updates.Changelog.apply(log, model)

      case Msg.BackendEvent(event) =>
        (updates.LocalNodeEvent.handle(event, model), Cmd.none)

      case Msg.ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Cmd.none)

      case Msg.SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.time).using(
              _.setZoneOffsetInSeconds(systemInfo.time_zone_offset_in_sec)
            )
            .modify(
              _.modalStack.modals.each.when[Modal.Welcome].model.baseFolder
            ).setTo(Nullable.toOption(systemInfo.app_data_dir).getOrElse(""))
            .info(
              "SystemInfo fetched.",
              Some(SystemInfoJson.debug(systemInfo))
            ),
          Cmd.none
        )

      case Msg.SystemInfoFetched(Left(_)) => (model, Cmd.none)

      case Msg.UiStateRestored(uiState) =>
        (
          model
            .modify(_.uiState).setTo(Some(uiState.getOrElse(UiState())))
            .info("UiState restored.", Some(uiState.toString())),
          Browser.setHtmlTheme(
            uiState.map(_.theme).getOrElse(UiState.DefaultTheme)
          )
        )

      case Msg.DatabaseOpened(Right(info)) =>
        update(Msg.SetDatabaseInfo(info), model)

      case Msg.DatabaseOpened(Left(e)) =>
        (model.error(e.default_message, Some(e)), Cmd.none)

      case Msg.SetDatabaseInfo(info) => {
        model
          .modify(_.databaseFolder).setTo(Some(info.folder))
          .modify(_.domain).setTo(Domain(info.initialDataset, info.localNodeId))
          .info("Database opened.", Some(info.folder))
          .pipe(model => applyUrlChange(model.url, model))
          .pipe { case (model, cmd) =>
            (
              model,
              Cmd.Batch(
                DatabaseFolder.save(info.folder),
                connectToServers()
              ) ++ cmd
            )
          }
      }

      case Msg.ServerConnectionsInitialized(result) =>
        (
          result match {
            case Right(_) => model
            case Left(e) =>
              model.error("Failed to initialize server connections.", Some(e))
          },
          Cmd.none
        )

      case Msg.SetRemoteInitialDataset(dataset) =>
        (
          model
            .modify(_.domain).setTo(
              Domain(dataset, model.domain.nodes.localId.get)
            ),
          Browser.pushUrl(Route.index.url(()))
        )

      case Msg.SetTheme(theme) =>
        updates.uiState(_.copy(theme = theme), model).pipe {
          case (model, cmd) =>
            (model, Cmd.Batch(cmd, Browser.setHtmlTheme(theme)))
        }

      case Msg.OpenOrClosePane(name, open) =>
        updates.uiState(_.openOrClosePane(name, open), model)

      case Msg.ResizePane(name, newSize) =>
        updates.uiState(_.resizePane(name, newSize), model)

      case Msg.FocusNode(id) =>
        (model, Browser.pushUrl(Route.node.url(id)))

      case Msg.UnfocusNode => {
        val newUrl = model.domain.cotonomas.focused match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusCotonoma(cotonoma) => {
        val newUrl = model.domain.nodes.focused match {
          // Maintain node unfocus
          case None => Route.cotonoma.url(cotonoma.id)
          // Maintain node focus
          case Some(_) =>
            if (model.domain.nodes.isNodeRoot(cotonoma))
              // Don't allow to focus a root cotonoma while maintaining node focus,
              // which should be converted into node focus.
              Route.node.url(cotonoma.nodeId)
            else
              Route.cotonomaInNode.url((cotonoma.nodeId, cotonoma.id))
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusedCotonomaDetailsFetched(Right(details)) =>
        model.modify(_.domain).using(_.setCotonomaDetails(details)).pipe {
          _ -> Browser.send(SectionGeomap.Msg.DatabaseFocusChanged.into)
        }

      case Msg.FocusedCotonomaDetailsFetched(Left(e)) =>
        (model.error("Couldn't fetch cotonoma details.", Some(e)), Cmd.none)

      case Msg.UnfocusCotonoma => {
        val newUrl = model.domain.nodes.focused match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusCoto(id, moveTo) => {
        val newUrl = new URL(model.url.toString())
        newUrl.hash = s"#${id.uuid}"
        model.copy(url = newUrl).pipe { model =>
          DatabaseFocus.coto(id, moveTo, model).pipe { case (model, focus) =>
            (
              model,
              Cmd.Batch(
                Browser.send(Msg.OpenOrClosePane(PaneFlow.PaneName, true)),
                Browser.pushUrl(newUrl.toString(), notify = false),
                focus
              )
            )
          }
        }
      }

      case Msg.UnfocusCoto => {
        val newUrl = new URL(model.url.toString())
        newUrl.hash = ""
        (
          model
            .modify(_.url).setTo(newUrl)
            .modify(_.domain.cotos).using(_.unfocus)
            .modify(_.geomap.focusedLocation).setTo(None)
            .pipe { model =>
              model.domain.geolocationInFocus match {
                case Some(location) =>
                  model.modify(_.geomap).using(_.moveTo(location))
                case None => model
              }
            },
          Browser.pushUrl(newUrl.toString(), notify = false)
        )
      }

      case Msg.ReloadDomain => {
        (
          model.copy(domain = Domain()),
          model.databaseFolder.map(
            DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened)
          ).getOrElse(Cmd.none)
        )
      }

      case Msg.DomainMsg(submsg) => {
        val (domain, cmds) = Domain.update(submsg, model.domain)
        (model.copy(domain = domain), cmds)
      }

      case Msg.OpenGeomap =>
        updates.uiState(_.openGeomap, model)

      case Msg.CloseMap =>
        updates.uiState(_.closeMap, model)

      case Msg.FocusGeolocation(location) =>
        updates.uiState(_.openGeomap, model).pipe { case (model, cmds) =>
          (model.modify(_.geomap).using(_.focus(location)), cmds)
        }

      case Msg.UnfocusGeolocation =>
        (model.modify(_.geomap).using(_.unfocus), Cmd.none)

      case Msg.DisplayGeolocationInFocus =>
        model.domain.geolocationInFocus match {
          case Some(location) =>
            updates.uiState(_.openGeomap, model).pipe { case (model, cmds) =>
              (
                model.modify(_.geomap).using(_.moveTo(location)),
                cmds
              )
            }
          case None => (model, Cmd.none)
        }

      case Msg.ModalMsg(submsg) => Modal.update(submsg, model)

      case Msg.NavCotonomasMsg(submsg) => {
        val (navCotonomas, cotonomas, cmds) =
          NavCotonomas.update(submsg, model.navCotonomas)
        (
          model.copy(navCotonomas = navCotonomas)
            .modify(_.domain.cotonomas).setTo(cotonomas),
          cmds
        )
      }

      case Msg.FlowInputMsg(submsg) => {
        val (flowInput, geomap, waitingPosts, cmds) =
          SectionFlowInput.update(
            submsg,
            model.flowInput,
            model.waitingPosts
          )(model)
        (
          model.copy(
            flowInput = flowInput,
            geomap = geomap,
            waitingPosts = waitingPosts
          ),
          cmds
        )
      }

      case Msg.SectionTimelineMsg(submsg) => {
        val (timeline, domain, cmds) =
          SectionTimeline.update(submsg, model.timeline)
        (model.copy(timeline = timeline, domain = domain), cmds)
      }

      case Msg.SectionPinnedCotosMsg(submsg) =>
        SectionPinnedCotos.update(submsg, model)

      case Msg.SectionTraversalsMsg(submsg) => {
        val (traversals, cmd) =
          SectionTraversals.update(submsg, model.traversals)
        (model.copy(traversals = traversals), cmd)
      }

      case Msg.SectionGeomapMsg(submsg) => {
        val (geomap, domain, cmd) = SectionGeomap.update(submsg, model.geomap)
        (model.copy(geomap = geomap, domain = domain), cmd)
      }
    }
  }

  def applyUrlChange(url: URL, model: Model): (Model, Cmd[Msg]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) => DatabaseFocus.node(None, model)

      case Route.node(id) =>
        if (model.domain.nodes.contains(id))
          DatabaseFocus.node(Some(id), model)
        else
          (model, Browser.pushUrl(Route.index.url(())))

      case Route.cotonoma(id) => DatabaseFocus.cotonoma(None, id, model)

      case Route.cotonomaInNode((nodeId, cotonomaId)) =>
        DatabaseFocus.cotonoma(Some(nodeId), cotonomaId, model)

      case _ =>
        (model, Browser.pushUrl(Route.index.url(())))
    }

  private def connectToServers(): Cmd.One[Msg] =
    tauri.invokeCommand("connect_to_servers").map(
      Msg.ServerConnectionsInitialized
    )

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    (tauri.listen[LogEventJson]("log", None).map(Msg.LogEvent): Sub[Msg]) <+>
      listenToBackendChanges(model) <+>
      (tauri.listen[LocalNodeEventJson]("backend-event", None)
        .map(Msg.BackendEvent))

  private def listenToBackendChanges(model: Model): Sub[Msg] =
    if (model.modalStack.opened[Modal.ParentSync])
      Sub.Empty
    else
      tauri.listen[ChangelogEntryJson]("backend-change", None)
        .map(Msg.BackendChange)

  def view(model: Model, dispatch: Into[Msg] => Unit): ReactElement = {
    implicit val _context: Context = model
    implicit val _dispatch = dispatch
    Fragment(
      AppHeader(model),
      AppBody(model),
      AppFooter(model),
      if (model.logViewToggle)
        Some(ViewLog(model.log))
      else
        None,
      Modal(model)
    )
  }
}

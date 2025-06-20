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

import marubinotto.fui._
import marubinotto.facade.Nullable
import marubinotto.libs.tauri

import cotoami.backend._
import cotoami.repository._
import cotoami.models._
import cotoami.updates
import cotoami.updates._
import cotoami.subparts._
import cotoami.subparts.modals.ModalAppUpdate

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
        showAppWindow,
        UiState.restore.map(Msg.UiStateRestored),
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
    implicit val context: Context = model
    msg match {
      case Msg.UrlChanged(url) => applyUrlChange(url, model.changeUrl(url))

      case Msg.AddMessage(category, message, details) =>
        (
          model.modify(_.viewMessages.messages).using(
            _.add(category, message, details)
          ),
          Cmd.none
        )

      case Msg.BackendMessage(message) =>
        (
          model.modify(_.viewMessages.messages).using(
            _.add(MessageJson.toMessage(message))
          ),
          Cmd.none
        )

      case Msg.BackendChange(log) => updates.Changelog.apply(log, model)

      case Msg.BackendEvent(event) =>
        updates.LocalNodeEvent.handle(event, model)

      case Msg.AppWindowShown(result) =>
        result match {
          case Right(_) => (model, Cmd.none)
          case Left(e) =>
            (model.error("Failed to display the app window.", e), Cmd.none)
        }

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
          model.modify(_.uiState).setTo(Some(uiState.getOrElse(UiState()))),
          Browser.setHtmlTheme(
            uiState.map(_.theme).getOrElse(UiState.DefaultTheme)
          )
        )

      case Msg.DatabaseOpened(Right(info)) =>
        update(Msg.SetDatabaseInfo(info), model)

      case Msg.DatabaseOpened(Left(e)) =>
        (model.error(e.default_message, e), Cmd.none)

      case Msg.SetDatabaseInfo(info) =>
        model
          .modify(_.databaseFolder).setTo(Some(info.folder))
          .modify(_.repo).setTo(Root(info.initialDataset, info.localNodeId))
          .info("Database opened.", Some(info.folder))
          .pipe(model => applyUrlChange(model.url, model))
          .pipe { case (model, cmd) =>
            (
              model,
              Cmd.Batch(
                DatabaseFolder.save(info.folder),
                connectToServers,
                Root.fetchOthersLastPostedAt,
                info.newOwnerPassword
                  .map(password =>
                    Modal.open(Modal.NewPassword.forOwner(password))
                  )
                  .getOrElse(Cmd.none)
              ) ++ cmd
            )
          }

      case Msg.ServerConnectionsInitialized(result) =>
        (
          result match {
            case Right(_) => model
            case Left(e) =>
              model.error("Failed to initialize server connections.", e)
          },
          Cmd.none
        )

      case Msg.SetInitialDataset(dataset) =>
        (
          model
            .modify(_.repo).setTo(
              Root(dataset, model.repo.nodes.localId.get)
            ),
          Cmd.Batch(
            Browser.pushUrl(Route.index.url(())),
            Root.fetchOthersLastPostedAt
          )
        )

      case Msg.SetTheme(theme) =>
        model
          .pipe(updates.uiState(_.copy(theme = theme)))
          .pipe { case (model, cmd) =>
            (model, Cmd.Batch(cmd, Browser.setHtmlTheme(theme)))
          }

      case Msg.SetPaneOpen(name, open) =>
        updates.uiState(_.setPaneOpen(name, open))(model)

      case Msg.ResizePane(name, newSize) =>
        updates.uiState(_.resizePane(name, newSize))(model)

      case Msg.SwapPane => updates.uiState(_.swapPane)(model)

      case Msg.FocusNode(id) =>
        (model, Browser.pushUrl(Route.node.url(id)))

      case Msg.UnfocusNode => {
        val newUrl = model.repo.cotonomas.focused match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusCotonoma(cotonoma) => {
        val newUrl = model.repo.nodes.focused match {
          // Maintain node unfocus
          case None => Route.cotonoma.url(cotonoma.id)
          // Maintain node focus
          case Some(_) =>
            if (model.repo.nodes.isNodeRoot(cotonoma))
              // Don't allow to focus a root cotonoma while maintaining node focus,
              // which should be converted into node focus.
              Route.node.url(cotonoma.nodeId)
            else
              Route.cotonomaInNode.url((cotonoma.nodeId, cotonoma.id))
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusedCotonomaDetailsFetched(Right(details)) =>
        model.modify(_.repo).using(_.setCotonomaDetails(details)).pipe {
          model =>
            {
              val (geomap, cmd) = model.geomap.onFocusChange(model.repo)
              (model.copy(geomap = geomap), cmd)
            }
        }

      case Msg.FocusedCotonomaDetailsFetched(Left(e)) =>
        (model.error("Couldn't fetch cotonoma details.", e), Cmd.none)

      case Msg.UnfocusCotonoma => {
        val newUrl = model.repo.nodes.focused match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Browser.pushUrl(newUrl))
      }

      case Msg.FocusCoto(id, moveTo) => {
        val newUrl = new URL(model.url.toString())
        newUrl.hash = s"#${id.uuid}"

        model.copy(url = newUrl)
          .pipe(DatabaseFocus.coto(id, moveTo))
          .pipe(
            // Change the URL, but prevent the current node/cotonoma from being reloaded.
            addCmd(_ => Browser.pushUrl(newUrl.toString(), notify = false))
          )
          .pipe(
            addCmd(_ => Browser.send(AppMain.Msg.SetPaneFlowOpen(true).into))
          )
          .pipe(
            // Reload the coto with its incoming neighbors.
            addCmd(_ => Root.fetchCotoDetails(id))
          )
      }

      case Msg.CotoFetchedOnDirectVisit(Right(details)) =>
        model.modify(_.repo).using(_.importFrom(details))
          .pipe(
            DatabaseFocus.coto(details.coto.id, moveTo = true)
          )
          .pipe(
            addCmd(_ => Browser.send(AppMain.Msg.SetPaneFlowOpen(true).into))
          )

      case Msg.CotoFetchedOnDirectVisit(Left(e)) =>
        update(Msg.UnfocusCoto, model)

      case Msg.UnfocusCoto => {
        val newUrl = new URL(model.url.toString())
        newUrl.hash = ""
        (
          model
            .modify(_.url).setTo(newUrl)
            .modify(_.repo.cotos).using(_.unfocus)
            .modify(_.geomap.focusedLocation).setTo(None)
            .pipe { model =>
              model.repo.geolocationInFocus match {
                case Some(location) =>
                  model.modify(_.geomap).using(_.moveTo(location))
                case None => model
              }
            },
          Browser.pushUrl(newUrl.toString(), notify = false)
        )
      }

      case Msg.Select(cotoId) =>
        (model.modify(_.repo.cotos).using(_.select(cotoId)), Cmd.none)

      case Msg.Deselect(cotoId) =>
        model.modify(_.repo.cotos).using(_.deselect(cotoId)).pipe { model =>
          (
            model,
            if (!model.repo.cotos.anySelected)
              Cmd.Batch(
                Modal.close(classOf[Modal.Selection]),
                Modal.close(classOf[Modal.NewIto])
              )
            else
              Cmd.none
          )
        }

      case Msg.Highlight(cotoId) =>
        (model.copy(highlight = Some(cotoId)), Cmd.none)

      case Msg.Unhighlight => (model.copy(highlight = None), Cmd.none)

      case Msg.ReloadRepository => {
        (
          model.copy(repo = Root()),
          model.databaseFolder.map(
            DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened)
          ).getOrElse(Cmd.none)
        )
      }

      case Msg.RepositoryMsg(submsg) => {
        val (repo, cmd) = Root.update(submsg, model.repo)
        (model.copy(repo = repo), cmd)
      }

      case Msg.Pin(cotoId) => {
        val (repo, cmd) = model.repo.pin(cotoId)
        (
          model.copy(repo = repo),
          cmd ++ Browser.send(AppMain.Msg.SetPaneStockOpen(true).into)
        )
      }

      case Msg.NodeUpdated(Right(details)) =>
        (
          model
            .modify(_.repo).using(_.importFrom(details))
            .modify(_.geomap).using { geomap =>
              details.root
                .map(_._2)
                .map(coto => geomap.updateMarker(coto.id.uuid))
                .getOrElse(geomap)
            },
          Cmd.none
        )

      case Msg.NodeUpdated(Left(e)) =>
        (model.error("Couldn't fetch node details.", e), Cmd.none)

      case Msg.CotoUpdated(Right(details)) =>
        (
          model
            .modify(_.repo).using(_.importFrom(details))
            .modify(_.geomap).using(_.updateMarker(details.coto.id.uuid)),
          Cmd.none
        )

      case Msg.CotoUpdated(Left(e)) =>
        (model.error("Couldn't fetch coto details.", e), Cmd.none)

      case Msg.Promoted(Right((cotonoma, coto))) =>
        (
          model
            .modify(_.repo.cotos).using(_.put(coto))
            .modify(_.repo.cotonomas).using(_.post(cotonoma, coto))
            .modify(_.geomap).using(_.updateMarker(coto.id.uuid)),
          Cmd.none
        )

      case Msg.Promoted(Left(e)) =>
        (model.error("Couldn't fetch a cotonoma pair.", e), Cmd.none)

      case Msg.ModalMsg(submsg) => Modal.update(submsg, model)

      case Msg.ViewMessagesMsg(submsg) => {
        val (viewMessages, cmd) =
          ViewMessages.update(submsg, model.viewMessages)
        (model.copy(viewMessages = viewMessages), cmd)
      }

      case Msg.NavCotonomasMsg(submsg) => {
        val (navCotonomas, cotonomas, cmds) =
          NavCotonomas.update(submsg, model.navCotonomas)
        (
          model.copy(navCotonomas = navCotonomas)
            .modify(_.repo.cotonomas).setTo(cotonomas),
          cmds
        )
      }

      case Msg.SectionNodeToolsMsg(submsg) => {
        val (nodeTools, cmd) =
          SectionNodeTools.update(submsg, model.nodeTools)
        (model.copy(nodeTools = nodeTools), cmd)
      }

      case Msg.AppMainMsg(submsg) =>
        model.uiState
          .map(AppMain.update(submsg))
          .map { case (uiState, cmd) =>
            (model.copy(uiState = Some(uiState)), cmd)
          }
          .getOrElse((model, Cmd.none))

      case Msg.PaneStockMsg(submsg) => PaneStock.update(submsg, model)

      case Msg.PaneSearchMsg(submsg) => {
        val (search, repo, cmd) =
          PaneSearch.update(submsg, model.search)
        (model.copy(search = search, repo = repo), cmd)
      }

      case Msg.FlowInputMsg(submsg) => {
        val (flowInput, geomap, waitingPosts, cmds) =
          SectionFlowInput.update(
            submsg,
            model.flowInput,
            model.timeline.waitingPosts
          )
        (
          model
            .modify(_.flowInput).setTo(flowInput)
            .modify(_.geomap).setTo(geomap)
            .modify(_.timeline.waitingPosts).setTo(waitingPosts),
          cmds
        )
      }

      case Msg.SectionTimelineMsg(submsg) => {
        val (timeline, repo, cmd) =
          SectionTimeline.update(submsg, model.timeline)
        (model.copy(timeline = timeline, repo = repo), cmd)
      }

      case Msg.SectionPinsMsg(submsg) => {
        val (uiState, cmd) = SectionPins.update(submsg)
        (model.copy(uiState = uiState), cmd)
      }

      case Msg.SectionTraversalsMsg(submsg) => {
        val (traversals, cmd) =
          SectionTraversals.update(submsg, model.traversals)
        (model.copy(traversals = traversals), cmd)
      }

      case Msg.SectionGeomapMsg(submsg) => {
        val (geomap, repo, cmd) = SectionGeomap.update(submsg, model.geomap)
        (
          model.copy(
            geomap = geomap,
            repo = repo,
            flowInput = submsg match {
              case SectionGeomap.Msg.FocusLocation(location) =>
                model.flowInput.setGeolocation(location)
              case _ => model.flowInput
            }
          ),
          cmd
        )
      }
    }
  }

  def applyUrlChange(url: URL, model: Model): (Model, Cmd[Msg]) = {
    // Detect the focused coto from the fragment identifier
    val focusCoto =
      if (url.hash.isBlank())
        Cmd.none
      else {
        CotoDetails.fetch(Id(url.hash.stripPrefix("#")))
          .map(Msg.CotoFetchedOnDirectVisit(_))
      }

    // Detect the focused node/cotonoma from the path
    url.pathname match {
      case Route.index(_) =>
        model
          .pipe(DatabaseFocus.node(None))
          .pipe(addCmd(_ => focusCoto))

      case Route.node(id) =>
        if (model.repo.nodes.contains(id))
          model
            .pipe(DatabaseFocus.node(Some(id)))
            .pipe(addCmd(_ => focusCoto))
        else
          (model, Browser.pushUrl(Route.index.url(())))

      case Route.cotonoma(id) =>
        model
          .pipe(DatabaseFocus.cotonoma(None, id))
          .pipe(addCmd(_ => focusCoto))

      case Route.cotonomaInNode((nodeId, cotonomaId)) =>
        model
          .pipe(DatabaseFocus.cotonoma(Some(nodeId), cotonomaId))
          .pipe(addCmd(_ => focusCoto))

      case _ =>
        (model, Browser.pushUrl(Route.index.url(())))
    }
  }

  // https://github.com/tauri-apps/tauri/issues/1564
  private def showAppWindow: Cmd.One[Msg] =
    tauri.invokeCommand("show_window")
      .map(Msg.AppWindowShown)

  private def connectToServers: Cmd.One[Msg] =
    tauri.invokeCommand("connect_to_servers")
      .map(Msg.ServerConnectionsInitialized)

  def subscriptions(model: Model): Sub[Msg] =
    listenToBackendMessages <+>
      listenToBackendChanges(model) <+>
      listenToBackendEvents <+>
      appUpdateProgress(model)

  private def listenToBackendMessages: Sub[Msg] =
    (tauri.listen[MessageJson]("message")
      .map(Msg.BackendMessage): Sub[Msg])

  private def listenToBackendChanges(model: Model): Sub[Msg] =
    if (model.modalStack.opened[Modal.ParentSync])
      Sub.Empty
    else
      tauri.listen[ChangelogEntryJson]("backend-change")
        .map(Msg.BackendChange)

  private def listenToBackendEvents: Sub[Msg] =
    tauri.listen[LocalNodeEventJson]("backend-event")
      .map(Msg.BackendEvent)

  private def appUpdateProgress(model: Model): Sub[Msg] =
    model.modalStack.get[Modal.AppUpdate]
      .map { case Modal.AppUpdate(modal) => ModalAppUpdate.progress(modal) }
      .getOrElse(Sub.Empty)

  def view(model: Model, dispatch: Into[Msg] => Unit): ReactElement = {
    implicit val _context: Context = model
    implicit val _dispatch = dispatch
    Fragment(
      AppHeader(model),
      AppBody(model),
      AppFooter(model),
      ViewMessages(model.viewMessages),
      Modal(model)
    )
  }
}

package cotoami

import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot
import slinky.web.html._

import com.softwaremill.quicklens._

import cats.effect.IO

import fui.FunctionalUI._
import cotoami.tauri
import cotoami.backend.{
  Commands,
  DatabaseInfo,
  LogEvent,
  Node,
  Nodes,
  SystemInfo
}

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions, Some(UrlChanged(_)))
    )
  }

  object DatabaseFolder {
    val SessionStorageKey = "databaseFolder"

    def save(folder: String): Cmd[Msg] =
      Cmd(IO {
        dom.window.sessionStorage.setItem(SessionStorageKey, folder)
        None
      })

    def restore(): Cmd[Option[String]] =
      Cmd(IO {
        Some(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
      })
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) =
    (
      Model(url = url),
      Seq(
        Model.UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfo.fetch().map(SystemInfoFetched(_)),
        DatabaseFolder.restore().flatMap(
          _.map(openDatabase(_)).getOrElse(Cmd.none)
        )
      )
    )

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case UrlChanged(url) => applyUrlChange(url, model.copy(url = url))

      case AddLogEntry(level, message, details) =>
        (
          model.copy(log = model.log.log(level, message, details)),
          Seq.empty
        )

      case BackendLogEvent(event) =>
        (
          model.copy(log = model.log.addEntry(LogEvent.toLogEntry(event))),
          Seq.empty
        )

      case ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.modalWelcome.baseFolder).setTo(systemInfo.app_data_dir)
            .modify(_.log).using(
              _.info("SystemInfo fetched.", Some(SystemInfo.debug(systemInfo)))
            ),
          Seq.empty
        )

      case SystemInfoFetched(Left(_)) => (model, Seq.empty)

      case UiStateRestored(uiState) =>
        (
          model.copy(uiState = Some(uiState.getOrElse(Model.UiState()))),
          Seq.empty
        )

      case DatabaseOpened(Right(json)) => {
        val info = DatabaseInfo(json)
        model
          .modify(_.databaseFolder).setTo(Some(info.folder))
          .modify(_.lastChangeNumber).setTo(info.lastChangeNumber)
          .modify(_.nodes).setTo(Nodes(info))
          .modify(_.modalWelcome.processing).setTo(false)
          .modify(_.log).using(
            _.info("Database opened.", Some(info.debug))
          ) match {
          case model =>
            applyUrlChange(model.url, model).modify(_._2).using(
              DatabaseFolder.save(info.folder) +: _
            )
        }
      }

      case DatabaseOpened(Left(e)) =>
        (
          model
            .modify(_.log).using(_.error(e.message, Option(e.details)))
            .modify(_.modalWelcome.processing).setTo(false)
            .modify(_.modalWelcome.systemError).setTo(Some(e.message)),
          Seq.empty
        )

      case TogglePane(name) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.togglePane(name)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save()))
          }
          case None => (model, Seq.empty)
        }
      }

      case ResizePane(name, newSize) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.resizePane(name, newSize)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save()))
          }
          case None => (model, Seq.empty)
        }
      }

      case SelectNode(id) =>
        (model, Seq(Browser.pushUrl(Route.node.url(id))))

      case DeselectNode => {
        val url = model.cotonomas.selected match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case SelectCotonoma(id) => {
        val url = model.nodes.selected match {
          case None       => Route.cotonoma.url(id)
          case Some(node) => Route.cotonomaInNode.url((node.id, id))
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case DeselectCotonoma => {
        val url = model.nodes.selected match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case ModalWelcomeMsg(subMsg) => {
        val (modalWelcome, cmds) =
          subparts.ModalWelcome.update(subMsg, model.modalWelcome);
        (model.copy(modalWelcome = modalWelcome), cmds)
      }

      case FetchMoreCotonomas =>
        if (model.cotonomasLoading) {
          (model, Seq.empty)
        } else {
          model.cotonomas.recentIds.nextPageIndex.map(i =>
            (
              model.copy(cotonomasLoading = true),
              Seq(fetchCotonomas(model.nodes.selectedId, i))
            )
          ).getOrElse((model, Seq.empty))
        }

      case CotonomasFetched(Right(page)) => {
        val cotonomas = model.cotonomas.addPageOfRecent(page)
        (
          model
            .modify(_.cotonomas).setTo(cotonomas)
            .modify(_.cotonomasLoading).setTo(false)
            .modify(_.log).using(
              _.info(
                "Recent cotonomas fetched.",
                Some(cotonomas.recentIds.debug)
              )
            ),
          Seq.empty
        )
      }

      case CotonomasFetched(Left(e)) =>
        (
          model
            .modify(_.cotonomasLoading).setTo(false)
            .error(e, "Couldn't fetch cotonomas."),
          Seq.empty
        )

      case CotonomaDetailsFetched(Right(details)) =>
        (
          model
            .modify(_.cotonomas).using(_.setCotonomaDetails(details))
            .modify(_.log).using(
              _.info(
                "Cotonoma details fetched.",
                Some(
                  s"name: ${details.cotonoma.name}" +
                    s", supers: ${details.supers.size}" +
                    s", subs: ${details.subs.rows.size}"
                )
              )
            ),
          Seq.empty
        )

      case CotonomaDetailsFetched(Left(e)) =>
        (
          model.error(e, "Couldn't fetch cotonoma details."),
          Seq.empty
        )
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) =>
        (
          model
            .clearSelection()
            .modify(_.cotonomasLoading).setTo(true),
          Seq(fetchCotonomas(None, 0))
        )

      case Route.node(id) =>
        if (model.nodes.contains(id)) {
          (
            model
              .clearSelection()
              .modify(_.nodes).using(_.select(id))
              .modify(_.cotonomasLoading).setTo(true),
            Seq(fetchCotonomas(Some(id), 0))
          )
        } else {
          (
            model.modify(_.log).using(_.warn(s"Node [${id}] not found.", None)),
            Seq(Browser.pushUrl(Route.index.url(())))
          )
        }

      case Route.cotonoma(id) =>
        if (model.cotonomas.contains(id)) {
          (
            model
              .modify(_.nodes).using(_.deselect())
              .modify(_.cotonomas).using(_.select(id)),
            Seq()
          )
        } else {
          (
            model.modify(_.log).using(
              _.warn(s"Cotonoma [${id}] not found.", None)
            ),
            Seq(Browser.pushUrl(Route.index.url(())))
          )
        }

      case Route.cotonomaInNode((nodeId, cotonomaId)) =>
        model.cotonomas.get(cotonomaId) match {
          case Some(cotonoma) =>
            if (cotonoma.nodeId == nodeId) {
              (
                model
                  .modify(_.nodes).using(_.select(nodeId))
                  .modify(_.cotonomas).using(_.select(cotonomaId)),
                Seq()
              )
            } else {
              (
                model,
                Seq(
                  Browser.pushUrl(
                    Route.cotonomaInNode.url((cotonoma.nodeId, cotonomaId))
                  )
                )
              )
            }

          case None =>
            (
              model.modify(_.log).using(
                _.warn(s"Cotonoma [${cotonomaId}] not found.", None)
              ),
              Seq(Browser.pushUrl(Route.index.url(())))
            )
        }

      case _ =>
        (model, Seq(Browser.pushUrl(Route.index.url(()))))
    }

  def fetchCotonomas(nodeId: Option[Id[Node]], pageIndex: Double): Cmd[Msg] =
    node_command(Commands.RecentCotonomas(nodeId, pageIndex)).map(
      CotonomasFetched(_)
    )

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    tauri.listen[LogEvent]("log", None).map(BackendLogEvent(_))

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    Fragment(
      subparts.appHeader(model, dispatch),
      div(id := "app-body", className := "body")(
        model.uiState
          .map(subparts.appBodyContent(model, _, dispatch))
          .getOrElse(Seq()): _*
      ),
      subparts.appFooter(model, dispatch),
      if (model.logViewToggle)
        Some(subparts.LogView.view(model.log, dispatch))
      else
        None,
      subparts.modal(model, dispatch)
    )
}

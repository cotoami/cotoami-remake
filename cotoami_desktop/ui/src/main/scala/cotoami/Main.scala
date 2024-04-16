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
import cotoami.backend._
import cotoami.repositories._

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions, Some(UrlChanged))
    )
  }

  object DatabaseFolder {
    val SessionStorageKey = "DatabaseFolder"

    def save(folder: String): Cmd[Msg] = Cmd(IO {
      dom.window.sessionStorage.setItem(SessionStorageKey, folder)
      None
    })

    def restore: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
    })
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) = {
    val (flowInput, flowInputCmd) = subparts.FormCoto.init("flowInput", true)
    (
      Model(url = url, flowInput = flowInput),
      Seq(
        Model.UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfoJson.fetch().map(SystemInfoFetched),
        DatabaseFolder.restore.flatMap(
          _.map(openDatabase).getOrElse(Cmd.none)
        ),
        flowInputCmd.map(FlowInputMsg)
      )
    )
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case UrlChanged(url) => applyUrlChange(url, model.copy(url = url))

      case AddLogEntry(level, message, details) =>
        (
          model.modify(_.log).using(_.log(level, message, details)),
          Seq.empty
        )

      case BackendLogEvent(event) =>
        (
          model.modify(_.log).using(
            _.addEntry(LogEventJson.toLogEntry(event))
          ),
          Seq.empty
        )

      case ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.modalWelcome.baseFolder).setTo(systemInfo.app_data_dir)
            .info(
              "SystemInfo fetched.",
              Some(SystemInfoJson.debug(systemInfo))
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
          .info("Database opened.", Some(info.debug)) match {
          case model =>
            applyUrlChange(model.url, model).modify(_._2).using(
              DatabaseFolder.save(info.folder) +: _
            )
        }
      }

      case DatabaseOpened(Left(e)) =>
        (
          model
            .error(e.message, Option(e))
            .modify(_.modalWelcome.processing).setTo(false)
            .modify(_.modalWelcome.systemError).setTo(Some(e.message)),
          Seq.empty
        )

      case TogglePane(name) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.togglePane(name)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save))
          }
          case None => (model, Seq.empty)
        }
      }

      case ResizePane(name, newSize) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.resizePane(name, newSize)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save))
          }
          case None => (model, Seq.empty)
        }
      }

      case ToggleContent(cotoViewId) =>
        (
          model.modify(_.contentTogglesOpened).using(ids =>
            if (ids.contains(cotoViewId))
              ids - cotoViewId
            else
              ids + cotoViewId
          ),
          Seq.empty
        )

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

      case CotonomaDetailsFetched(Right(details)) => {
        val cotonoma = Cotonoma(details.cotonoma)
        (
          model
            .modify(_.nodes).using(nodes =>
              if (!nodes.isSelecting(cotonoma.nodeId))
                nodes.deselect()
              else
                nodes
            )
            .modify(_.cotonomas).using(_.setCotonomaDetails(details)),
          Seq.empty
        )
      }

      case CotonomaDetailsFetched(Left(e)) =>
        (
          model.error("Couldn't fetch cotonoma details.", Some(e)),
          Seq.empty
        )

      case TimelineFetched(Right(cotos)) =>
        (
          model
            .modify(_.cotos).using(_.appendTimeline(cotos))
            .modify(_.cotonomas).using(_.importFrom(cotos.related_data))
            .info("Timeline fetched.", Some(PaginatedCotosJson.debug(cotos))),
          Seq.empty
        )

      case TimelineFetched(Left(e)) =>
        (
          model
            .modify(_.cotos.timelineLoading).setTo(false)
            .error("Couldn't fetch timeline cotos.", Some(e)),
          Seq.empty
        )

      case CotoGraphFetched(Right(graph)) =>
        (
          model
            .modify(_.cotos).using(_.importFrom(graph))
            .modify(_.cotonomas).using(_.importFrom(graph.cotos_related_data))
            .modify(_.links).using(_.addAll(graph.links))
            .info("Coto graph fetched.", Some(CotoGraphJson.debug(graph))),
          Seq.empty
        )

      case CotoGraphFetched(Left(e)) =>
        (
          model
            .error("Couldn't fetch a coto graph.", Some(e)),
          Seq.empty
        )

      case CotonomasMsg(subMsg) => {
        val (cotonomas, cmds) =
          Cotonomas.update(
            subMsg,
            model.cotonomas,
            model.nodes.selectedId
          )
        (model.copy(cotonomas = cotonomas), cmds)
      }

      case CotosMsg(subMsg) => {
        val (cotos, cmds) =
          Cotos.update(
            subMsg,
            model.cotos,
            model.nodes.selectedId,
            model.cotonomas.selectedId
          )
        (model.copy(cotos = cotos), cmds)
      }

      case FlowInputMsg(subMsg) => {
        val (flowInput, cmds) =
          subparts.FormCoto.update(subMsg, model.flowInput)
        (model.copy(flowInput = flowInput), cmds.map(_.map(FlowInputMsg)))
      }

      case ModalWelcomeMsg(subMsg) => {
        val (modalWelcome, cmds) =
          subparts.ModalWelcome.update(subMsg, model.modalWelcome)
        (model.copy(modalWelcome = modalWelcome), cmds)
      }
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) =>
        (
          model
            .clearSelection()
            .modify(_.cotonomas.recentLoading).setTo(true)
            .modify(_.cotos.timelineLoading).setTo(true),
          Seq(
            Cotonomas.fetchRecent(None, 0),
            Cotos.fetchTimeline(None, None, 0)
          )
        )

      case Route.node(id) =>
        if (model.nodes.contains(id)) {
          (
            model
              .clearSelection()
              .modify(_.nodes).using(_.select(id))
              .modify(_.cotonomas.recentLoading).setTo(true)
              .modify(_.cotos.timelineLoading).setTo(true),
            Seq(
              Cotonomas.fetchRecent(Some(id), 0),
              Cotos.fetchTimeline(Some(id), None, 0)
            )
          )
        } else {
          (
            model.warn(s"Node [${id}] not found.", None),
            Seq(Browser.pushUrl(Route.index.url(())))
          )
        }

      case Route.cotonoma(id) =>
        model
          .modify(_.nodes).using(_.deselect())
          .modify(_.cotonomas).using(_.select(id))
          .modify(_.cotos).setTo(Cotos())
          .modify(_.cotos.timelineLoading).setTo(true) match {
          case model =>
            (
              model,
              Seq(
                Cotonomas.fetchDetails(id),
                Cotos.fetchTimeline(None, Some(id), 0),
                if (model.cotonomas.isEmpty)
                  Cotonomas.fetchRecent(None, 0)
                else
                  Cmd.none
              )
            )
        }

      case Route.cotonomaInNode((nodeId, cotonomaId)) =>
        model
          .modify(_.nodes).using(_.select(nodeId))
          .modify(_.cotonomas).using(_.select(cotonomaId))
          .modify(_.cotos).setTo(Cotos())
          .modify(_.cotos.timelineLoading).setTo(true) match {
          case model =>
            (
              model,
              Seq(
                Cotonomas.fetchDetails(cotonomaId),
                Cotos.fetchTimeline(None, Some(cotonomaId), 0),
                if (model.cotonomas.isEmpty)
                  Cotonomas.fetchRecent(Some(nodeId), 0)
                else
                  Cmd.none
              )
            )
        }

      case _ =>
        (model, Seq(Browser.pushUrl(Route.index.url(()))))
    }

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    tauri.listen[LogEventJson]("log", None).map(BackendLogEvent)

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
        Some(subparts.ViewLog.view(model.log, dispatch))
      else
        None,
      subparts.modal(model, dispatch)
    )
}

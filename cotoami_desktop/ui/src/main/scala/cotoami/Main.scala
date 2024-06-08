package cotoami

import scala.util.chaining._
import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL
import org.scalajs.dom.HTMLElement

import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot
import slinky.web.html._

import com.softwaremill.quicklens._

import cats.effect.IO
import cats.syntax.all._

import fui._
import cotoami.tauri
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._

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
    val (flowInput, flowInputCmd) = FormCoto.init("flowInput", true)
    (
      Model(url = url, flowInput = flowInput),
      Seq(
        UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfoJson.fetch().map(SystemInfoFetched),
        DatabaseFolder.restore.flatMap(
          _.map(openDatabase(_).map(DatabaseOpened))
            .getOrElse(Modal.open(Modal.Model.welcome))
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

      case LogEvent(event) =>
        (
          model.modify(_.log).using(
            _.addEntry(LogEventJson.toLogEntry(event))
          ),
          Seq.empty
        )

      case BackendChange(log) => {
        val (domain, cmds) = model.domain.importChangelog(log)
        return (model.copy(domain = domain), cmds)
      }

      case BackendEvent(event) =>
        (
          model.handleLocalNodeEvent(event)
            .debug(
              "BackendEvent received.",
              Some(js.JSON.stringify(event))
            ),
          Seq.empty
        )

      case ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.context).using(
              _.setZoneOffsetInSeconds(systemInfo.time_zone_offset_in_sec)
            )
            .modify(
              _.modalStack.modals.each.when[Modal.WelcomeModel].model.baseFolder
            ).setTo(Nullable.toOption(systemInfo.app_data_dir).getOrElse(""))
            .info(
              "SystemInfo fetched.",
              Some(SystemInfoJson.debug(systemInfo))
            ),
          Seq.empty
        )

      case SystemInfoFetched(Left(_)) => (model, Seq.empty)

      case UiStateRestored(uiState) =>
        (
          model
            .modify(_.uiState).setTo(Some(uiState.getOrElse(UiState())))
            .info("UiState restored.", Some(uiState.toString())),
          Seq.empty
        )

      case DatabaseOpened(Right(json)) =>
        (model, Seq(Browser.send(SetDatabaseInfo(DatabaseInfo(json)))))

      case DatabaseOpened(Left(e)) =>
        (model.error(e.default_message, Some(e)), Seq())

      case SetDatabaseInfo(info) => {
        model
          .modify(_.databaseFolder).setTo(Some(info.folder))
          .modify(_.domain).using(_.init(info))
          .info("Database opened.", Some(info.debug)) match {
          case model =>
            applyUrlChange(model.url, model).modify(_._2).using(
              DatabaseFolder.save(info.folder) +: _
            )
        }
      }

      case OpenOrClosePane(name, open) =>
        model.uiState
          .map(_.openOrClosePane(name, open) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case ResizePane(name, newSize) =>
        model.uiState
          .map(_.resizePane(name, newSize) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case SwitchPinnedView(cotonoma, inColumns) =>
        model.uiState
          .map(_.setPinnedInColumns(cotonoma, inColumns) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case ScrollToPinnedCoto(pin) =>
        (
          model,
          Seq(
            Cmd(
              IO {
                dom.document.getElementById(
                  PaneStock.elementIdOfPinnedCoto(pin)
                ) match {
                  case element: HTMLElement =>
                    element.scrollIntoView(true)
                  case _ => ()
                }
                None
              }
            )
          )
        )

      case SelectNode(id) =>
        (model, Seq(Browser.pushUrl(Route.node.url(id))))

      case DeselectNode => {
        val url = model.domain.cotonomas.selected match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case SelectCotonoma(id) => {
        val url = model.domain.nodes.selected match {
          case None       => Route.cotonoma.url(id)
          case Some(node) => Route.cotonomaInNode.url((node.id, id))
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case DeselectCotonoma => {
        val url = model.domain.nodes.selected match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case ReloadDomain => {
        (
          model.copy(domain = Domain()),
          Seq(
            model.databaseFolder.map(
              openDatabase(_).map(DatabaseOpened)
            ).getOrElse(Cmd.none)
          )
        )
      }

      case DomainMsg(subMsg) => {
        val (domain, cmds) = Domain.update(subMsg, model.domain)
        (model.copy(domain = domain), cmds)
      }

      case ModalMsg(subMsg) =>
        Modal.update(subMsg, model.modalStack)
          .pipe { case (stack, cmds) => (model.copy(modalStack = stack), cmds) }

      case FlowInputMsg(subMsg) => {
        val (flowInput, waitingPosts, log, cmds) = FormCoto.update(
          subMsg,
          model.domain.currentCotonoma,
          model.flowInput,
          model.waitingPosts,
          model.log
        )
        (
          model.copy(
            flowInput = flowInput,
            waitingPosts = waitingPosts,
            log = log
          ),
          cmds.map(_.map(FlowInputMsg))
        )
      }

      case SectionTimelineMsg(subMsg) =>
        SectionTimeline.update(subMsg, model)

      case SectionTraversalsMsg(subMsg) => {
        val (traversals, cmds) =
          SectionTraversals.update(
            subMsg,
            model.traversals,
            model.domain
          )
        (
          model.copy(traversals = traversals),
          (subMsg, model.uiState) match {
            // Open the stock pane on OpenTraversal if it's closed.
            case (SectionTraversals.OpenTraversal(_), Some(uiState))
                if !uiState.paneOpened(PaneStock.PaneName) =>
              Browser.send(OpenOrClosePane(PaneStock.PaneName, true)) +: cmds
            case _ => cmds
          }
        )
      }
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) => {
        val (domain, cmds) = model.domain.selectNode(None)
        (model.copy(domain = domain).resetSubparts, cmds)
      }

      case Route.node(id) =>
        if (model.domain.nodes.contains(id)) {
          val (domain, cmds) = model.domain.selectNode(Some(id))
          (model.copy(domain = domain).resetSubparts, cmds)
        } else {
          (
            model.warn(s"Node [${id}] not found.", None),
            Seq(Browser.pushUrl(Route.index.url(())))
          )
        }

      case Route.cotonoma(id) => {
        val (domain, cmds) = model.domain.selectCotonoma(None, id)
        (model.copy(domain = domain).resetSubparts, cmds)
      }

      case Route.cotonomaInNode((nodeId, cotonomaId)) => {
        val (domain, cmds) =
          model.domain.selectCotonoma(Some(nodeId), cotonomaId)
        (model.copy(domain = domain).resetSubparts, cmds)
      }

      case _ =>
        (model, Seq(Browser.pushUrl(Route.index.url(()))))
    }

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    (tauri.listen[LogEventJson]("log", None).map(LogEvent): Sub[Msg]) <+>
      (tauri.listen[ChangelogEntryJson]("backend-change", None)
        .map(BackendChange)) <+>
      (tauri.listen[LocalNodeEventJson]("backend-event", None)
        .map(BackendEvent))

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    Fragment(
      AppHeader(model, dispatch),
      div(id := "app-body", className := "body")(
        model.uiState
          .map(AppBody.contents(model, _, dispatch))
          .getOrElse(Seq()): _*
      ),
      AppFooter(model, dispatch),
      if (model.logViewToggle)
        Some(ViewLog(model.log, dispatch))
      else
        None,
      Modal(model, dispatch)
    )
}

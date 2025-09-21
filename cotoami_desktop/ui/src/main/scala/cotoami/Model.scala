package cotoami

import scala.scalajs.js
import org.scalajs.dom.URL
import com.softwaremill.quicklens._

import marubinotto.facade.Nullable
import marubinotto.libs.tauri

import cotoami.backend._
import cotoami.repository._
import cotoami.models._
import cotoami.subparts._
import cotoami.subparts.Modal

trait Context {
  def time: Time
  def i18n: I18n
  def resourceDir: Option[String]
  def uiState: Option[UiState]
  def repo: Root
  def geomap: SectionGeomap.Model
  def isHighlighting(cotoId: Id[Coto]): Boolean

  // Synchronously convert a device file path to an URL that can be loaded by the webview.
  def resolveResource(path: String): Option[String] = {
    val platformPath =
      path
        .replace("/", tauri.path.sep())
        .stripPrefix(tauri.path.sep())
    resourceDir match {
      case Some(dir) => {
        val absPath = dir + tauri.path.sep() + platformPath
        Some(tauri.core.convertFileSrc(absPath))
      }
      case None => None
    }
  }
}

case class Model(
    url: URL,
    time: Time = Time(),
    i18n: I18n = I18n(),

    // Initial data to be loaded in Main.init()
    systemInfo: Option[SystemInfoJson] = None,
    resourceDir: Option[String] = None,
    databaseFolder: Option[String] = None, // saved in sessionStorage
    uiState: Option[UiState] = None, // saved in localStorage

    // Repository root
    repo: Root = Root(),

    // Highlighted coto by hover
    highlight: Option[Id[Coto]] = None,

    // Status of syncing with parent nodes
    parentSync: ParentSync = ParentSync(),

    // subparts
    modalStack: Modal.Stack = Modal.Stack(),
    viewMessages: ViewMessages.Model = ViewMessages.Model(),
    navCotonomas: NavCotonomas.Model = NavCotonomas.Model(),
    nodeTools: SectionNodeTools.Model = SectionNodeTools.Model(),
    search: PaneSearch.Model = PaneSearch.Model(),
    flowInput: SectionFlowInput.Model,
    timeline: SectionTimeline.Model = SectionTimeline.Model(),
    traversals: SectionTraversals.Model = SectionTraversals.Model(),
    geomap: SectionGeomap.Model = SectionGeomap.Model()
) extends Context {
  def path: String = url.pathname + url.search + url.hash

  def setSystemInfo(info: SystemInfoJson): Model =
    this
      .modify(_.systemInfo).setTo(Some(info))
      .modify(_.time).using(
        _.setZoneOffsetInSeconds(info.time_zone_offset_in_sec)
      )
      .modify(_.i18n).using(i18n =>
        Nullable.toOption(info.locale)
          .map(I18n.fromBcp47)
          .getOrElse(i18n)
      )
      .modify(_.resourceDir).setTo(Nullable.toOption(info.resource_dir))
      .modify(
        _.modalStack.modals.each.when[Modal.Welcome].model.baseFolder
      ).setTo(Nullable.toOption(info.app_data_dir).getOrElse(""))

  def isHighlighting(cotoId: Id[Coto]): Boolean = highlight == Some(cotoId)
  def clearHighlight: Model = copy(highlight = None)

  def messages: SystemMessages = viewMessages.messages

  def info(message: String, details: Option[String] = None): Model =
    this.modify(_.viewMessages.messages).using(_.info(message, details))
  def error(message: String, details: Option[String] = None): Model =
    this.modify(_.viewMessages.messages).using(_.error(message, details))
  def error(message: String, error: ErrorJson): Model =
    this.modify(_.viewMessages.messages).using(
      _.error(message, Some(js.JSON.stringify(error)))
    )

  def changeUrl(url: URL): Model =
    copy(
      url = url,
      traversals = SectionTraversals.Model()
    )
}

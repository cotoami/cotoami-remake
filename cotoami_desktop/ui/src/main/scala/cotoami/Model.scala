package cotoami

import scala.scalajs.js
import org.scalajs.dom.URL

import cotoami.utils.Log
import cotoami.backend._
import cotoami.repository._
import cotoami.models._
import cotoami.subparts._
import cotoami.subparts.Modal

trait Context {
  def time: Time
  def i18n: I18n
  def log: Log
  def uiState: Option[UiState]
  def repo: Root
  def geomap: SectionGeomap.Model
  def isHighlighting(cotoId: Id[Coto]): Boolean
}

case class Model(
    url: URL,
    time: Time = Time(),
    i18n: I18n = I18n(),

    // Log
    log: Log = Log(),
    logViewToggle: Boolean = false,

    // Initial data to be loaded in Main.init()
    systemInfo: Option[SystemInfoJson] = None,
    databaseFolder: Option[String] = None, // saved in sessionStorage
    uiState: Option[UiState] = None, // saved in localStorage

    // Repository root
    repo: Root = Root(),

    // Highlighted coto by hover
    highlight: Option[Id[Coto]] = None,

    // Coto/Cotonoma inputs waiting to be posted
    waitingPosts: WaitingPosts = WaitingPosts(),

    // Status of syncing with parent nodes
    parentSync: ParentSync = ParentSync(),

    // subparts
    modalStack: Modal.Stack = Modal.Stack(),
    navCotonomas: NavCotonomas.Model = NavCotonomas.Model(),
    search: PaneSearch.Model = PaneSearch.Model(),
    flowInput: SectionFlowInput.Model,
    timeline: SectionTimeline.Model = SectionTimeline.Model(),
    traversals: SectionTraversals.Model = SectionTraversals.Model(),
    geomap: SectionGeomap.Model = SectionGeomap.Model()
) extends Context {
  def path: String = url.pathname + url.search + url.hash

  def isHighlighting(cotoId: Id[Coto]): Boolean = highlight == Some(cotoId)

  def info(message: String, details: Option[String] = None): Model =
    copy(log = log.info(message, details))
  def error(message: String, error: Option[ErrorJson]): Model =
    copy(log = log.error(message, error.map(js.JSON.stringify(_))))

  def changeUrl(url: URL): Model =
    copy(
      url = url,
      waitingPosts = WaitingPosts(),
      traversals = SectionTraversals.Model()
    )
}

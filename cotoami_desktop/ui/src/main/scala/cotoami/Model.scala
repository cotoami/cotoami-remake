package cotoami

import scala.scalajs.js
import org.scalajs.dom.URL
import com.softwaremill.quicklens._

import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._
import cotoami.subparts.Modal

case class Model(
    url: URL,
    log: Log = Log(),
    context: Context = Context(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfoJson] = None,
    databaseFolder: Option[String] = None,

    // uiState that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[UiState] = None,

    // This value will be updated by and referred to from subparts that need to
    // control text input according to IME state.
    imeActive: Boolean = false,

    // Domain aggregate root
    domain: Domain = Domain(),

    // Coto/Cotonoma inputs waiting to be posted
    waitingPosts: WaitingPosts = WaitingPosts(),

    // Status of syncing with parent nodes
    parentSync: ParentSync = ParentSync(),

    // subparts
    modalStack: Modal.Stack = Modal.Stack(),
    flowInput: FormCoto.Model,
    traversals: SectionTraversals.Model = SectionTraversals.Model()
) {
  def path: String = this.url.pathname + this.url.search + this.url.hash

  def debug(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.debug(message, details))
  def info(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.info(message, details))
  def warn(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.warn(message, details))
  def error(message: String, error: Option[ErrorJson]): Model =
    this.copy(log = this.log.error(message, error.map(js.JSON.stringify(_))))

  def resetSubparts: Model =
    this.copy(
      waitingPosts = WaitingPosts(),
      traversals = SectionTraversals.Model()
    )

  def handleLocalNodeEvent(event: LocalNodeEventJson): Model = {
    // ParentSyncProgress
    for (progress <- event.ParentSyncProgress.toOption) {
      val parentSync = this.parentSync.progress(progress)
      val modalStack =
        if (parentSync.comingManyChanges)
          this.modalStack.openIfNot(Modal.ParentSync())
        else
          this.modalStack
      return this.copy(parentSync = parentSync, modalStack = modalStack)
    }

    // ParentSyncEnd
    for (end <- event.ParentSyncEnd.toOption) {
      return this.modify(_.parentSync).using(_.end(end))
    }

    this
  }
}

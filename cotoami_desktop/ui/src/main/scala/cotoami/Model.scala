package cotoami

import scala.reflect.ClassTag
import scala.scalajs.js
import org.scalajs.dom.URL
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._
import cotoami.subparts.Modal

trait Context {
  def time: Time
  def i18n: I18n
  def log: Log
  def domain: Domain
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

    // Domain aggregate root
    domain: Domain = Domain(),

    // Coto/Cotonoma inputs waiting to be posted
    waitingPosts: WaitingPosts = WaitingPosts(),

    // Status of syncing with parent nodes
    parentSync: ParentSync = ParentSync(),

    // subparts
    modalStack: Modal.Stack = Modal.Stack(),
    navCotonomas: NavCotonomas.Model = NavCotonomas.Model(),
    flowInput: FormCoto.Model,
    timeline: SectionTimeline.Model = SectionTimeline.Model(),
    geomap: SectionGeomap.Model = SectionGeomap.Model(),
    traversals: SectionTraversals.Model = SectionTraversals.Model()
) extends Context {
  def path: String = this.url.pathname + this.url.search + this.url.hash

  def debug(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.debug(message, details))
  def info(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.info(message, details))
  def warn(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.warn(message, details))
  def error(message: String, error: Option[ErrorJson]): Model =
    this.copy(log = this.log.error(message, error.map(js.JSON.stringify(_))))

  def changeUrl(url: URL): Model =
    this.copy(
      url = url,
      waitingPosts = WaitingPosts(),
      traversals = SectionTraversals.Model()
    )

  def updateUiState(update: UiState => UiState): (Model, Seq[Cmd[Msg]]) =
    this.uiState
      .map(update(_) match {
        case state => (this.copy(uiState = Some(state)), Seq(state.save))
      })
      .getOrElse((this, Seq.empty))

  def updateModal[M <: Modal.Model: ClassTag](newState: M): Model =
    this.copy(modalStack = this.modalStack.update(newState))

  def focusNode(nodeId: Option[Id[Node]]): (Model, Seq[Cmd[Msg]]) =
    this
      .modify(_.domain).using(_.unfocus())
      .modify(_.domain.nodes).using(_.focus(nodeId))
      .modify(_.domain.cotonomas.recentLoading).setTo(true)
      .modify(_.timeline).using(_.init) match {
      case model =>
        (
          model,
          Seq(
            Cotonomas.fetchRecent(nodeId, 0),
            SectionTimeline.fetch(nodeId, None, None, 0),
            model.domain.currentCotonomaId
              .map(Domain.fetchGraphFromCotonoma)
              .getOrElse(Cmd.none)
          )
        )
    }

  def focusCotonoma(
      nodeId: Option[Id[Node]],
      cotonomaId: Id[Cotonoma]
  ): (Model, Seq[Cmd[Msg]]) = {
    val shouldFetchCotonomas =
      // the focused node is changed
      nodeId != this.domain.nodes.focusedId ||
        // or no recent cotonomas has been loaded yet (which means the page being reloaded)
        this.domain.cotonomas.recentIds.isEmpty
    val (cotonomas, cmds) = this.domain.cotonomas.focusAndFetch(cotonomaId)
    this
      .modify(_.domain.nodes).using(_.focus(nodeId))
      .modify(_.domain.cotonomas).setTo(cotonomas)
      .modify(_.domain.cotos).setTo(Cotos())
      .modify(_.domain.links).setTo(Links())
      .modify(_.domain.cotonomas.recentLoading).setTo(shouldFetchCotonomas)
      .modify(_.timeline).using(_.init) match {
      case model =>
        (
          model,
          cmds ++ Seq(
            if (shouldFetchCotonomas)
              Cotonomas.fetchRecent(nodeId, 0)
            else
              Cmd.none,
            SectionTimeline.fetch(None, Some(cotonomaId), None, 0),
            Domain.fetchGraphFromCotonoma(cotonomaId)
          )
        )
    }
  }

  def handleLocalNodeEvent(event: LocalNodeEventJson): Model = {
    // ServerStateChanged
    for (change <- event.ServerStateChanged.toOption) {
      val nodeId = Id[Node](change.node_id)
      val notConnected =
        Nullable.toOption(change.not_connected).map(NotConnected(_))
      val clientAsChild =
        Nullable.toOption(change.client_as_child).map(ChildNode(_))
      return this.modify(_.domain.nodes).using(
        _.setServerState(nodeId, notConnected, clientAsChild)
      )
    }

    // ParentSyncProgress
    for (progress <- event.ParentSyncProgress.toOption) {
      val parentSync = this.parentSync.progress(ParentSyncProgress(progress))
      val modalStack =
        if (parentSync.comingManyChanges)
          this.modalStack.openIfNot(Modal.ParentSync())
        else
          this.modalStack
      return this.copy(parentSync = parentSync, modalStack = modalStack)
    }

    // ParentSyncEnd
    for (end <- event.ParentSyncEnd.toOption) {
      return this.modify(_.parentSync).using(_.end(ParentSyncEnd(end)))
    }

    this
  }

  def importChangelog(log: ChangelogEntryJson): (Model, Seq[Cmd[Msg]]) = {
    val expectedNumber = this.domain.lastChangeNumber + 1
    if (log.serial_number == expectedNumber)
      this
        .applyChange(log.change)
        .modify(_._1.domain.lastChangeNumber).setTo(log.serial_number)
    else
      (
        this.info(
          s"Unexpected change number (expected: ${expectedNumber})",
          Some(log.serial_number.toString())
        ),
        Seq(Browser.send(Msg.ReloadDomain))
      )
  }

  private def applyChange(change: ChangeJson): (Model, Seq[Cmd[Msg]]) = {
    // CreateCoto
    for (cotoJson <- change.CreateCoto.toOption) {
      return this.postCoto(cotoJson)
    }

    // CreateCotonoma
    for (cotonomaJson <- change.CreateCotonoma.toOption) {
      return this.postCotonoma(cotonomaJson)
    }

    // CreateLink
    for (linkJson <- change.CreateLink.toOption) {
      val link = Link(linkJson)
      return (this.modify(_.domain.links).using(_.put(link)), Seq.empty)
    }

    // UpsertNode
    for (nodeJson <- change.UpsertNode.toOption) {
      val node = Node(nodeJson)
      return (this.modify(_.domain.nodes).using(_.put(node)), Seq.empty)
    }

    // CreateNode
    for (createNodeJson <- change.CreateNode.toOption) {
      val model =
        this.modify(_.domain.nodes)
          .using(_.put(Node(createNodeJson.node)))
      return Nullable.toOption(createNodeJson.root)
        .map(model.postCotonoma(_))
        .getOrElse((model, Seq.empty))
    }

    (this, Seq.empty)
  }

  private def postCoto(cotoJson: CotoJson): (Model, Seq[Cmd[Msg]]) = {
    val coto = Coto(cotoJson, true)
    val cotos = this.domain.cotos.put(coto)
    val (cotonomas, cmds) =
      coto.postedInId.map(this.domain.cotonomas.updated(_))
        .getOrElse(this.domain.cotonomas, Seq.empty)
    val timeline =
      if (
        this.domain.inCurrentRoot ||
        coto.postedInId == this.domain.currentCotonomaId
      )
        this.timeline.post(coto.id)
      else
        this.timeline
    (
      this
        .modify(_.domain.cotos).setTo(cotos)
        .modify(_.domain.cotonomas).setTo(cotonomas)
        .modify(_.timeline).setTo(timeline),
      cmds
    )
  }

  private def postCotonoma(
      jsonPair: (CotonomaJson, CotoJson)
  ): (Model, Seq[Cmd[Msg]]) = {
    val cotonoma = Cotonoma(jsonPair._1)
    val coto = Coto(jsonPair._2)
    this
      .modify(_.domain.cotonomas).using(_.post(cotonoma, coto))
      .postCoto(jsonPair._2)
  }
}

package cotoami

import scala.scalajs.js
import org.scalajs.dom.URL
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.utils.Log
import cotoami.utils.facade.Nullable
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._
import cotoami.subparts.Modal

trait Context {
  def time: Time
  def i18n: I18n
  def log: Log
  def uiState: Option[UiState]
  def domain: Domain
  def focusedLocation: Option[Geolocation]
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
    traversals: SectionTraversals.Model = SectionTraversals.Model(),
    geomap: SectionGeomap.Model = SectionGeomap.Model()
) extends Context {
  def path: String = url.pathname + url.search + url.hash

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

  override def focusedLocation: Option[Geolocation] = geomap.focusedLocation

  def importChangelog(log: ChangelogEntryJson): (Model, Cmd[Msg]) = {
    val expectedNumber = domain.lastChangeNumber + 1
    if (log.serial_number == expectedNumber)
      this
        .applyChange(log.change)
        .modify(_._1.domain.lastChangeNumber).setTo(log.serial_number)
    else
      (
        info(
          s"Unexpected change number (expected: ${expectedNumber})",
          Some(log.serial_number.toString())
        ),
        Browser.send(Msg.ReloadDomain)
      )
  }

  private def applyChange(change: ChangeJson): (Model, Cmd[Msg]) = {
    // Handle changes in order of assumed their frequency:
    // CreateCoto
    for (cotoJson <- change.CreateCoto.toOption) {
      return postCoto(cotoJson)
    }

    // CreateCotonoma
    for (cotonomaJson <- change.CreateCotonoma.toOption) {
      return postCotonoma(cotonomaJson)
    }

    // CreateLink
    for (linkJson <- change.CreateLink.toOption) {
      val link = LinkBackend.toModel(linkJson)
      return (this.modify(_.domain.links).using(_.put(link)), Cmd.none)
    }

    // DeleteCoto
    for (deleteCotoJson <- change.DeleteCoto.toOption) {
      return (
        copy(domain = domain.deleteCoto(Id(deleteCotoJson.coto_id))),
        Cmd.none
      )
    }

    // UpsertNode
    for (nodeJson <- change.UpsertNode.toOption) {
      val node = NodeBackend.toModel(nodeJson)
      return (this.modify(_.domain.nodes).using(_.put(node)), Cmd.none)
    }

    // CreateNode
    for (createNodeJson <- change.CreateNode.toOption) {
      val model =
        this.modify(_.domain.nodes).using(
          _.put(NodeBackend.toModel(createNodeJson.node))
        )
      return Nullable.toOption(createNodeJson.root)
        .map(model.postCotonoma(_))
        .getOrElse((model, Cmd.none))
    }

    // SetNodeIcon
    for (setNodeIconJson <- change.SetNodeIcon.toOption) {
      return (
        this
          .modify(_.domain.nodes).using(
            _.setIcon(Id(setNodeIconJson.node_id), setNodeIconJson.icon)
          )
          .modify(_.geomap).using(_.refreshMarkers),
        Cmd.none
      )
    }

    (this, Cmd.none)
  }

  private def postCoto(cotoJson: CotoJson): (Model, Cmd.One[Msg]) = {
    val coto = CotoBackend.toModel(cotoJson, true)
    val cotos = domain.cotos.put(coto)
    val (cotonomas, fetchCotonoma) =
      coto.postedInId.map(domain.cotonomas.updated(_))
        .getOrElse((domain.cotonomas, Cmd.none))
    val timeline =
      (domain.nodes.focused, domain.cotonomas.focused) match {
        case (None, None) => this.timeline.post(coto.id) // all posts
        case (Some(node), None) =>
          if (coto.nodeId == node.id)
            this.timeline.post(coto.id) // posts in the focused node
          else
            this.timeline
        case (_, Some(cotonom)) =>
          if (coto.postedInId == Some(cotonom.id))
            this.timeline.post(coto.id) // posts in the focused cotonoma
          else
            this.timeline
      }
    val geomap =
      if (coto.geolocated)
        this.geomap.addOrRemoveMarkers
      else
        this.geomap
    (
      this
        .modify(_.domain.cotos).setTo(cotos)
        .modify(_.domain.cotonomas).setTo(cotonomas)
        .modify(_.timeline).setTo(timeline)
        .modify(_.geomap).setTo(geomap),
      fetchCotonoma
    )
  }

  private def postCotonoma(
      jsonPair: (CotonomaJson, CotoJson)
  ): (Model, Cmd.One[Msg]) = {
    val cotonoma = CotonomaBackend.toModel(jsonPair._1)
    val coto = CotoBackend.toModel(jsonPair._2)
    this
      .modify(_.domain.cotonomas).using(_.post(cotonoma, coto))
      .postCoto(jsonPair._2)
  }
}

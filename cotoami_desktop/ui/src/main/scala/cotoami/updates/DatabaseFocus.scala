package cotoami.updates

import scala.util.chaining._
import com.softwaremill.quicklens._

import fui.Cmd
import cotoami.{Model, Msg}
import cotoami.models.{Coto, Cotonoma, Id, Node}
import cotoami.backend.CotonomaDetails

object DatabaseFocus {

  def node(nodeId: Option[Id[Node]])(model: Model): (Model, Cmd.Batch[Msg]) =
    model
      .modify(_.repo).using(_.focusNode(nodeId))
      .modify(_.search).using(_.clear)
      .modify(_.flowInput).using(_.onFocusChange)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          model.navCotonomas.fetchRecent()(model)
        val (timeline, timelineCmd) = model.timeline.onFocusChange(model.repo)
        val (geomap, geomapCmd) = model.geomap.onFocusChange(model.repo)
        (
          model.copy(
            navCotonomas = navCotonomas,
            timeline = timeline,
            geomap = geomap
          ),
          Cmd.Batch(
            fetchRecentCotonomas,
            timelineCmd,
            model.repo.fetchGraph,
            geomapCmd
          )
        )
      }

  def cotonoma(
      nodeId: Option[Id[Node]],
      cotonomaId: Id[Cotonoma]
  )(model: Model): (Model, Cmd.Batch[Msg]) = {
    val shouldRecentFetchCotonomas =
      // the focused node is changed
      nodeId != model.repo.nodes.focusedId ||
        // or no recent cotonomas has been loaded yet
        // (which means the page being reloaded)
        model.repo.cotonomas.recentIds.isEmpty
    model
      .modify(_.repo).using(_.focusCotonoma(nodeId, cotonomaId))
      .modify(_.search).using(_.clear)
      .modify(_.flowInput).using(_.onFocusChange)
      .pipe { model =>
        val (navCotonomas, fetchRecentCotonomas) =
          if (shouldRecentFetchCotonomas)
            model.navCotonomas.fetchRecent()(model)
          else
            (model.navCotonomas, Cmd.none)
        val (timeline, timelineCmd) = model.timeline.onFocusChange(model.repo)
        (
          model.copy(navCotonomas = navCotonomas, timeline = timeline),
          Cmd.Batch(
            CotonomaDetails.fetch(cotonomaId)
              .map(Msg.FocusedCotonomaDetailsFetched),
            fetchRecentCotonomas,
            timelineCmd,
            model.repo.fetchGraph
          )
        )
      }
  }

  def coto(
      cotoId: Id[Coto],
      moveTo: Boolean
  )(model: Model): (Model, Cmd.One[Msg]) = {
    model
      .modify(_.repo.cotos).using(_.focus(cotoId))
      .pipe { model =>
        model.repo.cotos.focused match {
          case Some(focusedCoto) =>
            (
              focusedCoto.geolocation match {
                case Some(location) =>
                  model.modify(_.geomap).using(
                    if (moveTo)
                      _.focus(location).moveTo(location)
                    else
                      _.focus(location)
                  )
                case None => model
              },
              model.repo.lazyFetchGraphFrom(cotoId)
            )
          case None => (model, Cmd.none)
        }
      }
  }
}

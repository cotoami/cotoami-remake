package cotoami.browser

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import scala.util.chaining._

import cats.effect.IO

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import marubinotto.fui.{Cmd, Sub}
import marubinotto.libs.tauri

object BrowserDownloads {

  private val DownloadStartedEvent = "browser-download-started"
  private val DownloadFinishedEvent = "browser-download-finished"

  @js.native
  trait DownloadStartedJson extends js.Object {
    val content_label: String = js.native
    val id: String = js.native
    val url: String = js.native
    val source_url: String = js.native
    val path: String = js.native
    val filename: String = js.native
  }

  @js.native
  trait DownloadFinishedJson extends js.Object {
    val content_label: String = js.native
    val id: String = js.native
    val url: String = js.native
    val path: String = js.native
    val success: Boolean = js.native
  }

  enum Status {
    case Downloading, Finished, Failed
  }

  case class Entry(
      id: String,
      url: String,
      filename: String,
      path: String,
      started: Int,
      status: Status = Status.Downloading
  )

  case class Model(
      entries: Seq[Entry] = Seq.empty,
      nextStarted: Int = 1
  ) {
    def nonEmpty: Boolean = entries.nonEmpty
    def downloading: Boolean = entries.exists(_.status == Status.Downloading)

    def started(id: String, url: String, path: String, filename: String): Model =
      copy(
        entries = Entry(id, url, filename, path, nextStarted) +: entries,
        nextStarted = nextStarted + 1
      )

    def finished(id: String, url: String, path: String, success: Boolean): Model = {
      val status = if (success) Status.Finished else Status.Failed
      val index =
        entries.indexWhere(entry =>
          entry.id == id || (id.isBlank && entry.url == url && entry.path == path)
        )
      if (index < 0) this
      else
        copy(entries =
          entries.updated(index, entries(index).copy(status = status))
        )
    }

    def deleteEntry(entry: Entry): Model =
      copy(entries = entries.filterNot(_.id == entry.id))
  }

  sealed trait Msg

  object Msg {
    case class DownloadStarted(
        id: String,
        url: String,
        path: String,
        filename: String
    ) extends Msg
    case class DownloadFinished(
        id: String,
        url: String,
        path: String,
        success: Boolean
    ) extends Msg
    case class EntryDeleted(entry: Entry) extends Msg
    case class EntryOpened(entry: Entry) extends Msg
    case class EntryRevealFinished(result: Either[Throwable, Unit]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case Msg.DownloadStarted(id, url, path, filename) =>
        (model.started(id, url, path, filename), Cmd.none)

      case Msg.DownloadFinished(id, url, path, success) =>
        (model.finished(id, url, path, success), Cmd.none)

      case Msg.EntryDeleted(entry) =>
        (model.deleteEntry(entry), Cmd.none)

      case Msg.EntryOpened(entry) if entry.status == Status.Finished =>
        (
          model,
          tauri.core
            .invoke[Unit](
              "browser_reveal_downloaded_file",
              jso(path = entry.path)
            )
            .toFuture
            .pipe(Cmd.fromFuture)
            .map(Msg.EntryRevealFinished.apply)
        )

      case Msg.EntryOpened(_) =>
        (model, Cmd.none)

      case Msg.EntryRevealFinished(_) =>
        (model, Cmd.none)
    }

  def subscriptions(contentLabel: String): Sub[Msg] =
    Sub.fromCallback[Msg](s"$DownloadStartedEvent-${contentLabel}") {
      dispatch =>
        IO
          .fromFuture(
            IO(
              tauri.event
                .listen[DownloadStartedJson](
                  DownloadStartedEvent,
                  event =>
                    Option(event.payload)
                      .filter(_.content_label == contentLabel)
                      .foreach(payload =>
                        dispatch(
                          Msg.DownloadStarted(
                            payload.id,
                            payload.source_url,
                            payload.path,
                            payload.filename
                          )
                        )
                      )
                )
                .toFuture
            )
          )
          .map(unlisten => IO(unlisten()))
    }.combine(
      Sub.fromCallback[Msg](s"$DownloadFinishedEvent-${contentLabel}") {
        dispatch =>
          IO
            .fromFuture(
              IO(
                tauri.event
                  .listen[DownloadFinishedJson](
                    DownloadFinishedEvent,
                    event =>
                      Option(event.payload)
                        .filter(_.content_label == contentLabel)
                        .foreach(payload =>
                          dispatch(
                            Msg.DownloadFinished(
                              payload.id,
                              payload.url,
                              payload.path,
                              payload.success
                            )
                          )
                        )
                  )
                  .toFuture
              )
            )
            .map(unlisten => IO(unlisten()))
      }
    )

  def view(
      model: Model,
      paneTitle: String,
      emptyText: String,
      deleteTitle: String,
      onClose: () => Unit,
      dispatch: Msg => Unit
  ): ReactElement = {
    def downloadEntry(entry: Entry): ReactElement = {
      val canOpen = entry.status == Status.Finished
      li(
        className := s"browser-trail-entry browser-download-entry ${entry.status.toString.toLowerCase}",
        key := entry.id
      )(
        button(
          className := "browser-trail-open",
          `type` := "button",
          disabled := !canOpen,
          title := entry.path,
          onMouseDown := (e => e.preventDefault()),
          onClick := (_ => dispatch(Msg.EntryOpened(entry)))
        )(
          span(className := "browser-trail-favicon browser-download-icon")(
            materialSymbol(
              entry.status match {
                case Status.Downloading => "progress_activity"
                case Status.Finished    => "draft"
                case Status.Failed      => "error"
              },
              if (entry.status == Status.Downloading) "loading" else ""
            )
          ),
          span(className := "browser-trail-text")(
            span(className := "browser-trail-title")(entry.filename),
            span(className := "browser-trail-url")(
              entry.url
            )
          )
        ),
        button(
          className := "browser-trail-delete",
          `type` := "button",
          title := deleteTitle,
          onMouseDown := (e => {
            e.preventDefault()
            e.stopPropagation()
          }),
          onClick := (e => {
            e.stopPropagation()
            dispatch(Msg.EntryDeleted(entry))
          })
        )(
          materialSymbol("delete")
        )
      )
    }

    div(className := "browser-trail-pane browser-downloads-pane")(
      header(className := "browser-trail-header")(
        h2()(paneTitle),
        button(
          className := "browser-action",
          `type` := "button",
          title := "Close",
          onClick := (_ => onClose())
        )(materialSymbol("close"))
      ),
      div(className := "browser-trail-menu")(
        if (model.entries.isEmpty)
          div(className := "browser-trail-empty")(
            emptyText
          )
        else
          ul(className := "browser-trail-list browser-download-list")(
            model.entries.map(downloadEntry)*
          )
      )
    )
  }
}

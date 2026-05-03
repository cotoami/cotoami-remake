package cotoami.browser

import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.materialSymbol
import marubinotto.fui.Cmd

object BrowserTrail {

  case class Entry(
      url: String,
      title: Option[String],
      origin: String,
      host: String,
      path: String,
      faviconUrl: String,
      firstVisited: Int,
      lastVisited: Int
  ) {
    def label: String =
      title.map(_.trim).filter(_.nonEmpty).getOrElse(host)
  }

  case class Group(
      parent: Entry,
      children: Seq[Entry],
      lastVisited: Int
  )

  case class Model(
      entries: Seq[Entry] = Seq.empty,
      nextVisit: Int = 1
  ) {
    def remember(url: String, title: Option[String]): Model =
      parse(url) match {
        case Some(parsed) =>
          val nextEntries =
            entries.indexWhere(_.url == parsed.url) match {
              case -1 =>
                entries :+ Entry(
                  url = parsed.url,
                  title = title,
                  origin = parsed.origin,
                  host = parsed.host,
                  path = parsed.path,
                  faviconUrl = s"${parsed.origin}/favicon.ico",
                  firstVisited = nextVisit,
                  lastVisited = nextVisit
                )
              case index =>
                entries.updated(
                  index,
                  entries(index).copy(
                    title = title.orElse(entries(index).title),
                    lastVisited = nextVisit
                  )
                )
            }
          copy(entries = nextEntries, nextVisit = nextVisit + 1)
        case None => this
      }

    def groups: Seq[Group] =
      entries.map(_.origin).distinct.reverse.flatMap(origin =>
        entries.filter(_.origin == origin) match {
          case Seq() => None
          case entriesInOrigin =>
            val parent = entriesInOrigin.minBy(_.firstVisited)
            val children = entriesInOrigin
              .filterNot(_.url == parent.url)
              .reverse
              .toSeq
            Some(
              Group(
                parent,
                children,
                entriesInOrigin.map(_.lastVisited).max
              )
            )
        }
      )

    def deleteEntry(entry: Entry, level: Int): Model =
      if (level == 1)
        copy(entries = entries.filterNot(_.origin == entry.origin))
      else
        copy(entries = entries.filterNot(_.url == entry.url))

    def entryForUrl(url: String): Option[Entry] =
      parse(url).flatMap(parsed =>
        entries.find(_.url == parsed.url)
      )
  }

  sealed trait Msg

  object Msg {
    case class EntryDeleted(entry: Entry, level: Int) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    msg match {
      case Msg.EntryDeleted(entry, level) =>
        (model.deleteEntry(entry, level), Cmd.none)
    }

  def view(
      model: Model,
      currentUrl: String,
      paneTitle: String,
      emptyText: String,
      onClose: () => Unit,
      onNavigate: String => Unit,
      dispatch: Msg => Unit
  ): ReactElement = {
    val currentEntry = model.entryForUrl(currentUrl)

    def trailEntry(
        entry: Entry,
        level: Int,
        displayUrl: String,
        entryKey: Option[String] = None
    ): ReactElement = {
      val current = currentEntry.exists(_.url == entry.url)
      val canDelete =
        !current && !(level == 1 && currentEntry.exists(_.origin == entry.origin))
      val entryClass = optionalClasses(
        Seq(
          (s"browser-trail-entry level-${level}", true),
          ("current", current)
        )
      )
      val deleteButton = Option.when(canDelete) {
        button(
          className := "browser-trail-delete",
          `type` := "button",
          title := "Delete",
          onMouseDown := (e => {
            e.preventDefault()
            e.stopPropagation()
          }),
          onClick := (e => {
            e.stopPropagation()
            dispatch(Msg.EntryDeleted(entry, level))
          })
        )(
          materialSymbol("delete")
        )
      }
      val content =
        Seq(
          button(
            className := "browser-trail-open",
            `type` := "button",
            title := entry.url,
            onMouseDown := (e => e.preventDefault()),
            onClick := (_ => onNavigate(entry.url))
          )(
            span(className := "browser-trail-favicon")(
              materialSymbol("language"),
              img(
                alt := "",
                src := entry.faviconUrl,
                onError := (e =>
                  e.currentTarget
                    .asInstanceOf[dom.HTMLImageElement]
                    .style
                    .display = "none"
                )
              )
            ),
            span(className := "browser-trail-text")(
              span(className := "browser-trail-title")(entry.label),
              span(className := "browser-trail-url")(displayUrl)
            )
          )
        ) ++ deleteButton.toSeq
      entryKey match {
        case Some(value) =>
          li(className := entryClass, key := value)(
            content*
          )
        case None =>
          li(className := entryClass)(content*)
      }
    }

    def trailList: ReactElement = {
      val groups = model.groups
      div(className := "browser-trail-menu")(
        if (groups.isEmpty)
          div(className := "browser-trail-empty")(
            emptyText
          )
        else
          ul(className := "browser-trail-groups")(
            groups.map(group =>
              li(className := "browser-trail-group", key := group.parent.origin)(
                ul(className := "browser-trail-list")(
                  (Seq(trailEntry(group.parent, 1, group.parent.url)) ++
                    group.children.map(child =>
                      trailEntry(child, 2, child.path, Some(child.url))
                    ))*
                )
              )
            )*
          )
      )
    }

    div(className := "browser-trail-pane")(
      header(className := "browser-trail-header")(
        h2()(paneTitle),
        button(
          className := "browser-action",
          `type` := "button",
          title := "Close",
          onClick := (_ => onClose())
        )(materialSymbol("close"))
      ),
      trailList
    )
  }

  private case class ParsedUrl(
      url: String,
      origin: String,
      host: String,
      path: String
  )

  private def parse(url: String): Option[ParsedUrl] =
    try {
      val parsed = new URL(url)
      Option
        .when(parsed.protocol == "http:" || parsed.protocol == "https:") {
          val path = Seq(parsed.pathname, parsed.search, parsed.hash)
            .filter(_.nonEmpty)
            .mkString
          ParsedUrl(
            url = parsed.href,
            origin = parsed.origin,
            host = parsed.host,
            path = if (path.nonEmpty) path else "/"
          )
        }
    } catch {
      case _: Throwable => None
    }
}

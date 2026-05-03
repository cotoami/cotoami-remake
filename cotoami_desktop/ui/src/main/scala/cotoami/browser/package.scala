package cotoami

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Dynamic.{literal => jso}
import org.scalajs.dom.{URL, window as domWindow}

import marubinotto.libs.tauri

package object browser {

  private def decodeQueryPart(value: String): String =
    js.URIUtils.decodeURIComponent(value.replace("+", " "))

  private def currentBrowserShellContentLabel: Option[String] =
    try {
      val params = Option(new URL(domWindow.location.href).search).toSeq
        .flatMap(_.stripPrefix("?").split("&"))
        .filter(_.nonEmpty)
        .flatMap(part =>
          part.split("=", 2).toList match {
            case key :: value :: Nil =>
              Some(decodeQueryPart(key) -> decodeQueryPart(value))
            case key :: Nil =>
              Some(decodeQueryPart(key) -> "")
            case _ => None
          }
        )
        .toMap
      Option.when(params.get("browserShell").contains("1")) {
        params.get("contentLabel").filter(_.nonEmpty)
      }.flatten
    } catch {
      case _: Throwable => None
    }

  def openUrlInBrowser(
      url: String,
      locale: Option[String] = None,
      databaseFolder: Option[String] = None,
      focusedNodeId: Option[String] = None,
      focusedCotonomaId: Option[String] = None,
      theme: Option[String] = None
  ): Unit =
    if (tauri.isSupportedBrowserUrl(url)) {
      currentBrowserShellContentLabel match {
        case Some(contentLabel) =>
          tauri.core.invoke[Unit](
            "browser_navigate",
            jso(
              contentLabel = contentLabel,
              url = url
            )
          )
          ()
        case None =>
          openUrlInNewWindow(
            Some(url),
            locale,
            databaseFolder,
            focusedNodeId,
            focusedCotonomaId,
            theme
          )
      }
    }

  def openBlankInNewWindow(
      locale: Option[String] = None,
      databaseFolder: Option[String] = None,
      focusedNodeId: Option[String] = None,
      focusedCotonomaId: Option[String] = None,
      theme: Option[String] = None
  ): Unit =
    openUrlInNewWindow(
      None,
      locale,
      databaseFolder,
      focusedNodeId,
      focusedCotonomaId,
      theme
    )

  def openUrlInNewWindow(
      url: String,
      locale: Option[String] = None,
      databaseFolder: Option[String] = None,
      focusedNodeId: Option[String] = None,
      focusedCotonomaId: Option[String] = None,
      theme: Option[String] = None
  ): Unit =
    if (tauri.isSupportedBrowserUrl(url))
      openUrlInNewWindow(
        Some(url),
        locale,
        databaseFolder,
        focusedNodeId,
        focusedCotonomaId,
        theme
      )

  private def openUrlInNewWindow(
      url: Option[String],
      locale: Option[String],
      databaseFolder: Option[String],
      focusedNodeId: Option[String],
      focusedCotonomaId: Option[String],
      theme: Option[String]
  ): Unit = {
    tauri.core.invoke[Unit](
      "open_browser_window",
      jso(
        url = url.orUndefined,
        locale = locale.orUndefined,
        databaseFolder = databaseFolder.orUndefined,
        focusedNodeId = focusedNodeId.orUndefined,
        focusedCotonomaId = focusedCotonomaId.orUndefined,
        theme = theme.orUndefined
      )
    )
    ()
  }
}

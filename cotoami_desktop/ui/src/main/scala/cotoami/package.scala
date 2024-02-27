import org.scalajs.dom

import slinky.core.facade.{ReactElement, Fragment}
import slinky.web.html._

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import fui.FunctionalUI.Cmd
import cats.effect.IO

import cotoami.entities._

package object cotoami {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      testMsg: Option[String] = None,

      // UI state that can be saved in localStorage separately from app data.
      // It will be `None` before being restored from localStorage on init.
      uiState: Option[UiState] = None,

      // Node
      currentNode: Option[Node] = None,
      localNode: Option[Node] = None
  )

  case class UiState(
      paneToggles: Map[String, Boolean] = Map(),
      paneSizes: Map[String, Int] = Map()
  ) {
    def paneOpened(name: String): Boolean =
      this.paneToggles.getOrElse(name, true)

    def togglePane(name: String): UiState =
      this.copy(paneToggles =
        this.paneToggles + (name -> !this.paneOpened(name))
      )

    def resizePane(name: String, newSize: Int): UiState =
      this.copy(paneSizes = this.paneSizes + (name -> newSize))

    def save(): Cmd[Msg] =
      IO {
        dom.window.localStorage
          .setItem(UiState.StorageKey, this.asJson.toString())
        None
      }
  }

  object UiState {
    val StorageKey = "uiState"

    implicit val encoder: Encoder[UiState] = deriveEncoder
    implicit val decoder: Decoder[UiState] = deriveDecoder

    def restore(createMsg: Option[UiState] => Msg): Cmd[Msg] =
      IO {
        val value = dom.window.localStorage.getItem(StorageKey)
        println(s"localStorage[$StorageKey]: $value")
        val uiState =
          if (value != null) {
            decode[UiState](value) match {
              case Right(uiState) => Some(uiState)
              case Left(error) => {
                println(s"Invalid uiState in localStorage: $value")
                dom.window.localStorage.removeItem(StorageKey)
                None
              }
            }
          } else {
            None
          }
        Some(createMsg(uiState))
      }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Msg
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg
  case class TestCommand(result: Either[Throwable, String]) extends Msg
  case class UiStateRestored(state: Option[UiState]) extends Msg
  case class TogglePane(name: String) extends Msg
  case class ResizePane(name: String, newSize: Int) extends Msg

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def icon(name: String): ReactElement =
    span(className := "material-symbols")(name)

  def paneToggle(paneName: String, dispatch: Msg => Unit): ReactElement =
    Fragment(
      button(
        className := "fold icon",
        title := "Fold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_left")
      ),
      button(
        className := "unfold icon",
        title := "Unfold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_right")
      )
    )
}

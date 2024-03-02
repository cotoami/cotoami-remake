package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.FunctionalUI._
import cotoami.{Model, Msg, Log, icon, tauri, WelcomeModalMsg}
import cats.syntax.foldable

object WelcomeModal {

  case class Model(
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = ""
  )

  sealed trait Msg
  case class DatabaseNameInput(query: String) extends Msg
  case object SelectBaseFolder extends Msg
  case class BaseFolderSelected(result: Either[Throwable, Option[String]])
      extends Msg
  case class FolderNameInput(query: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Seq.empty)

      case SelectBaseFolder =>
        (
          model,
          Seq(
            tauri.selectSingleDirectory(
              "Select a new database directory",
              BaseFolderSelected andThen WelcomeModalMsg,
              Some(model.baseFolder)
            )
          )
        )

      case BaseFolderSelected(Right(path)) =>
        (model.copy(baseFolder = path.getOrElse(model.baseFolder)), Seq.empty)

      case BaseFolderSelected(Left(error)) =>
        (
          model,
          Seq(
            cotoami.log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case FolderNameInput(value) =>
        (model.copy(folderName = value), Seq.empty)
    }

  def view(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    dialog(className := "welcome", open := true)(
      article()(
        header()(
          h1()(
            img(
              className := "app-icon",
              alt := "Cotoami",
              src := "/images/logo/logomark.svg"
            ),
            "Welcome to Cotoami"
          )
        ),
        div(className := "body")(
          div(className := "body-main")(
            newDatabase(model, dispatch),
            openDatabase(model, dispatch)
          )
        )
      )
    )

  def newDatabase(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    section(className := "new-database")(
      h2()("New database"),
      form()(
        // Name
        label(htmlFor := "database-name")("Name"),
        input(
          `type` := "text",
          id := "database-name",
          name := "databaseName",
          value := model.databaseName,
          onInput := ((e) =>
            dispatch(WelcomeModalMsg(DatabaseNameInput(e.target.value)))
          )
        ),

        // Base folder
        label(htmlFor := "select-base-folder")("Base folder"),
        div(className := "file-select")(
          div(className := "file-path")(model.baseFolder),
          button(
            id := "select-base-folder",
            `type` := "button",
            className := "secondary",
            onClick := ((e) => dispatch(WelcomeModalMsg(SelectBaseFolder)))
          )(
            icon("folder")
          )
        ),

        // Folder name
        label(htmlFor := "folder-name")("Folder name to create"),
        input(
          `type` := "text",
          id := "folder-name",
          name := "folderName",
          value := model.folderName,
          onInput := ((e) =>
            dispatch(WelcomeModalMsg(FolderNameInput(e.target.value)))
          )
        ),

        // Create
        div(className := "buttons")(
          button(`type` := "submit", disabled := true)("Create")
        )
      )
    )

  def openDatabase(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    section(className := "open-database")(
      h2()("Open"),
      form()(
        // Folder
        label(htmlFor := "select-database-folder")("Folder"),
        div(className := "file-select")(
          div(className := "file-path")(
          ),
          button(
            id := "select-database-folder",
            `type` := "button",
            className := "secondary"
          )(
            icon("folder")
          )
        ),

        // Open
        div(className := "buttons")(
          button(`type` := "submit", disabled := true)("Open")
        )
      )
    )
}

package cotoami.subparts

import scala.scalajs.js
import slinky.core._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.FunctionalUI._
import cotoami.{Model, Msg, Log, Validation, tauri, WelcomeModalMsg}
import cotoami.components.material_symbol
import cotoami.backend
import cotoami.backend.{Node, DatabaseFolder}
import cats.syntax.foldable

object WelcomeModal {

  case class Model(
      // New database
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = "",
      folderNameErrors: Option[Seq[Validation.Error]] = None,

      // Open an existing database
      databaseFolder: String = "",
      databaseFolderErrors: Option[Seq[Validation.Error]] = None,

      // Shared
      processing: Boolean = false,
      systemError: Option[String] = None
  ) {
    def validateNewDatabaseInputs(): Boolean =
      Node
        .validateName(this.databaseName)
        .isEmpty && this.folderNameErrors.map(_.isEmpty).getOrElse(false)
  }

  sealed trait Msg

  // New database
  case class DatabaseNameInput(query: String) extends Msg
  case object SelectBaseFolder extends Msg
  case class BaseFolderSelected(result: Either[Throwable, Option[String]])
      extends Msg
  case class FolderNameInput(query: String) extends Msg
  case class NewFolderValidation(result: Either[backend.Error, Unit])
      extends Msg
  case object CreateDatabase extends Msg

  // Open an existing database
  case object SelectDatabaseFolder extends Msg
  case class DatabaseFolderSelected(result: Either[Throwable, Option[String]])
      extends Msg
  case class DatabaseFolderValidation(result: Either[backend.Error, Unit])
      extends Msg
  case object OpenDatabase extends Msg
  case class OpenDatabaseIn(folder: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Seq.empty)

      case SelectBaseFolder =>
        (
          model.copy(folderNameErrors = None),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a base folder",
                Some(model.baseFolder)
              )
              .map((BaseFolderSelected andThen WelcomeModalMsg)(_))
          )
        )

      case BaseFolderSelected(Right(path)) => {
        model.copy(baseFolder = path.getOrElse(model.baseFolder)) match {
          case model => (model, validateNewFolder(model))
        }
      }

      case BaseFolderSelected(Left(error)) =>
        (
          model.copy(systemError = Some(error.toString())),
          Seq(
            cotoami.log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case FolderNameInput(value) =>
        model.copy(folderName = value, folderNameErrors = None) match {
          case model => (model, validateNewFolder(model))
        }

      case NewFolderValidation(Right(_)) =>
        (model.copy(folderNameErrors = Some(Seq.empty)), Seq.empty)

      case NewFolderValidation(Left(error)) =>
        (
          model.copy(folderNameErrors =
            Some(Seq(backend.Error.toValidationError(error)))
          ),
          Seq.empty
        )

      case CreateDatabase =>
        (model.copy(processing = true), Seq(createDatabase(model)))

      case SelectDatabaseFolder =>
        (
          model.copy(databaseFolderErrors = None),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a database folder",
                None
              )
              .map((DatabaseFolderSelected andThen WelcomeModalMsg)(_))
          )
        )

      case DatabaseFolderSelected(Right(path)) => {
        model.copy(databaseFolder =
          path.getOrElse(model.databaseFolder)
        ) match {
          case model => (model, validateDatabaseFolder(model))
        }
      }

      case DatabaseFolderSelected(Left(error)) =>
        (
          model.copy(systemError = Some(error.toString())),
          Seq(
            cotoami.log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case DatabaseFolderValidation(Right(_)) =>
        (model.copy(databaseFolderErrors = Some(Seq.empty)), Seq.empty)

      case DatabaseFolderValidation(Left(error)) =>
        (
          model.copy(databaseFolderErrors =
            Some(Seq(backend.Error.toValidationError(error)))
          ),
          Seq.empty
        )

      case OpenDatabase =>
        (model.copy(processing = true), Seq(openDatabase(model.databaseFolder)))

      case OpenDatabaseIn(folder) =>
        (model.copy(processing = true), Seq(openDatabase(folder)))
    }

  def validateNewFolder(model: Model): Seq[Cmd[cotoami.Msg]] =
    if (!model.baseFolder.isBlank && !model.folderName.isBlank)
      Seq(
        tauri
          .invokeCommand(
            "validate_new_database_folder",
            js.Dynamic
              .literal(
                baseFolder = model.baseFolder,
                folderName = model.folderName
              )
          )
          .map((NewFolderValidation andThen WelcomeModalMsg)(_))
      )
    else
      Seq()

  def createDatabase(model: Model): Cmd[cotoami.Msg] =
    tauri
      .invokeCommand(
        "create_database",
        js.Dynamic
          .literal(
            databaseName = model.databaseName,
            baseFolder = model.baseFolder,
            folderName = model.folderName
          )
      )
      .map(cotoami.DatabaseOpened(_))

  def validateDatabaseFolder(model: Model): Seq[Cmd[cotoami.Msg]] =
    if (!model.databaseFolder.isBlank)
      Seq(
        tauri
          .invokeCommand(
            "validate_database_folder",
            js.Dynamic
              .literal(
                databaseFolder = model.databaseFolder
              )
          )
          .map((DatabaseFolderValidation andThen WelcomeModalMsg)(_))
      )
    else
      Seq()

  def openDatabase(folder: String): Cmd[cotoami.Msg] =
    tauri
      .invokeCommand(
        "open_database",
        js.Dynamic
          .literal(
            databaseFolder = folder
          )
      )
      .map(cotoami.DatabaseOpened(_))

  def view(
      model: Model,
      recentDatabases: Option[Seq[DatabaseFolder]],
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
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
          model.systemError.map(e => div(className := "system-error")(e)),
          div(className := "body-main")(
            recentDatabases.flatMap(recent(model, _, dispatch)),
            div(className := "create-or-open")(
              newDatabase(model, dispatch),
              openDatabase(model, dispatch)
            )
          )
        )
      )
    )

  def recent(
      model: Model,
      databases: Seq[DatabaseFolder],
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    if (databases.isEmpty) {
      None
    } else {
      Some(
        section(className := "recent-databases")(
          h2()("Recent"),
          ul()(
            databases.map(db =>
              li(key := db.path)(
                button(
                  className := "database default",
                  title := db.name,
                  disabled := model.processing,
                  onClick := ((e) =>
                    dispatch(WelcomeModalMsg(OpenDatabaseIn(db.path)))
                  )
                )(
                  img(
                    className := "node-icon",
                    alt := db.name,
                    src := s"data:image/png;base64,${db.icon}"
                  ),
                  db.name,
                  section(className := "database-path")(db.path)
                )
              )
            ): _*
          )
        )
      )
    }

  def newDatabase(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    section(className := "new-database")(
      h2()("New database"),
      form()(
        // Name
        databaseNameInput(model, dispatch),

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
            material_symbol("folder")
          )
        ),

        // Folder name
        label(htmlFor := "folder-name")("Folder name to create"),
        div(className := "input-with-validation")(
          input(
            `type` := "text",
            id := "folder-name",
            name := "folderName",
            value := model.folderName,
            Validation.ariaInvalid(model.folderNameErrors),
            // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
            // (onChange is almost the same as onInput in React)
            onChange := ((e) =>
              dispatch(WelcomeModalMsg(FolderNameInput(e.target.value)))
            )
          ),
          Validation.validationErrorDiv(model.folderNameErrors)
        ),

        // Create
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.validateNewDatabaseInputs() || model.processing,
            onClick := ((e) => dispatch(WelcomeModalMsg(CreateDatabase)))
          )(
            "Create"
          )
        )
      )
    )

  def databaseNameInput(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    val errors =
      if (model.databaseName.isEmpty) None
      else Some(Node.validateName(model.databaseName))
    Fragment(
      label(htmlFor := "database-name")("Name"),
      div(className := "input-with-validation")(
        input(
          `type` := "text",
          id := "database-name",
          name := "databaseName",
          value := model.databaseName,
          Validation.ariaInvalid(errors),
          // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
          onChange := ((e) =>
            dispatch(WelcomeModalMsg(DatabaseNameInput(e.target.value)))
          )
        ),
        Validation.validationErrorDiv(errors)
      )
    )
  }

  def openDatabase(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    section(className := "open-database")(
      h2()("Open"),
      form()(
        // Folder
        label(htmlFor := "select-database-folder")("Folder"),
        div(className := "input-with-validation")(
          div(className := "file-select")(
            div(className := "file-path")(model.databaseFolder),
            button(
              id := "select-database-folder",
              `type` := "button",
              className := "secondary",
              onClick := ((e) =>
                dispatch(WelcomeModalMsg(SelectDatabaseFolder))
              )
            )(
              material_symbol("folder")
            )
          ),
          Validation.validationErrorDiv(model.databaseFolderErrors)
        ),

        // Open
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := model.databaseFolderErrors
              .map(!_.isEmpty)
              .getOrElse(true) || model.processing,
            onClick := ((e) => dispatch(WelcomeModalMsg(OpenDatabase)))
          )("Open")
        )
      )
    )
}

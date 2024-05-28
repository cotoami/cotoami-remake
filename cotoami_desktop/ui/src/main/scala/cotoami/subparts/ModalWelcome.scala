package cotoami.subparts

import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.FunctionalUI._
import cotoami.{log_error, tauri, ModalWelcomeMsg}
import cotoami.utils.Validation
import cotoami.components.materialSymbol
import cotoami.backend.{DatabaseOpenedJson, ErrorJson, Node}

object ModalWelcome {

  case class Model(
      // New database
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = "",
      folderNameValidation: Validation.Result = Validation.Result(),

      // Open an existing database
      databaseFolder: String = "",
      databaseFolderValidation: Validation.Result = Validation.Result(),

      // Shared
      processing: Boolean = false,
      systemError: Option[String] = None
  ) {
    def validateDatabaseName: Validation.Result =
      if (this.databaseName.isBlank())
        Validation.Result()
      else
        Validation.Result(Node.validateName(this.databaseName))

    def validateNewDatabaseInputs: Boolean =
      this.validateDatabaseName.validated &&
        this.folderNameValidation.validated
  }

  sealed trait Msg

  // New database
  case class DatabaseNameInput(query: String) extends Msg
  case object SelectBaseFolder extends Msg
  case class BaseFolderSelected(result: Either[Throwable, Option[String]])
      extends Msg
  case class FolderNameInput(query: String) extends Msg
  case class NewFolderValidation(result: Either[ErrorJson, Unit]) extends Msg
  case object CreateDatabase extends Msg

  // Open an existing database
  case object SelectDatabaseFolder extends Msg
  case class DatabaseFolderSelected(result: Either[Throwable, Option[String]])
      extends Msg
  case class DatabaseFolderValidation(result: Either[ErrorJson, Unit])
      extends Msg
  case object OpenDatabase extends Msg
  case class OpenDatabaseIn(folder: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Seq.empty)

      case SelectBaseFolder =>
        (
          model.copy(folderNameValidation = Validation.Result()),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a base folder",
                Some(model.baseFolder)
              )
              .map(BaseFolderSelected andThen ModalWelcomeMsg)
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
            log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case FolderNameInput(value) =>
        model.copy(
          folderName = value,
          folderNameValidation = Validation.Result()
        ) match {
          case model => (model, validateNewFolder(model))
        }

      case NewFolderValidation(Right(_)) =>
        (
          model.copy(folderNameValidation = Validation.Result.validated()),
          Seq.empty
        )

      case NewFolderValidation(Left(error)) =>
        (
          model.copy(folderNameValidation =
            Validation.Result(Seq(ErrorJson.toValidationError(error)))
          ),
          Seq.empty
        )

      case CreateDatabase =>
        (model.copy(processing = true), Seq(createDatabase(model)))

      case SelectDatabaseFolder =>
        (
          model.copy(databaseFolderValidation = Validation.Result()),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a database folder",
                None
              )
              .map(DatabaseFolderSelected andThen ModalWelcomeMsg)
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
            log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case DatabaseFolderValidation(Right(_)) =>
        (
          model.copy(databaseFolderValidation = Validation.Result.validated()),
          Seq.empty
        )

      case DatabaseFolderValidation(Left(error)) =>
        (
          model.copy(databaseFolderValidation =
            Validation.Result(Seq(ErrorJson.toValidationError(error)))
          ),
          Seq.empty
        )

      case OpenDatabase =>
        (
          model.copy(processing = true),
          Seq(cotoami.openDatabase(model.databaseFolder))
        )

      case OpenDatabaseIn(folder) =>
        (model.copy(processing = true), Seq(cotoami.openDatabase(folder)))
    }

  private def validateNewFolder(model: Model): Seq[Cmd[cotoami.Msg]] =
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
          .map(NewFolderValidation andThen ModalWelcomeMsg)
      )
    else
      Seq()

  private def createDatabase(model: Model): Cmd[cotoami.Msg] =
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
      .map(cotoami.DatabaseOpened)

  private def validateDatabaseFolder(model: Model): Seq[Cmd[cotoami.Msg]] =
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
          .map(DatabaseFolderValidation andThen ModalWelcomeMsg)
      )
    else
      Seq()

  def apply(
      model: Model,
      recentDatabases: Seq[DatabaseOpenedJson],
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    dialog(
      className := "welcome",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
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
            recent(model, recentDatabases, dispatch),
            div(className := "create-or-open")(
              newDatabase(model, dispatch),
              openDatabase(model, dispatch)
            )
          )
        )
      )
    )

  private def recent(
      model: Model,
      databases: Seq[DatabaseOpenedJson],
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
              li(key := db.folder)(
                button(
                  className := "database default",
                  title := db.name,
                  disabled := model.processing,
                  onClick := (_ =>
                    dispatch(ModalWelcomeMsg(OpenDatabaseIn(db.folder)))
                  )
                )(
                  img(
                    className := "node-icon",
                    alt := db.name,
                    src := s"data:image/png;base64,${db.icon}"
                  ),
                  db.name,
                  section(className := "database-path")(db.folder)
                )
              )
            ): _*
          )
        )
      )
    }

  private def newDatabase(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
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
            onClick := (_ => dispatch(ModalWelcomeMsg(SelectBaseFolder)))
          )(
            materialSymbol("folder")
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
            Validation.ariaInvalid(model.folderNameValidation),
            // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
            // (onChange is almost the same as onInput in React)
            onChange := ((e) =>
              dispatch(ModalWelcomeMsg(FolderNameInput(e.target.value)))
            )
          ),
          Validation.sectionValidationError(model.folderNameValidation)
        ),

        // Create
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.validateNewDatabaseInputs || model.processing,
            onClick := (_ => dispatch(ModalWelcomeMsg(CreateDatabase)))
          )(
            "Create"
          )
        )
      )
    )

  private def databaseNameInput(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    val errors = model.validateDatabaseName
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
            dispatch(ModalWelcomeMsg(DatabaseNameInput(e.target.value)))
          )
        ),
        Validation.sectionValidationError(errors)
      )
    )
  }

  private def openDatabase(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
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
              onClick := (_ => dispatch(ModalWelcomeMsg(SelectDatabaseFolder)))
            )(
              materialSymbol("folder")
            )
          ),
          Validation.sectionValidationError(model.databaseFolderValidation)
        ),

        // Open
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.databaseFolderValidation.validated || model.processing,
            onClick := (_ => dispatch(ModalWelcomeMsg(OpenDatabase)))
          )("Open")
        )
      )
    )
}

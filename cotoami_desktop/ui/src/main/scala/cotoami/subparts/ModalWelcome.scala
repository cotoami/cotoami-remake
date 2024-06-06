package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui._
import cotoami.{log_error, tauri}
import cotoami.utils.Validation
import cotoami.components.materialSymbol
import cotoami.backend.{
  DatabaseInfo,
  DatabaseInfoJson,
  DatabaseOpenedJson,
  ErrorJson,
  Node
}

object ModalWelcome {

  case class Model(
      // New database
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = "",
      folderNameValidation: Validation.Result = Validation.Result.toBeValidated,

      // Open an existing database
      databaseFolder: String = "",
      databaseFolderValidation: Validation.Result =
        Validation.Result.toBeValidated,

      // Shared
      processing: Boolean = false,
      error: Option[String] = None
  ) {
    def validateDatabaseName: Validation.Result =
      if (this.databaseName.isBlank())
        Validation.Result.toBeValidated
      else
        Validation.Result(Node.validateName(this.databaseName))

    def readyToCreate: Boolean =
      !this.processing &&
        this.validateDatabaseName.validated &&
        this.folderNameValidation.validated

    def readyToOpen: Boolean =
      !this.processing && this.databaseFolderValidation.validated
  }

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = Modal.WelcomeMsg(this).pipe(cotoami.ModalMsg)
  }

  private def appMsgTagger[T](tagger: T => Msg): T => cotoami.Msg =
    tagger andThen Modal.WelcomeMsg andThen cotoami.ModalMsg

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
  case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfoJson])
      extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Seq.empty)

      case SelectBaseFolder =>
        (
          model.copy(folderNameValidation = Validation.Result.toBeValidated),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a base folder",
                Some(model.baseFolder)
              )
              .map(appMsgTagger(BaseFolderSelected(_)))
          )
        )

      case BaseFolderSelected(Right(path)) => {
        model.copy(baseFolder = path.getOrElse(model.baseFolder)) match {
          case model => (model, validateNewFolder(model))
        }
      }

      case BaseFolderSelected(Left(error)) =>
        (
          model.copy(error = Some(error.toString())),
          Seq(
            log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case FolderNameInput(value) =>
        model.copy(
          folderName = value,
          folderNameValidation = Validation.Result.toBeValidated
        ) match {
          case model => (model, validateNewFolder(model))
        }

      case NewFolderValidation(Right(_)) =>
        (
          model.copy(folderNameValidation = Validation.Result.validated),
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
          model.copy(databaseFolderValidation =
            Validation.Result.toBeValidated
          ),
          Seq(
            tauri
              .selectSingleDirectory(
                "Select a database folder",
                None
              )
              .map(appMsgTagger(DatabaseFolderSelected(_)))
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
          model.copy(error = Some(error.toString())),
          Seq(
            log_error("Folder selection error.", Some(error.toString()))
          )
        )

      case DatabaseFolderValidation(Right(_)) =>
        (
          model.copy(databaseFolderValidation = Validation.Result.validated),
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
          Seq(
            cotoami.openDatabase(model.databaseFolder).map(
              appMsgTagger(DatabaseOpened(_))
            )
          )
        )

      case OpenDatabaseIn(folder) =>
        (
          model.copy(processing = true),
          Seq(
            cotoami.openDatabase(folder).map(
              appMsgTagger(DatabaseOpened(_))
            )
          )
        )

      case DatabaseOpened(Right(json)) => {
        (
          model,
          Seq(
            Browser.send(cotoami.SetDatabaseInfo(DatabaseInfo(json))),
            Browser.send(cotoami.CloseModal)
          )
        )
      }

      case DatabaseOpened(Left(e)) =>
        (
          model.copy(processing = false, error = Some(e.default_message)),
          Seq(log_error(e.default_message, Some(e.toString())))
        )
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
          .map(appMsgTagger(NewFolderValidation(_)))
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
      .map(appMsgTagger(DatabaseOpened(_)))

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
          .map(appMsgTagger(DatabaseFolderValidation(_)))
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
        model.error.map(e => section(className := "error")(e)),
        div(className := "body")(
          sectionRecent(model, recentDatabases, dispatch),
          div(className := "create-or-open")(
            sectionNewDatabase(model, dispatch),
            sectionOpenDatabase(model, dispatch)
          )
        )
      )
    )

  private def sectionRecent(
      model: Model,
      databases: Seq[DatabaseOpenedJson],
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
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
                  onClick := (_ => dispatch(OpenDatabaseIn(db.folder).asAppMsg))
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

  private def sectionNewDatabase(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "new-database")(
      h2()("New database"),
      form()(
        // Name
        inputDatabaseName(model, dispatch),

        // Base folder
        div(className := "input-field")(
          label(htmlFor := "select-base-folder")("Base folder"),
          div(className := "file-select")(
            div(className := "file-path")(model.baseFolder),
            button(
              id := "select-base-folder",
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(SelectBaseFolder.asAppMsg))
            )(materialSymbol("folder"))
          )
        ),

        // Folder name
        div(className := "input-field")(
          label(htmlFor := "folder-name")("Folder name to create"),
          input(
            `type` := "text",
            id := "folder-name",
            name := "folderName",
            value := model.folderName,
            Validation.ariaInvalid(model.folderNameValidation),
            // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
            // (onChange is almost the same as onInput in React)
            onChange := ((e) =>
              dispatch(FolderNameInput(e.target.value).asAppMsg)
            )
          ),
          Validation.sectionValidationError(model.folderNameValidation)
        ),

        // Create button
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToCreate,
            onClick := (e => {
              e.preventDefault()
              dispatch(CreateDatabase.asAppMsg)
            })
          )("Create")
        )
      )
    )

  private def inputDatabaseName(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement = {
    val errors = model.validateDatabaseName
    div(className := "input-field")(
      label(htmlFor := "database-name")("Name"),
      input(
        `type` := "text",
        id := "database-name",
        name := "databaseName",
        value := model.databaseName,
        Validation.ariaInvalid(errors),
        // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
        onChange := (e => dispatch(DatabaseNameInput(e.target.value).asAppMsg))
      ),
      Validation.sectionValidationError(errors)
    )
  }

  private def sectionOpenDatabase(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "open-database")(
      h2()("Open"),
      form()(
        // Folder
        div(className := "input-field")(
          label(htmlFor := "select-database-folder")("Folder"),
          div(className := "file-select")(
            div(className := "file-path")(model.databaseFolder),
            button(
              id := "select-database-folder",
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(SelectDatabaseFolder.asAppMsg))
            )(materialSymbol("folder"))
          ),
          Validation.sectionValidationError(model.databaseFolderValidation)
        ),

        // Open
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToOpen,
            onClick := (e => {
              e.preventDefault()
              dispatch(OpenDatabase.asAppMsg)
            })
          )("Open")
        )
      )
    )
}

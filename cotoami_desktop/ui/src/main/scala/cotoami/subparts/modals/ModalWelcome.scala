package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui._
import cotoami.{log_error, Msg => AppMsg}
import cotoami.libs.tauri
import cotoami.utils.Validation
import cotoami.models.Node
import cotoami.components.materialSymbol
import cotoami.backend.{DatabaseInfo, DatabaseOpenedJson, ErrorJson}
import cotoami.subparts.Modal

object ModalWelcome {

  case class Model(
      // New database
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = "",
      folderNameValidation: Validation.Result =
        Validation.Result.notYetValidated,

      // Open an existing database
      databaseFolder: String = "",
      databaseFolderValidation: Validation.Result =
        Validation.Result.notYetValidated,

      // Shared
      processing: Boolean = false,
      error: Option[String] = None
  ) {
    def validateDatabaseName: Validation.Result =
      if (this.databaseName.isBlank())
        Validation.Result.notYetValidated
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
    def toApp: AppMsg = Modal.Msg.WelcomeMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.WelcomeMsg andThen AppMsg.ModalMsg

    // New database
    case class DatabaseNameInput(query: String) extends Msg
    case object SelectBaseFolder extends Msg
    case class BaseFolderSelected(result: Either[Throwable, Option[String]])
        extends Msg
    case class FolderNameInput(query: String) extends Msg
    case class NewFolderValidation(result: Either[ErrorJson, Null]) extends Msg
    case object CreateDatabase extends Msg

    // Open an existing database
    case object SelectDatabaseFolder extends Msg
    case class DatabaseFolderSelected(result: Either[Throwable, Option[String]])
        extends Msg
    case class DatabaseFolderValidation(result: Either[ErrorJson, Null])
        extends Msg
    case object OpenDatabase extends Msg
    case class OpenDatabaseIn(folder: String) extends Msg
    case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfo])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Cmd.none)

      case Msg.SelectBaseFolder =>
        (
          model.copy(folderNameValidation = Validation.Result.notYetValidated),
          tauri
            .selectSingleDirectory(
              "Select a base folder",
              Some(model.baseFolder)
            )
            .map(Msg.toApp(Msg.BaseFolderSelected(_)))
        )

      case Msg.BaseFolderSelected(Right(path)) => {
        model.copy(baseFolder = path.getOrElse(model.baseFolder)) match {
          case model => (model, validateNewFolder(model))
        }
      }

      case Msg.BaseFolderSelected(Left(error)) =>
        (
          model.copy(error = Some(error.toString())),
          log_error("Folder selection error.", Some(error.toString()))
        )

      case Msg.FolderNameInput(value) =>
        model.copy(
          folderName = value,
          folderNameValidation = Validation.Result.notYetValidated
        ) match {
          case model => (model, validateNewFolder(model))
        }

      case Msg.NewFolderValidation(Right(_)) =>
        (
          model.copy(folderNameValidation = Validation.Result.validated),
          Cmd.none
        )

      case Msg.NewFolderValidation(Left(error)) =>
        (
          model.copy(folderNameValidation =
            Validation.Result(Seq(ErrorJson.toValidationError(error)))
          ),
          Cmd.none
        )

      case Msg.CreateDatabase =>
        (
          model.copy(processing = true),
          DatabaseInfo.createDatabase(
            model.databaseName,
            model.baseFolder,
            model.folderName
          ).map(Msg.toApp(Msg.DatabaseOpened(_)))
        )

      case Msg.SelectDatabaseFolder =>
        (
          model.copy(databaseFolderValidation =
            Validation.Result.notYetValidated
          ),
          tauri
            .selectSingleDirectory(
              "Select a database folder",
              None
            )
            .map(Msg.toApp(Msg.DatabaseFolderSelected(_)))
        )

      case Msg.DatabaseFolderSelected(Right(path)) => {
        model.copy(databaseFolder =
          path.getOrElse(model.databaseFolder)
        ) match {
          case model => (model, validateDatabaseFolder(model))
        }
      }

      case Msg.DatabaseFolderSelected(Left(error)) =>
        (
          model.copy(error = Some(error.toString())),
          log_error("Folder selection error.", Some(error.toString()))
        )

      case Msg.DatabaseFolderValidation(Right(_)) =>
        (
          model.copy(databaseFolderValidation = Validation.Result.validated),
          Cmd.none
        )

      case Msg.DatabaseFolderValidation(Left(error)) =>
        (
          model.copy(databaseFolderValidation =
            Validation.Result(Seq(ErrorJson.toValidationError(error)))
          ),
          Cmd.none
        )

      case Msg.OpenDatabase =>
        (
          model.copy(processing = true),
          DatabaseInfo.openDatabase(model.databaseFolder).map(
            Msg.toApp(Msg.DatabaseOpened(_))
          )
        )

      case Msg.OpenDatabaseIn(folder) =>
        (
          model.copy(processing = true),
          DatabaseInfo.openDatabase(folder).map(
            Msg.toApp(Msg.DatabaseOpened(_))
          )
        )

      case Msg.DatabaseOpened(Right(info)) => {
        (
          model,
          Cmd.Batch(
            Browser.send(AppMsg.SetDatabaseInfo(info)),
            Modal.close(classOf[Modal.Welcome])
          )
        )
      }

      case Msg.DatabaseOpened(Left(e)) =>
        (
          model.copy(processing = false, error = Some(e.default_message)),
          log_error(e.default_message, Some(e.toString()))
        )
    }

  private def validateNewFolder(model: Model): Cmd.Single[AppMsg] =
    if (!model.baseFolder.isBlank && !model.folderName.isBlank)
      tauri
        .invokeCommand(
          "validate_new_database_folder",
          js.Dynamic
            .literal(
              baseFolder = model.baseFolder,
              folderName = model.folderName
            )
        )
        .map(Msg.toApp(Msg.NewFolderValidation(_)))
    else
      Cmd.none

  private def validateDatabaseFolder(model: Model): Cmd.Single[AppMsg] =
    if (!model.databaseFolder.isBlank)
      tauri
        .invokeCommand(
          "validate_database_folder",
          js.Dynamic
            .literal(
              folder = model.databaseFolder
            )
        )
        .map(Msg.toApp(Msg.DatabaseFolderValidation(_)))
    else
      Cmd.none

  def apply(
      model: Model,
      recentDatabases: Seq[DatabaseOpenedJson]
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "welcome",
      error = model.error
    )(
      img(
        className := "app-icon",
        alt := "Cotoami",
        src := "/images/logo/logomark.svg"
      ),
      "Welcome to Cotoami"
    )(
      sectionRecent(model, recentDatabases),
      div(className := "create-or-open")(
        sectionNewDatabase(model),
        sectionOpenDatabase(model)
      )
    )

  private def sectionRecent(
      model: Model,
      databases: Seq[DatabaseOpenedJson]
  )(implicit dispatch: AppMsg => Unit): ReactElement =
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
                    dispatch(Msg.OpenDatabaseIn(db.folder).toApp)
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

  private def sectionNewDatabase(
      model: Model
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    section(className := "new-database")(
      h2()("New database"),
      form()(
        // Name
        inputDatabaseName(model),

        // Base folder
        div(className := "input-field")(
          label(htmlFor := "select-base-folder")("Base folder"),
          div(className := "file-select")(
            div(className := "file-path")(model.baseFolder),
            button(
              id := "select-base-folder",
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(Msg.SelectBaseFolder.toApp))
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
              dispatch(Msg.FolderNameInput(e.target.value).toApp)
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
              dispatch(Msg.CreateDatabase.toApp)
            })
          )("Create")
        )
      )
    )

  private def inputDatabaseName(
      model: Model
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
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
        onChange := (e => dispatch(Msg.DatabaseNameInput(e.target.value).toApp))
      ),
      Validation.sectionValidationError(errors)
    )
  }

  private def sectionOpenDatabase(
      model: Model
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    section(className := "open-database")(
      h2()("Open"),
      form()(
        // Database folder
        div(className := "input-field")(
          label(htmlFor := "select-database-folder")("Database folder"),
          div(className := "file-select")(
            div(className := "file-path")(model.databaseFolder),
            button(
              id := "select-database-folder",
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(Msg.SelectDatabaseFolder.toApp))
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
              dispatch(Msg.OpenDatabase.toApp)
            })
          )("Open")
        )
      )
    )
}

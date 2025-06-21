package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui._
import marubinotto.libs.tauri
import marubinotto.Validation
import marubinotto.facade.Nullable
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.backend.{
  DatabaseInfo,
  DatabaseOpenedJson,
  ErrorJson,
  NodeBackend,
  NodeJson
}
import cotoami.subparts.Modal

object ModalWelcome {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      appUpdate: Option[tauri.updater.Update],

      // New database
      databaseName: String = "",
      baseFolder: String = "",
      folderName: String = "",
      folderNameValidation: Validation.Result =
        Validation.Result.notYetValidated,
      newDatabaseFolder: Option[String] = None,

      // Open an existing database
      databaseFolder: String = "",
      databaseFolderValidation: Validation.Result =
        Validation.Result.notYetValidated,

      // Shared
      processing: Boolean = false,
      error: Option[String] = None
  ) {
    def validateDatabaseName: Validation.Result =
      if (databaseName.isBlank())
        Validation.Result.notYetValidated
      else
        Validation.Result(Node.validateName(databaseName))

    def validateNewFolder: Cmd.One[AppMsg] =
      if (!baseFolder.isBlank && !folderName.isBlank)
        tauri
          .invokeCommand(
            "validate_new_database_folder",
            js.Dynamic.literal(
              baseFolder = baseFolder,
              folderName = folderName
            )
          )
          .map(Msg.NewFolderValidation(_).into)
      else
        Cmd.none

    def validateDatabaseFolder: Cmd.One[AppMsg] =
      if (!databaseFolder.isBlank)
        tauri
          .invokeCommand(
            "validate_database_folder",
            js.Dynamic.literal(
              folder = databaseFolder
            )
          )
          .map(Msg.DatabaseFolderValidation(_).into)
      else
        Cmd.none

    def readyToCreate: Boolean =
      !processing &&
        validateDatabaseName.validated &&
        folderNameValidation.validated

    def readyToOpen: Boolean =
      !processing && databaseFolderValidation.validated

    def openDatabase: (Model, Cmd[AppMsg]) = openDatabaseIn(databaseFolder)

    def openDatabaseIn(
        folder: String,
        ownerPassword: Option[String] = None
    ): (Model, Cmd[AppMsg]) =
      (
        copy(processing = true),
        DatabaseInfo.openDatabase(folder, ownerPassword)
          .map(Msg.DatabaseOpened(folder, _).into)
      )
  }

  object Model {
    def apply(): (Model, Cmd[AppMsg]) =
      (Model(appUpdate = None), checkAppUpdate)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.WelcomeMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class AppUpdateChecked(
        result: Either[Throwable, Option[tauri.updater.Update]]
    ) extends Msg

    // New database
    case class DatabaseNameInput(query: String) extends Msg
    case object SelectBaseFolder extends Msg
    case class BaseFolderSelected(result: Either[Throwable, Option[String]])
        extends Msg
    case class FolderNameInput(query: String) extends Msg
    case class NewFolderValidation(result: Either[ErrorJson, String])
        extends Msg
    case object CreateDatabase extends Msg

    // Open an existing database
    case object SelectDatabaseFolder extends Msg
    case class DatabaseFolderSelected(result: Either[Throwable, Option[String]])
        extends Msg
    case class DatabaseFolderValidation(result: Either[ErrorJson, Null])
        extends Msg
    case object OpenDatabase extends Msg
    case class OpenDatabaseIn(folder: String, password: Option[String] = None)
        extends Msg
    case class DatabaseOpened(
        folder: String,
        result: Either[ErrorJson, DatabaseInfo]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.AppUpdateChecked(Right(appUpdate)) =>
        (model.copy(appUpdate = appUpdate), Cmd.none)

      case Msg.AppUpdateChecked(Left(e)) => {
        println(s"Tauri update check failed: ${e}")
        (model, Cmd.none)
      }

      case Msg.DatabaseNameInput(value) =>
        (model.copy(databaseName = value), Cmd.none)

      case Msg.SelectBaseFolder =>
        (
          model.copy(folderNameValidation = Validation.Result.notYetValidated),
          tauri
            .selectSingleDirectory(
              context.i18n.text.ModalWelcome_new_selectBaseFolder,
              Some(model.baseFolder)
            )
            .map(Msg.BaseFolderSelected(_).into)
        )

      case Msg.BaseFolderSelected(Right(path)) => {
        model.copy(baseFolder = path.getOrElse(model.baseFolder)).pipe(model =>
          (model, model.validateNewFolder)
        )
      }

      case Msg.BaseFolderSelected(Left(e)) =>
        (
          model.copy(error = Some(e.toString())),
          cotoami.error("Folder selection error.", Some(e.toString()))
        )

      case Msg.FolderNameInput(value) =>
        model.copy(
          folderName = value,
          folderNameValidation = Validation.Result.notYetValidated
        ).pipe(model => (model, model.validateNewFolder))

      case Msg.NewFolderValidation(Right(folder)) =>
        (
          model.copy(
            folderNameValidation = Validation.Result.validated,
            newDatabaseFolder = Some(folder)
          ),
          Cmd.none
        )

      case Msg.NewFolderValidation(Left(error)) =>
        (
          model.copy(
            folderNameValidation =
              Validation.Result(Seq(ErrorJson.toValidationError(error))),
            newDatabaseFolder = None
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
          ).map(
            Msg.DatabaseOpened(model.newDatabaseFolder.getOrElse(""), _).into
          )
        )

      case Msg.SelectDatabaseFolder =>
        (
          model.copy(databaseFolderValidation =
            Validation.Result.notYetValidated
          ),
          tauri
            .selectSingleDirectory(
              context.i18n.text.ModalWelcome_open_selectFolder,
              None
            )
            .map(Msg.DatabaseFolderSelected(_).into)
        )

      case Msg.DatabaseFolderSelected(Right(path)) =>
        model.copy(databaseFolder = path.getOrElse(model.databaseFolder)).pipe(
          model => (model, model.validateDatabaseFolder)
        )

      case Msg.DatabaseFolderSelected(Left(e)) =>
        (
          model.copy(error = Some(e.toString())),
          cotoami.error("Folder selection error.", Some(e.toString()))
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

      case Msg.OpenDatabase => model.openDatabase

      case Msg.OpenDatabaseIn(folder, password) =>
        model.openDatabaseIn(folder, password)

      case Msg.DatabaseOpened(folder, Right(info)) => {
        (
          model,
          Cmd.Batch(
            Browser.send(AppMsg.SetDatabaseInfo(info)),
            Modal.close(classOf[Modal.Welcome])
          )
        )
      }

      case Msg.DatabaseOpened(folder, Left(e)) =>
        model.copy(processing = false).pipe { model =>
          if (e.code == "invalid-owner-password") {
            val targetNode = e.params.get("node")
              .map(_.asInstanceOf[NodeJson])
              .map(NodeBackend.toModel)
            (
              model,
              Modal.open(
                Modal.InputPassword(
                  password => Msg.OpenDatabaseIn(folder, Some(password)).into,
                  context.i18n.text.ModalInputOwnerPassword_title,
                  Some(context.i18n.text.ModalInputOwnerPassword_message),
                  targetNode
                )
              )
            )
          } else
            (
              model.copy(error = Some(e.default_message)),
              cotoami.error(e.default_message, e)
            )
        }
    }

  private def checkAppUpdate: Cmd.One[AppMsg] =
    tauri.updater.check(js.undefined).toFuture
      .pipe(Cmd.fromFuture)
      .map(_.map(Nullable.toOption(_)))
      .map(Msg.AppUpdateChecked(_).into)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      recentDatabases: Seq[DatabaseOpenedJson]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "welcome",
      error = model.error
    )(
      img(
        className := "app-icon",
        alt := "Cotoami",
        src := "/images/logo/logomark.svg"
      ),
      context.i18n.text.ModalWelcome_title
    )(
      sectionAppUpdate(),
      div(className := "database")(
        sectionRecent(model, recentDatabases),
        div(className := "create-or-open")(
          sectionNewDatabase(model),
          sectionOpenDatabase(model)
        )
      )
    )

  private def sectionAppUpdate()(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "app-update")(
      materialSymbol("info"),
      div(className := "message")(
        context.i18n.text.ModalWelcome_update_message("0.8.0")
      ),
      button(
        className := "update contrast outline",
        onClick := (_ => dispatch(Modal.Msg.OpenModal(Modal.AppUpdate())))
      )(context.i18n.text.ModalWelcome_update_updateNow)
    )

  private def sectionRecent(
      model: Model,
      databases: Seq[DatabaseOpenedJson]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Option.when(!databases.isEmpty) {
      section(className := "recent-databases")(
        h2()(context.i18n.text.ModalWelcome_recent),
        ScrollArea()(
          ul()(
            databases.map(db =>
              li(key := db.folder)(
                button(
                  className := "database default",
                  title := db.name,
                  disabled := model.processing,
                  onClick := (_ => dispatch(Msg.OpenDatabaseIn(db.folder)))
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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "new-database")(
      h2()(context.i18n.text.ModalWelcome_new),
      form()(
        // Name
        fieldInput(
          name = context.i18n.text.ModalWelcome_new_name,
          inputValue = model.databaseName,
          inputErrors = Some(model.validateDatabaseName),
          onInput = input => dispatch(Msg.DatabaseNameInput(input))
        ),

        // Base folder
        field(
          name = context.i18n.text.ModalWelcome_new_baseFolder
        )(
          div(className := "file-select")(
            div(className := "file-path")(model.baseFolder),
            button(
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(Msg.SelectBaseFolder))
            )(materialSymbol("folder"))
          )
        ),

        // Folder name
        fieldInput(
          name = context.i18n.text.ModalWelcome_new_folderName,
          inputValue = model.folderName,
          inputErrors = Some(model.folderNameValidation),
          onInput = input => dispatch(Msg.FolderNameInput(input))
        ),

        // Create button
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToCreate,
            onClick := (e => {
              e.preventDefault()
              dispatch(Msg.CreateDatabase)
            })
          )(context.i18n.text.ModalWelcome_new_create)
        )
      )
    )

  private def sectionOpenDatabase(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "open-database")(
      h2()(context.i18n.text.ModalWelcome_open),
      form()(
        // Database folder
        field(
          name = context.i18n.text.ModalWelcome_open_folder
        )(
          div(className := "file-select")(
            div(className := "file-path")(model.databaseFolder),
            button(
              `type` := "button",
              className := "secondary",
              onClick := (_ => dispatch(Msg.SelectDatabaseFolder))
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
              dispatch(Msg.OpenDatabase)
            })
          )(context.i18n.text.ModalWelcome_open_open)
        )
      )
    )
}

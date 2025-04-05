package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

import cotoami.libs.tauri
import cotoami.models.{Id, Node}

case class DatabaseInfo(json: DatabaseInfoJson) {
  def folder: String = json.folder
  def localNodeId: Id[Node] = Id(json.local_node_id)
  def initialDataset: InitialDataset = InitialDataset(json.initial_dataset)
  def newOwnerPassword: Option[String] =
    Nullable.toOption(json.new_owner_password)
}

object DatabaseInfo {
  def createDatabase(
      databaseName: String,
      baseFolder: String,
      folderName: String
  ): Cmd.One[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.createDatabase(databaseName, baseFolder, folderName)
      .map(_.map(DatabaseInfo(_)))

  def openDatabase(
      folder: String,
      ownerPassword: Option[String] = None
  ): Cmd.One[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.openDatabase(folder, ownerPassword)
      .map(_.map(DatabaseInfo(_)))

  def newOwnerPassword: Cmd.One[Either[ErrorJson, String]] =
    tauri.invokeCommand("new_owner_password")
}

@js.native
trait DatabaseInfoJson extends js.Object {
  val folder: String = js.native
  val local_node_id: String = js.native
  val initial_dataset: InitialDatasetJson = js.native
  val new_owner_password: Nullable[String] = js.native
}

object DatabaseInfoJson {
  def createDatabase(
      databaseName: String,
      baseFolder: String,
      folderName: String
  ): Cmd.One[Either[ErrorJson, DatabaseInfoJson]] =
    tauri
      .invokeCommand(
        "create_database",
        js.Dynamic
          .literal(
            databaseName = databaseName,
            baseFolder = baseFolder,
            folderName = folderName
          )
      )

  def openDatabase(
      folder: String,
      ownerPassword: Option[String] = None
  ): Cmd.One[Either[ErrorJson, DatabaseInfoJson]] =
    tauri
      .invokeCommand(
        "open_database",
        js.Dynamic
          .literal(
            databaseFolder = folder,
            ownerPassword = ownerPassword.getOrElse(null)
          )
      )
}

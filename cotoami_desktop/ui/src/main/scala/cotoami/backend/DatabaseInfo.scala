package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import marubinotto.facade.Nullable
import marubinotto.libs.tauri

import cotoami.models.{Id, Node}

case class DatabaseInfo(
    json: DatabaseInfoJson,
    initialDataset: InitialDataset
) {
  def folder: String = json.folder
  def localNodeId: Id[Node] = Id(json.local_node_id)
  def newOwnerPassword: Option[String] =
    Nullable.toOption(json.new_owner_password)
}

object DatabaseInfo {
  def decode(json: DatabaseInfoJson): IO[DatabaseInfo] =
    for {
      initialDataset <- InitialDataset.decode(json.initial_dataset)
    } yield DatabaseInfo(json, initialDataset)

  def createDatabase(
      databaseName: String,
      baseFolder: String,
      folderName: String
  ): IO[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.createDatabase(databaseName, baseFolder, folderName)
      .flatMap(_.traverse(DatabaseInfo.decode))

  def openDatabase(
      folder: String,
      ownerPassword: Option[String] = None
  ): IO[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.openDatabase(folder, ownerPassword)
      .flatMap(_.traverse(DatabaseInfo.decode))

  def newOwnerPassword: IO[Either[ErrorJson, String]] =
    tauri.invokeCommandIO("new_owner_password")
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
  ): IO[Either[ErrorJson, DatabaseInfoJson]] =
    tauri
      .invokeCommandIO(
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
  ): IO[Either[ErrorJson, DatabaseInfoJson]] =
    tauri
      .invokeCommandIO(
        "open_database",
        js.Dynamic
          .literal(
            databaseFolder = folder,
            ownerPassword = ownerPassword.getOrElse(null)
          )
      )
}

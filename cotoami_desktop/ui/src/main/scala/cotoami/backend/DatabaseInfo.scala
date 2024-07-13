package cotoami.backend

import scala.scalajs.js
import fui.Cmd
import cotoami.tauri

case class DatabaseInfo(json: DatabaseInfoJson) {
  def folder: String = this.json.folder
  def localNodeId: Id[Node] = Id(this.json.local_node_id)
  def initialDataset: InitialDataset = InitialDataset(this.json.initial_dataset)

  def debug: String = {
    val s = new StringBuilder
    s ++= s"folder: ${this.folder}"
    s ++= s", localNodeId: {${this.localNodeId}}"
    s ++= s", initialDataset: {${this.initialDataset.debug}}"
    s.result()
  }
}

object DatabaseInfo {
  def createDatabase(
      databaseName: String,
      baseFolder: String,
      folderName: String
  ): Cmd[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.createDatabase(databaseName, baseFolder, folderName)
      .map(_.map(DatabaseInfo(_)))

  def openDatabase(folder: String): Cmd[Either[ErrorJson, DatabaseInfo]] =
    DatabaseInfoJson.openDatabase(folder).map(_.map(DatabaseInfo(_)))
}

@js.native
trait DatabaseInfoJson extends js.Object {
  val folder: String = js.native
  val local_node_id: String = js.native
  val initial_dataset: InitialDatasetJson = js.native
}

object DatabaseInfoJson {
  def createDatabase(
      databaseName: String,
      baseFolder: String,
      folderName: String
  ): Cmd[Either[ErrorJson, DatabaseInfoJson]] =
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

  def openDatabase(folder: String): Cmd[Either[ErrorJson, DatabaseInfoJson]] =
    tauri
      .invokeCommand(
        "open_database",
        js.Dynamic
          .literal(
            databaseFolder = folder
          )
      )
}

package cotoami.backend

import scala.scalajs.js

case class DatabaseInfo(json: DatabaseInfoJson) {
  def folder: String = this.json.folder
  def initialDataset: InitialDataset = InitialDataset(this.json.initial_dataset)

  def debug: String = {
    val s = new StringBuilder
    s ++= s"folder: ${this.folder}"
    s ++= s", initialDataset: {${this.initialDataset.debug}}"
    s.result()
  }
}

@js.native
trait DatabaseInfoJson extends js.Object {
  val folder: String = js.native
  val initial_dataset: InitialDatasetJson = js.native
}

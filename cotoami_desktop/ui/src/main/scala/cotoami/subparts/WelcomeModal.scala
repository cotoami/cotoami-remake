package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg, icon}

object WelcomeModal {
  def view(model: Model, dispatch: Msg => Unit): ReactElement =
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

  def newDatabase(model: Model, dispatch: Msg => Unit): ReactElement =
    section(className := "new-database")(
      h2()("New database"),
      form()(
        // Name
        label(htmlFor := "new-database-name")("Name"),
        input(
          `type` := "text",
          id := "new-database-name",
          name := "databaseName"
        ),

        // Base folder
        label(htmlFor := "select-base-folder")("Base folder"),
        div(className := "file-select")(
          div(className := "file-path")(
            model.systemInfo.map(_.app_data_dir).getOrElse("").toString()
          ),
          button(
            id := "select-base-folder",
            `type` := "button",
            className := "secondary",
            onClick := ((e) => dispatch(cotoami.SelectDirectory))
          )(
            icon("folder")
          )
        ),

        // Folder name
        label(htmlFor := "new-folder-name")("Folder name"),
        input(
          `type` := "text",
          id := "new-folder-name",
          name := "folderName"
        ),

        // Create
        div(className := "buttons")(
          button(`type` := "submit", disabled := true)("Create")
        )
      )
    )

  def openDatabase(model: Model, dispatch: Msg => Unit): ReactElement =
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
            className := "secondary",
            onClick := ((e) => dispatch(cotoami.SelectDirectory))
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

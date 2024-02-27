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
          name := "databaseName",
          placeholder := "Database name"
        ),

        // Folder
        label(htmlFor := "new-database-folder")("Folder"),
        div(className := "text-input-with-button")(
          input(
            `type` := "text",
            id := "new-database-folder",
            name := "databaseFolder",
            placeholder := "Database folder path",
            readOnly := true
          ),
          button(
            `type` := "button",
            className := "secondary",
            onClick := ((e) => dispatch(cotoami.SelectDirectory))
          )(
            icon("folder")
          )
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
        label(htmlFor := "database-folder")("Folder"),
        div(className := "text-input-with-button")(
          input(
            `type` := "text",
            id := "database-folder",
            name := "databaseFolder",
            placeholder := "Database folder path",
            readOnly := true
          ),
          button(`type` := "button", className := "secondary")(
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

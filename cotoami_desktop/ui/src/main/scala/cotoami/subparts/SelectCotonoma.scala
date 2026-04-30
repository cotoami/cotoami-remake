package cotoami.subparts

import scala.scalajs.js

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.facade.Nullable
import marubinotto.components.Select

import cotoami.Context
import cotoami.models.{Cotonoma, Id, Node}

object SelectCotonoma {
  sealed trait CotonomaOption extends Select.SelectOption

  class ExistingCotonoma(
      val cotonoma: Cotonoma,
      val disabled: Boolean = false
  ) extends CotonomaOption {
    val value: String = s"cotonoma:${cotonoma.id}"
    val label: String = cotonoma.name
    val isDisabled: Boolean = disabled
  }

  class NewCotonoma(
      val name: String,
      // While we want to use the `Id[Node]` type here, but the type can't be restored
      // when passed back from the Select component. (Probably since it's an AnyVal the
      // type information would be lost).
      val targetNodeId: String
  ) extends CotonomaOption {
    val value: String = s"new-cotonoma:${name}:${targetNodeId}"
    val label: String = name
    val isDisabled: Boolean = false
  }

  def apply(
      className: String = "",
      options: Seq[CotonomaOption],
      placeholder: Option[String] = None,
      inputValue: js.UndefOr[String] = js.undefined,
      defaultValue: Option[CotonomaOption] = None,
      value: Option[CotonomaOption] = None,
      onInputChange: Option[(String, Select.InputActionMeta) => String] = None,
      allowNewCotonomas: Boolean = false,
      showRootMark: Boolean = false,
      rootMarkLabel: Option[String] = None,
      currentCotonomaId: Option[Id[Cotonoma]] = None,
      currentMarkLabel: Option[String] = None,
      isLoading: Boolean = false,
      noOptionsMessage: Option[Select.NoOptionsMessageArg => ReactElement] =
        None,
      isSearchable: Boolean = true,
      isClearable: Boolean = false,
      autoFocus: Boolean = false,
      menuPlacement: String = "auto",
      onChange: Option[(Option[CotonomaOption], Select.ActionMeta) => Unit] =
        None
  )(using context: Context): ReactElement = {
    val filteredOptions = filterNewCotonomas(options, allowNewCotonomas)
    val filteredDefaultValue = filterNewCotonoma(defaultValue, allowNewCotonomas)
    val filteredValue = filterNewCotonoma(value, allowNewCotonomas)
    Select(
      className = Seq("cotonoma-select", className).filter(_.nonEmpty).mkString(" "),
      options = filteredOptions,
      placeholder = placeholder,
      inputValue = inputValue,
      defaultValue = filteredDefaultValue,
      value = filteredValue,
      onInputChange = onInputChange,
      formatOptionLabel = Some(option =>
        divOptionLabel(
          option.asInstanceOf[CotonomaOption],
          showRootMark,
          rootMarkLabel,
          currentCotonomaId,
          currentMarkLabel
        )
      ),
      isLoading = isLoading,
      noOptionsMessage = noOptionsMessage,
      isSearchable = isSearchable,
      isClearable = isClearable,
      autoFocus = autoFocus,
      menuPlacement = menuPlacement,
      onChange = onChange.map(callback =>
        (option, actionMeta) =>
          callback(
            filterNewCotonoma(
              Nullable.toOption(option).map(_.asInstanceOf[CotonomaOption]),
              allowNewCotonomas
            ),
            actionMeta
          )
      )
    )
  }

  private def filterNewCotonomas(
      options: Seq[CotonomaOption],
      allowNewCotonomas: Boolean
  ): Seq[CotonomaOption] =
    if (allowNewCotonomas) options
    else options.filter {
      case _: NewCotonoma => false
      case _              => true
    }

  private def filterNewCotonoma(
      option: Option[CotonomaOption],
      allowNewCotonomas: Boolean
  ): Option[CotonomaOption] =
    option.filter {
      case _: NewCotonoma => allowNewCotonomas
      case _              => true
    }

  private def divOptionLabel(
      option: CotonomaOption,
      showRootMark: Boolean,
      rootMarkLabel: Option[String],
      currentCotonomaId: Option[Id[Cotonoma]],
      currentMarkLabel: Option[String]
  )(using context: Context): ReactElement =
    option match {
      case option: ExistingCotonoma =>
        div(className := "existing-cotonoma")(
          context.repo.nodes.get(option.cotonoma.nodeId).map(PartsNode.imgNode(_)),
          span(className := "cotonoma-name")(option.cotonoma.name),
          Option.when(showRootMark && context.repo.nodes.isNodeRoot(option.cotonoma)) {
            span(className := "root-mark")(
              rootMarkLabel.map(label => s"(${label})").getOrElse("")
            )
          },
          Option.when(currentCotonomaId.contains(option.cotonoma.id)) {
            span(className := "current-mark")(
              currentMarkLabel.map(label => s"(${label})").getOrElse("")
            )
          }
        )

      case option: NewCotonoma =>
        div(className := "new-cotonoma")(
          span(className := "description")(
            s"${context.i18n.text.SelectCotonoma_newCotonoma}:"
          ),
          context.repo.nodes.get(Id[Node](option.targetNodeId)).map(PartsNode.imgNode(_)),
          span(className := "cotonoma-name")(option.name)
        )
    }
}

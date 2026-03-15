package marubinotto.components

import org.scalajs.dom

import marubinotto.optionalClasses
import slinky.core._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.SyntheticKeyboardEvent
import slinky.web.html._

object MarkdownEditor {
  case class Props(
      value: String,
      placeholder: String = "",
      showLineNumbers: Boolean = true,
      onChange: String => Unit = _ => (),
      onFocus: () => Unit = () => (),
      onBlur: () => Unit = () => (),
      onCompositionStart: () => Unit = () => (),
      onCompositionEnd: () => Unit = () => (),
      onKeyDown: SyntheticKeyboardEvent[dom.HTMLTextAreaElement] => Unit =
        _ => ()
  )

  private case class Token(text: String, className: Option[String] = None)

  def apply(
      value: String,
      placeholder: String = "",
      showLineNumbers: Boolean = true,
      onChange: String => Unit = _ => (),
      onFocus: () => Unit = () => (),
      onBlur: () => Unit = () => (),
      onCompositionStart: () => Unit = () => (),
      onCompositionEnd: () => Unit = () => (),
      onKeyDown: SyntheticKeyboardEvent[dom.HTMLTextAreaElement] => Unit =
        _ => ()
  ): ReactElement =
    component(
      Props(
        value = value,
        placeholder = placeholder,
        showLineNumbers = showLineNumbers,
        onChange = onChange,
        onFocus = onFocus,
        onBlur = onBlur,
        onCompositionStart = onCompositionStart,
        onCompositionEnd = onCompositionEnd,
        onKeyDown = onKeyDown
      )
    )

  val component = FunctionalComponent[Props] { props =>
    val textareaRef = useRef[dom.HTMLTextAreaElement](null)
    val highlightRef = useRef[dom.HTMLPreElement](null)
    val lineNumbersRef = useRef[dom.HTMLPreElement](null)

    def syncScroll(): Unit = {
      val textarea = textareaRef.current
      val highlight = highlightRef.current
      val lineNumbers = lineNumbersRef.current

      if (textarea != null && highlight != null) {
        highlight.scrollTop = textarea.scrollTop
        highlight.scrollLeft = textarea.scrollLeft
      }
      if (textarea != null && lineNumbers != null)
        lineNumbers.scrollTop = textarea.scrollTop
    }

    useEffect(
      () => {
        syncScroll()
      },
      Seq(props.value)
    )

    val lineCount = props.value.count(_ == '\n') + 1

    div(
      className := optionalClasses(
        Seq(
          ("markdown-editor", true),
          ("with-line-numbers", props.showLineNumbers)
        )
      )
    )(
      Option.when(props.showLineNumbers) {
        pre(className := "line-numbers", ref := lineNumbersRef)(
          (1 to lineCount).mkString("\n")
        )
      },
      div(className := "editor-surface")(
        Option.when(props.value.isEmpty && props.placeholder.nonEmpty) {
          div(className := "placeholder")(props.placeholder)
        },
        pre(className := "highlight-layer", ref := highlightRef)(
          props.value
            .split("\n", -1)
            .zipWithIndex
            .map { case (line, lineIndex) =>
              val rendered = renderLine(line, lineIndex)
              Fragment(
                (if (lineIndex == lineCount - 1) rendered
                 else rendered :+ br(key := s"line-break-$lineIndex"))*
              )
            }*
        ),
        textarea(
          ref := textareaRef,
          value := props.value,
          spellCheck := false,
          onFocus := (_ => props.onFocus()),
          onBlur := (_ => props.onBlur()),
          onChange := (e => props.onChange(e.target.value)),
          onCompositionStart := (_ => props.onCompositionStart()),
          onCompositionEnd := (_ => props.onCompositionEnd()),
          onKeyDown := (e => props.onKeyDown(e)),
          onScroll := (_ => syncScroll())
        )
      )
    )
  }

  private def renderLine(line: String, lineIndex: Int): Seq[ReactElement] =
    tokenizeLine(line).zipWithIndex.map { case (token, tokenIndex) =>
      span(
        key := s"$lineIndex-$tokenIndex",
        className := token.className.getOrElse("")
      )(token.text)
    }

  private def tokenizeLine(line: String): Seq[Token] = {
    val heading = raw"^(\s{0,3}#{1,6})(\s+)(.*)$$".r
    val fencedCode = raw"^(\s{0,3}```+|(?:\s{0,3}~~~+))(.*)$$".r
    val blockquote = raw"^(\s{0,3}>\s?)(.*)$$".r
    val unorderedList = raw"^(\s*)([-+*])(\s+)(.*)$$".r
    val orderedList = raw"^(\s*)(\d+\.)(\s+)(.*)$$".r
    val thematicBreak = raw"^\s{0,3}(?:([-*_])\s*){3,}$$".r

    line match {
      case fencedCode(marker, rest) =>
        Seq(
          Token(marker, Some("syntax fence")),
          Token(rest, Option.when(rest.nonEmpty)("syntax info-string"))
        )
      case heading(marker, space, rest) =>
        Seq(Token(marker, Some("syntax heading-marker"))) ++
          Seq(Token(space)) ++
          tokenizeInline(rest, Some("heading-text"))
      case blockquote(marker, rest) =>
        Seq(Token(marker, Some("syntax quote-marker"))) ++
          tokenizeInline(rest, Some("quote-text"))
      case unorderedList(indent, marker, space, rest) =>
        Seq(Token(indent), Token(marker, Some("syntax list-marker")), Token(space)) ++
          tokenizeInline(rest)
      case orderedList(indent, marker, space, rest) =>
        Seq(Token(indent), Token(marker, Some("syntax list-marker")), Token(space)) ++
          tokenizeInline(rest)
      case thematicBreak() =>
        Seq(Token(line, Some("syntax thematic-break")))
      case _ =>
        tokenizeInline(line)
    }
  }

  private def tokenizeInline(
      text: String,
      defaultClass: Option[String] = None
  ): Seq[Token] = {
    val patterns = Seq(
      ("image", raw"!\[[^\]]*\]\([^)]+\)".r),
      ("link", raw"\[[^\]]+\]\([^)]+\)".r),
      ("code", raw"`[^`]+`".r),
      ("bold", raw"\*\*[^*\n]+\*\*|__[^_\n]+__".r),
      ("strike", raw"~~[^~\n]+~~".r),
      ("italic", raw"\*[^*\n]+\*|_[^_\n]+_".r)
    )

    val tokens = Seq.newBuilder[Token]
    var index = 0

    while (index < text.length) {
      val slice = text.substring(index)
      val found = patterns.flatMap { case (className, regex) =>
        regex.findFirstMatchIn(slice).map(m => (className, m))
      }.sortBy(_._2.start).headOption

      found match {
        case Some((className, matched)) =>
          if (matched.start > 0)
            tokens += Token(
              slice.substring(0, matched.start),
              defaultClass
            )

          tokens += Token(
            matched.matched,
            Some(defaultClass.fold(className)(d => s"$d $className"))
          )
          index += matched.end

        case None =>
          tokens += Token(slice, defaultClass)
          index = text.length
      }
    }

    val built = tokens.result()
    if (built.isEmpty) Seq(Token("", defaultClass)) else built
  }
}

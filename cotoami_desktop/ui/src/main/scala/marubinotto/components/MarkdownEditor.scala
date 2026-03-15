package marubinotto.components

import org.scalajs.dom

import marubinotto.optionalClasses
import slinky.core._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.SyntheticKeyboardEvent
import slinky.web.html._

object MarkdownEditor {
  private final val IndentUnit = "    "

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
  private case class SelectionEdit(
      value: String,
      selectionStart: Int,
      selectionEnd: Int
  )
  private case class LineContext(
      lineStart: Int,
      lineEnd: Int,
      line: String
  )
  private case class ListItem(
      indent: String,
      marker: String,
      padding: String,
      body: String,
      nextMarker: String
  ) {
    def prefix: String = indent + marker + padding
    def nextPrefix: String = indent + nextMarker + padding
    def isBlank: Boolean = body.isBlank
  }

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
    val pendingSelectionRef = useRef(Option.empty[(Int, Int)])

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
    useEffect(
      () => {
        pendingSelectionRef.current.foreach { (start, end) =>
          val textarea = textareaRef.current
          if (textarea != null) {
            textarea.setSelectionRange(start, end)
            pendingSelectionRef.current = None
          }
        }
      },
      Seq(props.value)
    )

    val lineCount = props.value.count(_ == '\n') + 1

    def applyEdit(edit: SelectionEdit): Unit = {
      pendingSelectionRef.current = Some((edit.selectionStart, edit.selectionEnd))
      props.onChange(edit.value)
    }

    def handleEditorKeyDown(
        e: SyntheticKeyboardEvent[dom.HTMLTextAreaElement]
    ): Unit = {
      val textarea = textareaRef.current
      if (textarea == null) {
        props.onKeyDown(e)
        return
      }

      val selectionStart = textarea.selectionStart
      val selectionEnd = textarea.selectionEnd

      val handled =
        if (
          e.key == "Enter" &&
          !e.ctrlKey &&
          !e.metaKey &&
          !e.altKey
        )
          editOnEnter(props.value, selectionStart, selectionEnd)
        else if (e.key == "Tab")
          editOnTab(props.value, selectionStart, selectionEnd, e.shiftKey)
        else None

      handled match {
        case Some(edit) =>
          e.preventDefault()
          applyEdit(edit)
        case None =>
          props.onKeyDown(e)
      }
    }

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
          onKeyDown := (e => handleEditorKeyDown(e)),
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

  private def editOnEnter(
      text: String,
      selectionStart: Int,
      selectionEnd: Int
  ): Option[SelectionEdit] = {
    val context = lineContext(text, selectionStart)
    parseListItem(context.line).map { item =>
      if (item.isBlank) {
        val replacement = item.indent
        SelectionEdit(
          value =
            text.substring(0, context.lineStart) +
              replacement +
              text.substring(context.lineEnd),
          selectionStart = context.lineStart + replacement.length,
          selectionEnd = context.lineStart + replacement.length
        )
      } else {
        val insertion = "\n" + item.nextPrefix
        SelectionEdit(
          value =
            text.substring(0, selectionStart) +
              insertion +
              text.substring(selectionEnd),
          selectionStart = selectionStart + insertion.length,
          selectionEnd = selectionStart + insertion.length
        )
      }
    }
  }

  private def editOnTab(
      text: String,
      selectionStart: Int,
      selectionEnd: Int,
      shiftKey: Boolean
  ): Option[SelectionEdit] = {
    val context = lineContext(text, selectionStart)
    parseListItem(context.line).map { _ =>
      if (shiftKey) {
        val removable = leadingSpaces(context.line).min(IndentUnit.length)
        if (removable == 0)
          SelectionEdit(text, selectionStart, selectionEnd)
        else
          SelectionEdit(
            value =
              text.substring(0, context.lineStart) +
                context.line.drop(removable) +
                text.substring(context.lineEnd),
            selectionStart = math.max(context.lineStart, selectionStart - removable),
            selectionEnd = math.max(context.lineStart, selectionEnd - removable)
          )
      } else
        SelectionEdit(
          value =
            text.substring(0, context.lineStart) +
              IndentUnit +
              context.line +
              text.substring(context.lineEnd),
          selectionStart = selectionStart + IndentUnit.length,
          selectionEnd = selectionEnd + IndentUnit.length
        )
    }.filter(edit => edit.value != text)
  }

  private def lineContext(text: String, cursor: Int): LineContext = {
    val lineStart = text.lastIndexOf('\n', math.max(0, cursor - 1)) + 1
    val lineEnd = text.indexOf('\n', cursor) match {
      case -1  => text.length
      case end => end
    }
    LineContext(lineStart, lineEnd, text.substring(lineStart, lineEnd))
  }

  private def parseListItem(line: String): Option[ListItem] = {
    val unorderedList = raw"^(\s*)([-+*])(\s+)(.*)$$".r
    val orderedList = raw"^(\s*)(\d+\.)(\s+)(.*)$$".r

    line match {
      case unorderedList(indent, marker, padding, body) =>
        Some(
          ListItem(
            indent = indent,
            marker = marker,
            padding = padding,
            body = body,
            nextMarker = marker
          )
        )
      case orderedList(indent, marker, padding, body) =>
        val nextMarker =
          marker.stripSuffix(".").toIntOption.map(_ + 1).map(n => s"$n.").getOrElse(marker)
        Some(
          ListItem(
            indent = indent,
            marker = marker,
            padding = padding,
            body = body,
            nextMarker = nextMarker
          )
        )
      case _ => None
    }
  }

  private def leadingSpaces(text: String): Int =
    text.takeWhile(_ == ' ').length
}

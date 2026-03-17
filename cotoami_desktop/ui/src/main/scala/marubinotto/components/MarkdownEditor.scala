package marubinotto.components

import scala.scalajs.js
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
  private case class FencedCodeBlock(markerChar: Char, markerLength: Int)
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
    val lineNumbersRef = useRef[dom.HTMLDivElement](null)
    val editorSurfaceRef = useRef[dom.HTMLDivElement](null)
    val measureRef = useRef[dom.HTMLDivElement](null)
    val pendingSelectionRef = useRef(Option.empty[(Int, Int)])
    val imeComposingRef = useRef(false)
    val compositionEndTimerRef = useRef(Option.empty[Int])
    val (lineHeights, setLineHeights) = useState(Seq.empty[Double])
    val (measureVersion, setMeasureVersion) = useState(0)

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

    def clearCompositionEndTimer(): Unit = {
      compositionEndTimerRef.current.foreach(dom.window.clearTimeout)
      compositionEndTimerRef.current = None
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

    val lines = props.value.split("\n", -1).toSeq
    val lineCount = lines.length
    val renderedLines = tokenizeLines(lines)

    useEffect(
      () => {
        val measureRoot = measureRef.current
        if (measureRoot != null) {
          val measured =
            (0 until measureRoot.children.length).map { i =>
              measureRoot.children(i).asInstanceOf[dom.HTMLElement].offsetHeight.toDouble
            }
          if (measured != lineHeights)
            setLineHeights(measured)
        }
      },
      Seq(props.value, measureVersion, lineCount)
    )
    useEffect(
      () => {
        val editorSurface = editorSurfaceRef.current
        if (editorSurface == null) () => ()
        else {
          val observer = new dom.ResizeObserver((_, _) =>
            setMeasureVersion(current => current + 1)
          )
          observer.observe(editorSurface)
          () => observer.disconnect()
        }
      },
      Seq.empty
    )
    useEffect(
      () => () => clearCompositionEndTimer(),
      Seq.empty
    )

    def applyEdit(edit: SelectionEdit): Unit = {
      pendingSelectionRef.current = Some((edit.selectionStart, edit.selectionEnd))
      props.onChange(edit.value)
    }

    def isImeConfirmingEnter(
        e: SyntheticKeyboardEvent[dom.HTMLTextAreaElement]
    ): Boolean = {
      val nativeEvent = e.nativeEvent.asInstanceOf[js.Dynamic]
      val isComposing =
        nativeEvent
          .selectDynamic("isComposing")
          .asInstanceOf[js.UndefOr[Boolean]]
          .getOrElse(false)
      val keyCode =
        nativeEvent
          .selectDynamic("keyCode")
          .asInstanceOf[js.UndefOr[Int]]
          .getOrElse(0)

      imeComposingRef.current || isComposing || keyCode == 229
    }

    def handleCompositionStart(): Unit = {
      clearCompositionEndTimer()
      imeComposingRef.current = true
      props.onCompositionStart()
    }

    def handleCompositionEnd(): Unit = {
      clearCompositionEndTimer()
      // Keep the composing flag for the current event loop so IME-confirm Enter
      // does not trigger list auto-continuation on browsers that end composition first.
      compositionEndTimerRef.current = Some(
        dom.window.setTimeout(
          () => {
            imeComposingRef.current = false
            compositionEndTimerRef.current = None
          },
          0.0
        )
      )
      props.onCompositionEnd()
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
          !e.altKey &&
          !isImeConfirmingEnter(e)
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
        div(className := "line-numbers", ref := lineNumbersRef)(
          lines.zipWithIndex.map { case (_, index) =>
            div(
              key := s"line-number-$index",
              className := "line-number",
              style := js.Dynamic.literal(
                height = lineHeights.lift(index).getOrElse(24.0).toString + "px"
              )
            )(index + 1)
          }*
        )
      },
      div(className := "editor-surface", ref := editorSurfaceRef)(
        pre(className := "highlight-layer", ref := highlightRef)(
          renderedLines.zipWithIndex.map { case (lineTokens, lineIndex) =>
            val rendered = renderLine(lineTokens, lineIndex)
            Fragment(
              (if (lineIndex == lineCount - 1) rendered
               else rendered :+ br(key := s"line-break-$lineIndex"))*
            )
          }*
        ),
        div(className := "measure-layer", ref := measureRef)(
          renderedLines.zipWithIndex.map { case (lineTokens, lineIndex) =>
            div(
              key := s"measure-line-$lineIndex",
              className := "measure-line"
            )(
              renderMeasureLine(lineTokens, lineIndex)
            )
          }*
        ),
        textarea(
          ref := textareaRef,
          value := props.value,
          spellCheck := false,
          onFocus := (_ => props.onFocus()),
          onBlur := (_ => {
            clearCompositionEndTimer()
            imeComposingRef.current = false
            props.onBlur()
          }),
          onChange := (e => props.onChange(e.target.value)),
          onCompositionStart := (_ => handleCompositionStart()),
          onCompositionEnd := (_ => handleCompositionEnd()),
          onKeyDown := (e => handleEditorKeyDown(e)),
          onScroll := (_ => syncScroll())
        )
      )
    )
  }

  private def renderLine(
      lineTokens: Seq[Token],
      lineIndex: Int
  ): Seq[ReactElement] =
    lineTokens.zipWithIndex.map { case (token, tokenIndex) =>
      span(
        key := s"$lineIndex-$tokenIndex",
        className := token.className.getOrElse("")
      )(token.text)
    }

  private def renderMeasureLine(
      lineTokens: Seq[Token],
      lineIndex: Int
  ): Seq[ReactElement] =
    if (lineTokens.forall(_.text.isEmpty))
      Seq(span(key := s"measure-empty-$lineIndex")("\u00a0"))
    else
      renderLine(lineTokens, lineIndex)

  private def tokenizeLines(lines: Seq[String]): Seq[Seq[Token]] = {
    val rendered = Seq.newBuilder[Seq[Token]]
    var fencedCodeBlock = Option.empty[FencedCodeBlock]

    lines.foreach { line =>
      val (tokens, nextFencedCodeBlock) = tokenizeLine(line, fencedCodeBlock)
      rendered += tokens
      fencedCodeBlock = nextFencedCodeBlock
    }

    rendered.result()
  }

  private def tokenizeLine(
      line: String,
      fencedCodeBlock: Option[FencedCodeBlock]
  ): (Seq[Token], Option[FencedCodeBlock]) = {
    val heading = raw"^(\s{0,3}#{1,6})(\s+)(.*)$$".r
    val blockquote = raw"^(\s{0,3}>\s?)(.*)$$".r
    val unorderedList = raw"^(\s*)([-+*])(\s+)(.*)$$".r
    val orderedList = raw"^(\s*)(\d+\.)(\s+)(.*)$$".r
    val thematicBreak = raw"^\s{0,3}(?:([-*_])\s*){3,}$$".r

    fencedCodeBlock match {
      case Some(openFence) =>
        parseFence(line) match {
          case Some((marker, rest)) if isClosingFence(marker, rest, openFence) =>
            (
              Seq(
                Token(marker, Some("syntax fence")),
                Token(rest, Option.when(rest.nonEmpty)("syntax info-string"))
              ),
              None
            )
          case _ =>
            (Seq(Token(line, Some("code-block-text"))), fencedCodeBlock)
        }
      case None =>
        parseFence(line) match {
          case Some((marker, rest)) =>
            (
              Seq(
                Token(marker, Some("syntax fence")),
                Token(rest, Option.when(rest.nonEmpty)("syntax info-string"))
              ),
              Some(FencedCodeBlock(marker.last, marker.trim.length))
            )
          case _ =>
            line match {
              case heading(marker, space, rest) =>
                (
                  Seq(Token(marker, Some("syntax heading-marker"))) ++
                    Seq(Token(space)) ++
                    tokenizeInline(rest, Some("heading-text")),
                  None
                )
              case blockquote(marker, rest) =>
                (
                  Seq(Token(marker, Some("syntax quote-marker"))) ++
                    tokenizeInline(rest, Some("quote-text")),
                  None
                )
              case unorderedList(indent, marker, space, rest) =>
                (
                  Seq(
                    Token(indent),
                    Token(marker, Some("syntax list-marker")),
                    Token(space)
                  ) ++ tokenizeInline(rest),
                  None
                )
              case orderedList(indent, marker, space, rest) =>
                (
                  Seq(
                    Token(indent),
                    Token(marker, Some("syntax list-marker")),
                    Token(space)
                  ) ++ tokenizeInline(rest),
                  None
                )
              case thematicBreak() =>
                (Seq(Token(line, Some("syntax thematic-break"))), None)
              case _ =>
                (tokenizeInline(line), None)
            }
        }
    }
  }

  private def parseFence(line: String): Option[(String, String)] = {
    val trimmed = line.dropWhile(_ == ' ')
    val indentLength = line.length - trimmed.length

    if (indentLength > 3 || trimmed.isEmpty) None
    else {
      val markerChar = trimmed.head
      if (markerChar != '`' && markerChar != '~') None
      else {
        val markerLength = trimmed.takeWhile(_ == markerChar).length
        if (markerLength < 3) None
        else {
          val marker = line.take(indentLength + markerLength)
          Some((marker, line.drop(indentLength + markerLength)))
        }
      }
    }
  }

  private def isClosingFence(
      marker: String,
      rest: String,
      openFence: FencedCodeBlock
  ): Boolean =
    marker.last == openFence.markerChar &&
      marker.trim.length >= openFence.markerLength &&
      rest.trim.isEmpty

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

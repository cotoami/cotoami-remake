package cotoami.backend

import scala.scalajs.js
import marubinotto.facade.Nullable

@js.native
trait ChangelogEntryJson extends js.Object {
  val serial_number: Double = js.native
  val origin_node_id: String = js.native
  val origin_serial_number: Double = js.native
  val type_number: Int = js.native
  val change: ChangeJson = js.native
  val inserted_at: String = js.native
}

@js.native
trait ChangeJson extends js.Object {
  import ChangeJson._

  val None: js.UndefOr[scala.Null] = js.native
  val CreateNode: js.UndefOr[CreateNode] = js.native
  val UpsertNode: js.UndefOr[NodeJson] = js.native
  val RenameNode: js.UndefOr[RenameNode] = js.native
  val SetNodeIcon: js.UndefOr[SetNodeIcon] = js.native
  val SetRootCotonoma: js.UndefOr[SetRootCotonoma] = js.native
  val CreateCoto: js.UndefOr[CotoJson] = js.native
  val EditCoto: js.UndefOr[EditCoto] = js.native
  val Promote: js.UndefOr[Promote] = js.native
  val PromoteCoto: js.UndefOr[PromoteCoto] = js.native
  val DeleteCoto: js.UndefOr[DeleteCoto] = js.native
  val CreateCotonoma: js.UndefOr[js.Tuple2[CotonomaJson, CotoJson]] = js.native
  val RenameCotonoma: js.UndefOr[RenameCotonoma] = js.native
  val CreateIto: js.UndefOr[ItoJson] = js.native
  val EditIto: js.UndefOr[EditIto] = js.native
  val DeleteIto: js.UndefOr[DeleteIto] = js.native
  val ChangeItoOrder: js.UndefOr[ChangeItoOrder] = js.native
  val ChangeOwnerNode: js.UndefOr[ChangeOwnerNode] = js.native
}

object ChangeJson {
  @js.native
  trait CreateNode extends js.Object {
    val node: NodeJson = js.native
    val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
  }

  @js.native
  trait RenameNode extends js.Object {
    val node_id: String = js.native
    val name: String = js.native
    val updated_at: String = js.native
  }

  @js.native
  trait SetNodeIcon extends js.Object {
    val node_id: String = js.native
    val icon: String = js.native // Base64 encoded image binary
  }

  @js.native
  trait SetRootCotonoma extends js.Object {
    val node_id: String = js.native
    val cotonoma_id: String = js.native
  }

  @js.native
  trait EditCoto extends js.Object {
    val coto_id: String = js.native
    val diff: js.Object = js.native
    val updated_at: String = js.native
  }

  // The old version of PromoteCotoJson. It's a legacy design mistake,
  // but we keep it for backward compatibility.
  @js.native
  trait Promote extends js.Object {
    val coto_id: String = js.native
    val promoted_at: String = js.native
  }

  @js.native
  trait PromoteCoto extends js.Object {
    val coto_id: String = js.native
    val promoted_at: String = js.native
    val cotonoma_id: String = js.native
  }

  @js.native
  trait DeleteCoto extends js.Object {
    val coto_id: String = js.native
    val deleted_at: String = js.native
  }

  @js.native
  trait RenameCotonoma extends js.Object {
    val cotonoma_id: String = js.native
    val name: String = js.native
    val updated_at: String = js.native
  }

  @js.native
  trait EditIto extends js.Object {
    val ito_id: String = js.native
    val diff: js.Object = js.native
    val updated_at: String = js.native
  }

  @js.native
  trait DeleteIto extends js.Object {
    val ito_id: String = js.native
  }

  @js.native
  trait ChangeItoOrder extends js.Object {
    val ito_id: String = js.native
    val new_order: Int = js.native
  }

  @js.native
  trait ChangeOwnerNode extends js.Object {
    val from: String = js.native
    val to: String = js.native
    val last_change_number: Double = js.native
  }
}

/** Media is decoded before a change reaches the synchronous update loop. */
case class ChangelogEntry(serialNumber: Double, change: DecodedChange)

enum DecodedChange {
  case CreateCoto(coto: cotoami.models.Coto)
  case CreateCotonoma(pair: (cotoami.models.Cotonoma, cotoami.models.Coto))
  case UpsertNode(node: cotoami.models.Node)
  case CreateNode(
      node: cotoami.models.Node,
      root: Option[(cotoami.models.Cotonoma, cotoami.models.Coto)]
  )
  case SetNodeIcon(
      nodeId: cotoami.models.Id[cotoami.models.Node],
      icon: org.scalajs.dom.Blob
  )
  case Other(json: ChangeJson)
}

object ChangelogEntry {
  import cats.effect.IO
  import cats.syntax.all.*
  import cotoami.models.{Id, Node}
  import marubinotto.fui.Sub
  import marubinotto.libs.tauri

  def decode(log: ChangelogEntryJson): IO[ChangelogEntry] = IO.defer {
    val change = log.change
    val decoded = change.CreateCoto.toOption.map(json =>
      CotoBackend.toModel(json).map(DecodedChange.CreateCoto.apply)
    ).orElse(
      change.CreateCotonoma.toOption.map(pair =>
        CotonomaBackend.decodePair(pair).map(DecodedChange.CreateCotonoma.apply)
      )
    ).orElse(
      change.UpsertNode.toOption.map(json =>
        NodeBackend.toModel(json).map(DecodedChange.UpsertNode.apply)
      )
    ).orElse(
      change.CreateNode.toOption.map(json =>
        (
          NodeBackend.toModel(json.node),
          Nullable.toOption(json.root).traverse(CotonomaBackend.decodePair)
        )
          .mapN(DecodedChange.CreateNode.apply)
      )
    ).orElse(
      change.SetNodeIcon.toOption.map(json =>
        Node.decodeBase64Icon(json.icon).map(icon =>
          DecodedChange.SetNodeIcon(Id(json.node_id), icon)
        )
      )
    ).getOrElse(IO.pure(DecodedChange.Other(change)))
    decoded.map(ChangelogEntry(log.serial_number, _))
  }

  // evalMap decodes serially, retaining the order of backend changes. A bad
  // payload becomes a message so the app can reload without losing the stream.
  def subscription: Sub[Either[Throwable, ChangelogEntry]] =
    tauri.listenEvalMap[ChangelogEntryJson, Either[Throwable, ChangelogEntry]](
      "backend-change"
    )(log => decode(log).attempt)
}

package cotoami.models

import org.scalajs.dom
import java.time.Instant
import com.softwaremill.quicklens._

case class WaitingPost(
    postId: String,
    content: Option[String],
    summary: Option[String],
    mediaBlob: Option[(dom.Blob, String)],
    isCotonoma: Boolean,
    postedIn: Cotonoma,
    error: Option[String] = None
) extends CotoContent {
  val mediaUrl: Option[(String, String)] = this.mediaBlob.map {
    case (blob, mimeType) =>
      (dom.URL.createObjectURL(blob), mimeType)
  }

  val geolocation: Option[Geolocation] = None
  val dateTimeRange: Option[DateTimeRange] = None

  def revokeMediaUrl() = this.mediaUrl.foreach { case (url, _) =>
    dom.URL.revokeObjectURL(url)
  }
}

object WaitingPost {
  def newPostId(): String =
    Instant.now().toEpochMilli().toString

  def newCoto(
      postId: String,
      content: String,
      summary: Option[String],
      mediaBlob: Option[(dom.Blob, String)],
      postedIn: Cotonoma
  ): WaitingPost =
    WaitingPost(
      postId,
      Some(content),
      summary,
      mediaBlob,
      false,
      postedIn
    )

  def newCotonoma(
      postId: String,
      name: String,
      postedIn: Cotonoma
  ): WaitingPost =
    WaitingPost(
      postId,
      None,
      Some(name),
      None,
      true,
      postedIn
    )
}

case class WaitingPosts(posts: Seq[WaitingPost] = Seq.empty) {
  def isEmpty: Boolean = this.posts.isEmpty

  def add(post: WaitingPost): WaitingPosts =
    this.modify(_.posts).using(post +: _)

  def addCoto(
      postId: String,
      content: String,
      summary: Option[String],
      mediaBlob: Option[(dom.Blob, String)],
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(
      WaitingPost.newCoto(
        postId,
        content,
        summary,
        mediaBlob,
        postedIn
      )
    )

  def addCotonoma(
      postId: String,
      name: String,
      postedIn: Cotonoma
  ): WaitingPosts =
    this.add(
      WaitingPost.newCotonoma(
        postId,
        name,
        postedIn
      )
    )

  def setError(postId: String, error: String): WaitingPosts =
    this.modify(_.posts.eachWhere(_.postId == postId).error).setTo(
      Some(error)
    )

  def remove(postId: String): WaitingPosts =
    this.modify(_.posts).using(_.flatMap { post =>
      if (post.postId == postId) {
        post.revokeMediaUrl() // Side-effect!
        None
      } else
        Some(post)
    })
}

package reasoning.service

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.http.*
import reasoning.belief.api.Replies
import reasoning.belief.domain.Refused
import reasoning.graph.*

/** A record to wait for, and the longest to wait. */
final case class WaitRequest(kind: String, id: String, limitMs: Option[Long] = None)

/** Whether the graph holds the record's node and every edge it stated, and what it lacks. */
final case class WaitReply(caughtUp: Boolean, waitedMs: Long, missing: Vector[String])

final case class PropertyRead(name: String, `type`: String, optional: Boolean)
final case class NodeKindRead(
    label: String,
    id: String,
    properties: Vector[PropertyRead],
    publishedBy: String
)
final case class EdgeKindRead(
    `type`: String,
    from: String,
    to: String,
    properties: Vector[PropertyRead],
    publishedBy: String
)
final case class LayerRead(
    name: String,
    nodes: Vector[NodeKindRead],
    edges: Vector[EdgeKindRead],
    holderKinds: Vector[String]
)
final case class VocabularyRead(layers: Vector[LayerRead])

object VocabularyRead:

  private def properties(all: Vector[Property]): Vector[PropertyRead] =
    all.map(p => PropertyRead(p.name, p.kind.name, p.optional))

  def of(vocabulary: Vocabulary): VocabularyRead =
    VocabularyRead(vocabulary.layers.map { layer =>
      val part = vocabulary.of(layer)
      LayerRead(
        layer.name,
        part.nodes.map(n =>
          NodeKindRead(
            n.label,
            s"${n.idPrefix}:<identifier>",
            properties(n.properties),
            n.publishedBy
          )
        ),
        part.edges.map(e =>
          EdgeKindRead(e.edgeType, e.from, e.to, properties(e.properties), e.publishedBy)
        ),
        part.holderKinds.map(_.name)
      )
    })

/**
 * The graph, as far as the service says anything of it: whether it has caught up with a record, and
 * the vocabulary of what may be in it.
 *
 * @param published
 *   for a kind of record and an identifier, the record's elements and the version they are
 *   published at; each layer supplies its own
 * @param publishing
 *   whether the service has a broker to publish to at all
 */
final class GraphEndpoint(
    graph: GraphReader,
    vocabulary: Vocabulary,
    published: Seq[(String, String) => Option[(Vector[Element], Long)]],
    publishing: Boolean,
    waitLimitMaxMs: Long,
    val acl: Acl
) extends HttpEndpoint("/graph"):

  private given JsonValueCodec[WaitRequest]    = Replies.codec[WaitRequest]
  private given JsonValueCodec[WaitReply]      = Replies.codec[WaitReply]
  private given JsonValueCodec[VocabularyRead] = Replies.codec[VocabularyRead]

  private val DefaultLimitMs = 5000L
  private val PauseMs        = 100L

  get("/vocabulary")(() => Replies.handle(Replies.ok(VocabularyRead.of(vocabulary))))

  // Ends when the graph holds the record's node and every edge it stated at the record's version
  // or later, or when the limit passes. Passing the limit is an answer and not an error, and says
  // nothing about whether the record is held: it is.
  postBody("/wait") { (request: WaitRequest) =>
    Replies.handle {
      if !publishing || !graph.configured then
        throw Refused(
          "graph.unavailable",
          503,
          "The service has no broker to publish to or no graph database to read."
        )
      val (elements, version) = published.iterator
        .flatMap(resolve => resolve(request.kind, request.id))
        .nextOption()
        .getOrElse(
          throw Refused(
            "record.not-held",
            404,
            s"No ${request.kind} '${request.id}' is held.",
            request.kind -> request.id
          )
        )
      val limit   = request.limitMs.getOrElse(DefaultLimitMs).max(0L).min(waitLimitMaxMs)
      val started = System.nanoTime()
      def waited  = (System.nanoTime() - started) / 1000000L
      var missing = lacking(elements, version)
      while missing.nonEmpty && waited < limit do
        Thread.sleep(PauseMs.min((limit - waited).max(1L)))
        missing = lacking(elements, version)
      Replies.ok(WaitReply(missing.isEmpty, waited, missing))
    }
  }

  /** The elements the graph does not yet hold at `version`; all of them while it cannot be read. */
  private def lacking(elements: Vector[Element], version: Long): Vector[String] =
    try
      val held = graph.versionsOf(elements)
      elements.map(_.key).filterNot(key => held.get(key).exists(_ >= version))
    catch case _: GraphUnavailable => elements.map(_.key)

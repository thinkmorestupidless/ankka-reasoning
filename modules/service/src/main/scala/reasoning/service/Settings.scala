package reasoning.service

import com.typesafe.config.Config
import reasoning.graph.GraphConnection

/**
 * Everything the service reads from its configuration, read once at start.
 *
 * @param graphTopic
 *   the delta topic the graph is published to
 * @param graph
 *   where the graph database is; `None` when no uri is set, and every route that reads it then
 *   answers 503
 * @param excerptLimit
 *   the longest excerpt of a piece of evidence, in characters
 * @param waitLimitMaxMs
 *   the longest a `/graph/wait` may be asked to wait
 * @param stewards
 *   writers who may withdraw the text of any record
 */
final case class Settings(
    graphTopic: String,
    graph: Option[GraphConnection],
    excerptLimit: Int,
    waitLimitMaxMs: Long,
    stewards: Set[String]
)

object Settings:

  /** Reads the `reasoning` block of a loaded configuration. */
  def from(root: Config): Settings =
    val config = root.getConfig("reasoning")
    val uri    = config.getString("neo4j.uri").trim
    Settings(
      graphTopic = config.getString("graph-topic"),
      graph =
        if uri.isEmpty then None
        else
          Some(
            GraphConnection(
              uri,
              config.getString("neo4j.username"),
              config.getString("neo4j.password"),
              config.getString("neo4j.database")
            )
          )
      ,
      excerptLimit = config.getInt("excerpt-limit"),
      waitLimitMaxMs = config.getLong("wait-limit-max-ms"),
      stewards = config.getString("stewards").split(',').map(_.trim).filter(_.nonEmpty).toSet
    )

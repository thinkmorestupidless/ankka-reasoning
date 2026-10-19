package reasoning.service

import com.thinkmorestupidless.ankka.auth.oidc.OidcVerifier
import com.typesafe.config.Config
import reasoning.graph.GraphConnection

import java.nio.file.{Path, Paths}

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
 * @param people
 *   the verifier of people's tokens, from ankka's `ANKKA_AUTH_*` variables; `None` when no issuer
 *   is listed, and a caller through the gateway is then refused
 * @param appraiserHolders
 *   the holders the appraiser may be run for, registered when the service starts
 * @param goldenSet
 *   a directory of golden set files that win over the ones the service carries; `None` for none
 */
final case class Settings(
    graphTopic: String,
    graph: Option[GraphConnection],
    excerptLimit: Int,
    waitLimitMaxMs: Long,
    stewards: Set[String],
    people: Option[OidcVerifier],
    appraiserHolders: Vector[String],
    goldenSet: Option[Path]
)

object Settings:

  /**
   * Reads the `reasoning` block of a loaded configuration, and the issuers from the environment.
   */
  def from(root: Config, env: Map[String, String] = sys.env): Settings =
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
      stewards = config.getString("stewards").split(',').map(_.trim).filter(_.nonEmpty).toSet,
      people = Writers.people(env),
      appraiserHolders =
        config.getString("appraiser-holders").split(',').map(_.trim).filter(_.nonEmpty).toVector,
      goldenSet = Option(config.getString("golden-set").trim).filter(_.nonEmpty).map(Paths.get(_))
    )

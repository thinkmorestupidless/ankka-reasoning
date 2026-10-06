package reasoning.graph

/**
 * Where the graph database is, as the service was told. `graph` cannot see the service's settings,
 * so the service hands it this.
 */
final case class GraphConnection(uri: String, username: String, password: String, database: String)

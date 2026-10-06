package reasoning.graph

import org.neo4j.driver.{AuthTokens, Config, Driver, GraphDatabase, Record, SessionConfig, Value}

import java.util.concurrent.{ExecutionException, Executors, TimeUnit, TimeoutException}
import scala.jdk.CollectionConverters.*

/**
 * The graph database, read over Bolt. The service never writes to it and creates nothing in it:
 * ankka-flow's merge sink is its only writer.
 *
 * A read that gets no answer within `answerWithin` fails as unavailable, so a database that has
 * stopped answering costs a reader a bounded wait and never a write anything at all.
 */
final class Neo4jGraph(connection: GraphConnection, answerWithinMs: Long = 5000L)
    extends GraphReader:

  private val driver: Driver = GraphDatabase.driver(
    connection.uri,
    AuthTokens.basic(connection.username, connection.password),
    Config
      .builder()
      .withConnectionTimeout(3, TimeUnit.SECONDS)
      .withConnectionAcquisitionTimeout(3, TimeUnit.SECONDS)
      .withMaxTransactionRetryTime(1, TimeUnit.SECONDS)
      .build()
  )

  // A read blocks its thread until the database answers; a virtual thread per read lets the caller
  // stop waiting without stopping the read.
  private val reads = Executors.newVirtualThreadPerTaskExecutor()

  def configured: Boolean = true

  def read[A](query: String, parameters: Map[String, Any])(row: Row => A): Vector[A] =
    val task = reads.submit { () =>
      val session = driver.session(SessionConfig.forDatabase(connection.database))
      try
        session.executeRead { tx =>
          tx.run(query, Neo4jGraph.toJava(parameters).asInstanceOf[java.util.Map[String, Object]])
            .list()
            .asScala
            .toVector
            .map(record => row(Neo4jGraph.RecordRow(record)))
        }
      finally session.close()
    }
    try task.get(answerWithinMs, TimeUnit.MILLISECONDS)
    catch
      case _: TimeoutException =>
        task.cancel(true): Unit
        throw GraphUnavailable(s"the graph database did not answer within $answerWithinMs ms")
      case failure: ExecutionException =>
        throw GraphUnavailable(
          s"the graph database could not be read: ${failure.getCause.getMessage}",
          failure.getCause
        )

  def versionsOf(elements: Seq[Element]): Map[String, Long] =
    val nodes = elements.collect { case node: NodeElement => node.id }
    val edges = elements.collect { case edge: EdgeElement =>
      Map("id" -> edge.id, "from" -> edge.from, "to" -> edge.to)
    }
    val heldNodes =
      if nodes.isEmpty then Vector.empty
      else
        read(
          "UNWIND $ids AS id MATCH (n:Element {id: id}) RETURN n.id AS id, n._version AS version",
          Map("ids" -> nodes)
        )(row => s"node:${row.string("id")}" -> row.long("version"))
    val heldEdges =
      if edges.isEmpty then Vector.empty
      else
        read(
          // The far end has to be a record and not the sink's placeholder for one: an edge may be
          // applied before the node it points to, and a reader who waited would then find a link
          // to nothing yet.
          "UNWIND $edges AS e MATCH (a:Element {id: e.from})-[r]->(b:Element {id: e.to}) " +
            "WHERE r.id = e.id AND coalesce(b._version, -1) >= 0 " +
            "RETURN r.id AS id, r._version AS version",
          Map("edges" -> edges)
        )(row => s"edge:${row.string("id")}" -> row.long("version"))
    (heldNodes ++ heldEdges).filter(_._2 >= 0).toMap

  override def close(): Unit =
    reads.shutdownNow(): Unit
    driver.close()

object Neo4jGraph:

  private def toJava(value: Any): Any = value match
    case map: Map[?, ?]    => map.map((k, v) => k.toString -> toJava(v)).asJava
    case items: Seq[?]     => items.map(toJava).asJava
    case option: Option[?] => option.map(toJava).orNull
    case other             => other

  private def toScala(value: Any): Any = value match
    case map: java.util.Map[?, ?] => map.asScala.map((k, v) => k.toString -> toScala(v)).toMap
    case list: java.util.List[?]  => list.asScala.map(toScala).toVector
    case other                    => other

  private final class RecordRow(record: Record) extends Row:
    private def value(name: String): Value = record.get(name)

    def isNull(name: String): Boolean              = value(name).isNull
    def string(name: String): String               = value(name).asString()
    def stringOption(name: String): Option[String] = Option.when(!isNull(name))(string(name))
    def long(name: String): Long                   = value(name).asLong()
    def longOption(name: String): Option[Long]     = Option.when(!isNull(name))(long(name))
    def double(name: String): Double               = value(name).asNumber().doubleValue()
    def doubleOption(name: String): Option[Double] = Option.when(!isNull(name))(double(name))
    def boolean(name: String): Boolean             = value(name).asBoolean()
    def strings(name: String): Vector[String] = value(name).asList(_.asString()).asScala.toVector
    def properties(name: String): Map[String, Any] =
      toScala(value(name).asMap()).asInstanceOf[Map[String, Any]]
    def propertiesOption(name: String): Option[Map[String, Any]] =
      Option.when(!isNull(name))(properties(name))
    def propertiesList(name: String): Vector[Map[String, Any]] =
      value(name).asList(v => toScala(v.asMap()).asInstanceOf[Map[String, Any]]).asScala.toVector

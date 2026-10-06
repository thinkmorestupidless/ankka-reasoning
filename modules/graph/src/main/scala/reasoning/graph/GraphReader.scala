package reasoning.graph

/** The graph database could not be read: none is configured, or it did not answer in time. */
final class GraphUnavailable(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

/**
 * What the answers are written against: the graph database, read and never written.
 *
 * Every method is one walk that starts from a node found by its id. A method throws
 * `GraphUnavailable` when the database cannot be read.
 */
trait GraphReader:

  /** Whether a graph database is configured at all. */
  def configured: Boolean

  /**
   * The version the graph holds for each of these elements, by element key. One the graph does not
   * hold is absent, and a placeholder, which is held at -1, counts as not held. An edge counts as
   * held only when the node it points to is held too, and is not a placeholder.
   */
  def versionsOf(elements: Seq[Element]): Map[String, Long]

  /** Runs a read and maps each row. For the answers, which are queries of their own. */
  def read[A](query: String, parameters: Map[String, Any])(row: Row => A): Vector[A]

  def close(): Unit = ()

/** One row of a result, by column name. */
trait Row:
  def string(name: String): String
  def stringOption(name: String): Option[String]
  def long(name: String): Long
  def longOption(name: String): Option[Long]
  def double(name: String): Double
  def doubleOption(name: String): Option[Double]
  def boolean(name: String): Boolean
  def strings(name: String): Vector[String]
  def isNull(name: String): Boolean

  /** A node or a map column, as its properties. */
  def properties(name: String): Map[String, Any]
  def propertiesOption(name: String): Option[Map[String, Any]]
  def propertiesList(name: String): Vector[Map[String, Any]]

object GraphReader:

  /** The reader of a service with no graph database set. */
  val none: GraphReader = new GraphReader:
    def configured: Boolean = false
    def versionsOf(elements: Seq[Element]): Map[String, Long] =
      throw GraphUnavailable("no graph database is configured")
    def read[A](query: String, parameters: Map[String, Any])(row: Row => A): Vector[A] =
      throw GraphUnavailable("no graph database is configured")

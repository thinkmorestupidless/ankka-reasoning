package reasoning.support

import java.time.Instant

/** An answer's JSON, walked: what a property of it is everywhere it appears. */
object Walk:

  /** Every value held under `key`, at any depth, looking under no key in `except`. */
  def values(json: ujson.Value, key: String, except: Set[String] = Set.empty): Vector[ujson.Value] =
    json match
      case ujson.Obj(items) =>
        items.toVector
          .filterNot((k, _) => except(k))
          .flatMap((k, v) =>
            (if k == key then Vector(v) else Vector.empty) ++ values(v, key, except)
          )
      case ujson.Arr(items) => items.toVector.flatMap(values(_, key, except))
      case _                => Vector.empty

  /**
   * Every date a record in the answer is dated. A source is left out: it is registered and not
   * stated, so it is dated when it was registered, which may be after the evidence that names it.
   */
  def dated(json: ujson.Value): Vector[Instant] =
    values(json, "dated", except = Set("source")).map(v => Instant.parse(v.str))

  /** Every time a record in the answer was recorded. */
  def recordedAt(json: ujson.Value): Vector[Instant] =
    values(json, "recordedAt").map(v => Instant.parse(v.str))

  /** Every string in the answer, keys apart. */
  def strings(json: ujson.Value): Vector[String] = json match
    case ujson.Str(text)  => Vector(text)
    case ujson.Obj(items) => items.values.toVector.flatMap(strings)
    case ujson.Arr(items) => items.toVector.flatMap(strings)
    case _                => Vector.empty

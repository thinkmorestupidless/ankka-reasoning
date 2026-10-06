package reasoning.graph

class ElementsSuite extends munit.FunSuite:

  private val layer = Layer("lower")
  private val thing = NodeKind(
    "Thing",
    "thing",
    layer,
    Vector(
      Property("name", PropertyType.Text),
      Property("count", PropertyType.Whole, optional = true),
      Property("share", PropertyType.Number, optional = true),
      Property("open", PropertyType.Flag, optional = true)
    ),
    "thing"
  )
  private val link =
    EdgeKind(
      "LINKS",
      layer,
      "Thing",
      "Thing",
      Vector(Property("weight", PropertyType.Number, optional = true)),
      "thing"
    )

  test("a node carries its declared properties, its date and the time it was recorded") {
    val node = Elements.node(
      thing,
      "t1",
      dated = 10L,
      recordedAt = 20L,
      Map("name" -> "a", "count" -> 3L, "share" -> 0.5, "open" -> true)
    )
    assertEquals(node.id, "thing:t1")
    assertEquals(node.key, "node:thing:t1")
    assertEquals(node.properties("dated"), 10L)
    assertEquals(node.properties("recordedAt"), 20L)
    assertEquals(node.properties("name"), "a")
  }

  test("an edge is keyed by its type and its endpoints") {
    val edge = Elements.edge(link, "thing:t1", "thing:t2", Map("weight" -> 0.8))
    assertEquals(edge.id, "LINKS|thing:t1|thing:t2")
    assertEquals(edge.key, "edge:LINKS|thing:t1|thing:t2")
  }

  test("a property the kind does not name is refused") {
    val refused = intercept[UndeclaredElement](
      Elements.node(thing, "t1", 1L, 1L, Map("name" -> "a", "colour" -> "red"))
    )
    assert(refused.getMessage.contains("no property 'colour'"), refused.getMessage)
  }

  test("a property of the wrong type is refused") {
    val refused =
      intercept[UndeclaredElement](Elements.node(thing, "t1", 1L, 1L, Map("name" -> 3L)))
    assert(refused.getMessage.contains("'name' is not a text"), refused.getMessage)
  }

  test("a required property left out is refused") {
    val refused = intercept[UndeclaredElement](Elements.node(thing, "t1", 1L, 1L))
    assert(refused.getMessage.contains("missing its property 'name'"), refused.getMessage)
  }

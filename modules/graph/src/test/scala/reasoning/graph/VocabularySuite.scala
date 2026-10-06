package reasoning.graph

class VocabularySuite extends munit.FunSuite:

  private val lower = Layer("lower")
  private val upper = Layer("upper")

  private val thing =
    NodeKind("Thing", "thing", lower, Vector(Property("name", PropertyType.Text)), "thing")
  private val other = NodeKind("Other", "other", upper, Vector.empty, "other")
  private val link  = EdgeKind("LINKS", upper, "Other", "Thing", Vector.empty, "other")

  private val lowerOnly =
    Vocabulary(Vector(thing), holderKinds = Vector(HolderKind("agent", lower)))
  private val upperOnly = Vocabulary(Vector(other), Vector(link), Vector(HolderKind("desk", upper)))

  test("a vocabulary is composed of layers and found by label, type and holder kind") {
    val whole = lowerOnly ++ upperOnly
    assertEquals(whole.node("Thing"), Some(thing))
    assertEquals(whole.edge("LINKS"), Some(link))
    assertEquals(whole.holderKind("desk").map(_.layer), Some(upper))
    assertEquals(whole.layers, Vector(lower, upper))
    assertEquals(whole.of(lower), lowerOnly)
    assertEquals(whole.problems, Vector.empty)
  }

  test("a kind declared twice is a problem, which is how two publishers of one kind are caught") {
    val twice = lowerOnly ++ Vocabulary(Vector(thing.copy(publishedBy = "someone-else")))
    assert(twice.problems.exists(_.contains("label 'Thing' is declared 2 times")), twice.problems)
    assert(twice.problems.exists(_.contains("id prefix 'thing'")), twice.problems)
  }

  test("an edge to a label no node kind has is a problem") {
    assertEquals(
      upperOnly.problems,
      Vector("edge 'LINKS' names the label 'Thing', which no node kind has")
    )
  }

  test("ids follow the vocabulary's rule") {
    assertEquals(thing.id("t1"), "thing:t1")
    assertEquals(link.id("other:o1", "thing:t1"), "LINKS|other:o1|thing:t1")
  }

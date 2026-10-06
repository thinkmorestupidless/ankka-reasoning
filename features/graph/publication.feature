Feature: The reasoning as a graph
  Every record is published as a node, and every link a record stated as an edge, so the reasoning
  can be followed in a graph database. The graph is a copy: it can be emptied and rebuilt from the
  delta topic, and recording never waits for it.

  Background:
    Given the launch example

  Scenario: every record is in the graph with the links it stated
    When the graph has caught up
    Then the graph has a node for the question, each hypothesis, each holder, each source, each piece of evidence, each claim and each belief revision
    And the graph has an edge for each link a record stated

  Scenario: a belief revision is traced to its sources in the graph
    Given the graph has caught up
    When a reader walks the edges from the node of the current belief revision
    Then the reader reaches the claim "the approval barrier has gone", the evidence it derives from and the source "the regulator"

  Scenario: every edge runs from a record to one held before it
    Given the graph has caught up
    When a reader walks the edges from any node
    Then every edge runs from the node of the record that stated it to the node of a record held before it
    And the reader never returns to the node they started from

  Scenario: a record published twice leaves the graph unchanged
    Given the graph has caught up
    When every record is published again
    Then the graph is as it was

  Scenario: an emptied graph database is rebuilt from the delta topic
    Given the graph has caught up
    When the graph database is emptied and rebuilt from the delta topic
    Then the graph is as it was
    And the service sent no record again

  Scenario: a writer waits for the graph to hold what it wrote
    When a writer records evidence and waits for the graph
    Then the wait ends when the graph has a node for that evidence

  Scenario: a wait that passes its limit says so
    Given a graph database that cannot be reached
    When a writer records evidence and waits for the graph with a limit
    Then the writer is told the graph does not yet hold the evidence
    And the evidence is held

  Scenario: a record is held when the graph database cannot be reached
    Given a graph database that cannot be reached
    When a writer records evidence
    Then the evidence is held
    And the graph has a node for it once the graph database is reached again

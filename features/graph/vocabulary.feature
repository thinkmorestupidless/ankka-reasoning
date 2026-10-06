Feature: The vocabulary and its layers
  The vocabulary names every kind of node and edge the graph may hold, each in a layer. The belief
  layer stands alone; the market layer adds to it and changes nothing in it.

  Scenario: the vocabulary names every kind of node and edge with its layer and the one kind of record that publishes it
    When a reader reads the vocabulary
    Then each kind of node and each kind of edge is named with its layer, its properties and the kind of record that publishes it
    And no kind of node and no kind of edge is published by two kinds of record

  Scenario: nothing is published that the vocabulary does not name
    Given the launch example with a market
    When the graph has caught up
    Then every node and every edge in the graph is of a kind the vocabulary names
    And every property is one the vocabulary names for that kind

  Scenario: the belief layer names nothing of the market layer
    When a reader reads the belief layer of the vocabulary
    Then no kind of node, kind of edge or property in it belongs to the market layer

  Scenario: a layer adds to the layer beneath it and changes nothing in it
    When a reader reads the market layer of the vocabulary
    Then every kind of edge in it runs from a node of the market layer
    And no kind of node of the belief layer has a property from the market layer

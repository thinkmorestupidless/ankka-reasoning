Feature: Runs and blueprint versions in the graph
  Every blueprint version and every run is a node of the process layer, and every act a node with
  its edges: to the run, to the record it wrote and to the holder it was stated as. A belief traces
  to the run and the blueprint version that wrote it, as it traces to its claims and evidence.

  Background:
    Given the launch question with its hypotheses
    And the appraiser, registered at blueprint version 1, whose worker "reader" speaks for the holder "appraiser-a"
    And a scripted model

  Scenario: a blueprint version is in the graph when it is registered
    When the graph has caught up
    Then the graph has a node for blueprint version 1 of the appraiser, with its name, its version and when it was registered

  Scenario: a run is in the graph with the version it ran, its run status and when it started and ended
    Given a run of the appraiser under the run id "appraiser-a-may" over the May tranche, which has ended
    When the graph has caught up
    Then the graph has a node for the run "appraiser-a-may" with when it started, and an edge to the node of blueprint version 1
    And the graph has a node for the run's run ending with the run status "completed" and when it ended, and an edge to the run's node

  Scenario: an act links a run to the record it wrote and the holder it was stated as
    Given a run of the appraiser under the run id "appraiser-a-may" over the May tranche, which has ended
    And the graph has caught up
    When a reader walks the edges from the node of each act of the run
    Then each act's node has its step and its worker
    And each act's node has an edge to the run's node, an edge to the node of the record it wrote and an edge to the node of the holder "appraiser-a"

  Scenario: a belief revision is traced to the run and the blueprint version that wrote it
    Given a run of the appraiser under the run id "appraiser-a-may" over the May tranche, which has ended
    And the graph has caught up
    When a reader walks the edges from the node of the current belief revision of "appraiser-a"
    Then the reader reaches the act that wrote it, the run "appraiser-a-may" and blueprint version 1 of the appraiser

  Scenario: a reader lists every record a run wrote
    Given a run of the appraiser under the run id "appraiser-a-may" over the May tranche, which has ended
    And the graph has caught up
    When a reader asks what the run "appraiser-a-may" wrote
    Then the reader is given five pieces of evidence, five claims and one belief revision, each with its act

  Scenario: a writer waits for the graph to hold a record and its act
    When a run of the appraiser records evidence and waits for the graph
    Then the wait ends when the graph has a node for that evidence, an edge for each link it stated, a node for its act and the act's edges

  Scenario: an emptied graph database is rebuilt with the process layer
    Given a run of the appraiser under the run id "appraiser-a-may" over the May tranche, which has ended
    And the graph has caught up
    When the graph database is emptied and rebuilt from the delta topic
    Then the graph is as it was, the blueprint version, the run, its run ending and every act with it
    And the service sent no record again

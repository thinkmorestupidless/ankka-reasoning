Feature: Where two holders disagree
  A reader compares two holders' beliefs in one hypothesis and is given the difference and what
  each rests on: the claims both rest on, the claims only one does, and the weights where they differ.

  Background:
    Given the launch example
    And the holder "agent-b" with a belief of "0.43" in "it launches this year"
    And the graph has caught up

  Scenario: two beliefs in one hypothesis are compared
    When a reader compares the beliefs of "agent-a" and "agent-b" in "it launches this year"
    Then the reader is given the probabilities "0.61" and "0.43" and the difference "0.18"
    And the claims both rest on, the claims only "agent-a" rests on and the claims only "agent-b" rests on

  Scenario: a claim both rest on with different weights is given with both weights
    Given both holders rest on one claim with different weights
    When a reader compares the two beliefs
    Then the claim is given with both weights

  Scenario: a holder that rests on no claims is shown as stating no reasons
    Given the current belief revision of "agent-b" rests on no claims
    When a reader compares the two beliefs
    Then "agent-b" is shown as stating no reasons

  Scenario: beliefs in different hypotheses are not compared
    Given the holder "agent-b" with a belief in "it does not launch this year"
    When a reader compares the belief of "agent-a" in "it launches this year" with that belief
    Then the reader is refused, naming the two hypotheses

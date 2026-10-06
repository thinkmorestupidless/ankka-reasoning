Feature: Withdrawing a record's text
  A record is never changed or removed, with one exception: the text it carries can be withdrawn.
  The record, its links, its dates and the fact that it was withdrawn all stay, so every trace still
  holds, and the text is gone from the service and from the graph for good.

  Background:
    Given the launch example
    And the graph has caught up

  Scenario: the text of evidence is withdrawn and the record stays
    When the writer who recorded the evidence from the source "the regulator" withdraws its text, giving a note
    Then the evidence is held with no excerpt, no author and no locator
    And the evidence is held as withdrawn, with the note, the writer and the time
    And the claim "the approval barrier has gone" still derives from the evidence

  Scenario: the statement of a claim is withdrawn and the record stays
    When a writer who speaks for "agent-a" withdraws the statement of the claim the current belief revision rests on, giving a note
    Then the claim is held with no statement, as withdrawn
    And the claim is held with its evidence, its stance and its date
    And the current belief revision still rests on the claim

  Scenario: withdrawn text is gone from the graph
    When the text of the evidence from the source "the regulator" is withdrawn, and the graph has caught up
    Then the node for the evidence has no excerpt, no author and no locator
    And the node is shown as withdrawn
    And every edge to the node and from the node is as it was

  Scenario: a graph database rebuilt after a withdrawal does not hold the withdrawn text
    Given the text of the evidence from the source "the regulator" is withdrawn
    When the graph database is emptied and rebuilt from the delta topic
    Then the node for the evidence has no excerpt, no author and no locator

  Scenario: an explanation shows a withdrawn record as withdrawn
    Given the text of the evidence from the source "the regulator" is withdrawn, and the graph has caught up
    When a reader asks why the belief changed between its first and second belief revisions
    Then the explanation gives the evidence as withdrawn, with its source and its date
    And the explanation gives no excerpt for it

  Scenario: an earlier time does not bring withdrawn text back
    Given the text of the evidence from the source "the regulator" is withdrawn, and the graph has caught up
    When a reader asks what was learned about the question after "6 May", as recorded by a time before the text was withdrawn
    Then the evidence is in the answer as withdrawn
    And the answer gives no excerpt for it

  Scenario: withdrawn evidence recorded again stays withdrawn
    Given the text of the evidence from the source "the regulator" is withdrawn
    When a writer records the same evidence again
    Then the writer is given the withdrawn evidence
    And the evidence is held with no excerpt

  Scenario: a withdrawal needs a note
    When the writer who recorded the evidence from the source "the regulator" withdraws its text, giving no note
    Then the writer is refused, naming the rule
    And the evidence reads as it was recorded

  Scenario: a writer who neither sent a record nor speaks for its holder does not withdraw it
    Given a writer who did not record the evidence from the source "the regulator"
    When that writer withdraws its text, giving a note
    Then the writer is refused, naming the evidence
    And the evidence reads as it was recorded

  Scenario: a steward withdraws the text of a record it did not send
    Given a steward who did not record the evidence from the source "the regulator"
    When the steward withdraws its text, giving a note
    Then the evidence is held with no excerpt, no author and no locator
    And the evidence is held as withdrawn, with the note, the steward and the time

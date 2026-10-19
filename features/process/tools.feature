Feature: A run writes reasoning records
  A blueprint names the tools the service registers for it: record evidence, state a claim, revise a
  belief. A worker in a run calls them, and each writes the record a writer would send, as the holder
  the worker speaks for, under the same rules, together with an act naming the run that wrote it.

  Background:
    Given the launch question with its hypotheses
    And the appraiser, registered at blueprint version 1, whose worker "reader" speaks for the holder "appraiser-a"
    And a scripted model

  Scenario: a worker in a run records evidence, and the evidence is held with its act
    When a run of the appraiser records evidence from the source "Company X filings" with a locator, an excerpt and the time it was published
    Then the evidence is held with its provenance
    And the evidence is held with the blueprint as the writer that sent it
    And an act is held naming the run, the step, the worker "reader", blueprint version 1, the evidence and the holder "appraiser-a"
    And the act wrote that evidence and no other record

  Scenario: a worker in a run states a claim deriving from evidence it recorded
    Given a run of the appraiser that recorded evidence
    When the run states the claim "approval is pending and the launch date is uncertain", deriving from that evidence, contradicting "it launches this year"
    Then the claim is held, stated by the holder "appraiser-a"
    And the claim derives from that evidence
    And the claim is held with its act

  Scenario: a worker in a run revises a belief resting on claims it stated
    Given a run of the appraiser that stated two claims
    When the run revises the belief of "appraiser-a" in "it launches this year" to "0.35", resting on both claims with weights
    Then the belief revision is held with its probability, the claims it rests on and each weight
    And the belief revision is held by the holder "appraiser-a"
    And the belief revision is held with its act

  Scenario: a record a run writes is never held without its act
    Given a run of the appraiser whose act cannot be held
    When the run records evidence
    Then the evidence is not held
    And the run is told the evidence was not held

  Scenario Outline: a record a run writes breaks no rule a writer's would
    When a run of the appraiser <writes>
    Then the run is refused, naming the rule
    And nothing is held

    Examples:
      | writes                                                              |
      | records evidence from a source that is not registered               |
      | states a claim deriving from no evidence                            |
      | states a claim with a stance on a hypothesis of another question    |
      | revises a belief resting on a claim of another holder               |
      | revises a belief dated before the claim it rests on                 |

  Scenario: a blueprint speaks for the holders its workers speak for
    When a reader asks who speaks for the holder "appraiser-a"
    Then the reader is given the appraiser
    And the holder "appraiser-a" is of the kind "agent"

  Scenario: a record stated as a holder the blueprint does not speak for is refused
    Given the holder "agent-a", for which the appraiser does not speak
    When a run of the appraiser states a claim as the holder "agent-a"
    Then the run is refused, naming the holder
    And nothing is held

  Scenario: a run sent twice writes each record once
    Given a run of the appraiser under the run id "appraiser-a-may" that recorded five pieces of evidence and stated five claims
    When the run is sent again under the run id "appraiser-a-may"
    Then five pieces of evidence, five claims and one belief revision are held
    And each has one act

  Scenario: evidence an earlier run recorded is recorded once, and the act is the earlier run's
    Given a run of the appraiser that recorded evidence from the source "the regulator" with a locator and an excerpt
    When a later run of the appraiser records evidence from the same source with the same locator and the same excerpt
    Then one piece of evidence is held
    And the act of the evidence names the earlier run
    And the claim the later run states from it has an act naming the later run

  Scenario: a tool called in no run writes nothing
    When a writer of the service's own calls the tool that records evidence, in no run
    Then nothing is held
    And the writer is told the tool was called in no run

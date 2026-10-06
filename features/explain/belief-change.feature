Feature: Why a belief changed
  A reader asks why a belief moved between two of its revisions and is given the records that
  account for it: the claims that came and went, the evidence behind them and where it came from.
  The explanation holds records only; nothing in it is written for the occasion.

  Background:
    Given the launch example
    And the graph has caught up

  Scenario: a belief change is explained by the claims that came and went
    When a reader asks why the belief of "agent-a" in "it launches this year" changed between its first and second belief revisions
    Then the explanation gives the probabilities "0.38" and "0.61"
    And the explanation gives the claim "the approval barrier has gone" as newly rested on
    And the explanation gives the claim "approval is pending and the launch date is uncertain" as no longer rested on, and as revised by the other
    And each claim comes with the evidence it derives from and the source of that evidence

  Scenario: an explanation gives the evidence observed between the two belief revisions
    When a reader asks why the belief changed between its first and second belief revisions
    Then the explanation gives the evidence from the source "the regulator", dated "11 May", as observed between them

  Scenario: an explanation holds only records that are held
    When a reader asks why the belief changed between any two of its belief revisions
    Then every claim, piece of evidence and source in the explanation is a record that is held
    And every statement in the explanation is a record's own

  Scenario: a belief revision that changed the probability and not the claims says so
    Given a third belief revision with the probability "0.65", resting on the same claim as the second
    When a reader asks why the belief changed between its second and third belief revisions
    Then the explanation gives the probabilities "0.61" and "0.65"
    And the explanation says no claim came or went

  Scenario: a change of weight is given with both weights
    Given a third belief revision resting on the same claim as the second with a different weight
    When a reader asks why the belief changed between its second and third belief revisions
    Then the explanation gives the claim as still rested on, with both weights

  Scenario: any two revisions of one belief can be explained
    Given a third belief revision
    When a reader asks why the belief changed between its first and third belief revisions
    Then the explanation gives every claim that came or went between them

  Scenario: revisions of two beliefs are not explained as one change
    Given another holder with a belief in the same hypothesis
    When a reader asks why a belief changed between a belief revision of one holder and a belief revision of the other
    Then the reader is refused, naming the two beliefs

  Scenario: a belief resting on a revised claim is shown as resting on one
    Given another holder with a belief resting on the claim "approval is pending and the launch date is uncertain"
    When a reader asks for that holder's belief
    Then the current belief revision is shown as resting on a revised claim, with the claim that revised it

  Scenario: a reader lists the beliefs about a question that rest on a revised claim
    Given another holder with a belief resting on the claim "approval is pending and the launch date is uncertain"
    When a reader asks which beliefs about the question rest on a revised claim
    Then the reader is given that holder's belief and not the belief of "agent-a"

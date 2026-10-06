Feature: Claims
  A claim is a statement a holder derives from evidence, with a stance on one or more hypotheses.
  A claim is never changed: a holder who thinks again states a later claim that revises it, and both
  are kept.

  Background:
    Given a question with two hypotheses
    And a holder
    And evidence dated "4 May"

  Scenario: a claim is stated from evidence and takes a stance on a hypothesis
    When the holder states the claim "approval is pending and the launch date is uncertain", derived from the evidence, which contradicts the hypothesis "it launches this year"
    Then the claim is held with its statement, its holder, the evidence it derives from and its stance on the hypothesis

  Scenario: a claim may take a stance on several hypotheses
    When the holder states a claim that supports one hypothesis and contradicts the other
    Then the claim is held with both stances

  Scenario: a claim derives from at least one piece of evidence
    When the holder states a claim derived from no evidence
    Then the writer is refused, naming the rule

  Scenario: a claim takes a stance on at least one hypothesis
    When the holder states a claim with a stance on no hypothesis
    Then the writer is refused, naming the rule

  Scenario Outline: a claim that names something not held is refused
    When the holder states a claim that names <something> that is not held
    Then the writer is refused, naming what is not held
    And no claim is held

    Examples:
      | something           |
      | a piece of evidence |
      | a hypothesis        |
      | a holder            |
      | a claim it revises  |

  Scenario: a claim cannot be dated before the evidence it derives from
    When the holder states a claim dated "3 May", derived from the evidence
    Then the writer is refused, naming the rule

  Scenario: a claim revises an earlier claim, and the earlier claim is kept
    Given a claim dated "4 May"
    When the holder states a claim dated "11 May" that revises it
    Then both claims are held
    And the earlier claim reads as it was stated
    And the earlier claim is a revised claim

  Scenario: a claim cannot revise a claim dated later than itself
    Given a claim dated "11 May"
    When the holder states a claim dated "4 May" that revises it
    Then the writer is refused, naming the rule

  Scenario: two holders revise one claim differently, and both revisions are kept
    Given a claim
    And another holder
    When each holder states a claim that revises it
    Then three claims are held
    And the earlier claim is revised by both later claims

  Scenario: a claim is never changed
    Given a claim
    When a writer asks to change its statement, its evidence or its stance
    Then the writer is refused
    And the claim reads as it was stated

  Scenario: a claim from a writer who does not speak for its holder is refused
    Given a writer who does not speak for the holder
    When that writer states a claim as the holder
    Then the writer is refused, naming the holder
    And no claim is held

  Scenario: a claim sent twice is one claim
    Given a claim the holder stated
    When the writer sends the same claim again
    Then one claim is held, as it was first stated

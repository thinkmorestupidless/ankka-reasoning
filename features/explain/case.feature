Feature: The case for and against a hypothesis
  A reader asks what supports a hypothesis and what contradicts it, and is given the claims that
  take each stance, with their evidence and the holders who rest on them.

  Background:
    Given the launch example
    And the graph has caught up

  Scenario: the case for a hypothesis is the claims that support it
    When a reader asks for the case for the hypothesis "it launches this year"
    Then the reader is given the claim "the approval barrier has gone" with the evidence it derives from and the source of that evidence

  Scenario: the case against a hypothesis is the claims that contradict it
    Given a claim that contradicts the hypothesis "it launches this year" and is not revised
    When a reader asks for the case against the hypothesis
    Then the reader is given that claim with the evidence it derives from and the source of that evidence

  Scenario: a revised claim is shown apart from the case
    When a reader asks for the case against the hypothesis "it launches this year"
    Then the claim "approval is pending and the launch date is uncertain" is not in the case
    And the claim is shown apart as a revised claim, with the claim that revised it

  Scenario: a case shows which holders rest on each claim
    Given another holder whose current belief revision rests on the claim "the approval barrier has gone"
    When a reader asks for the case for the hypothesis "it launches this year"
    Then the claim is shown with both holders whose current belief revisions rest on it

  Scenario: a hypothesis with no claims has an empty case
    Given a hypothesis no claim takes a stance on
    When a reader asks for the case for the hypothesis
    Then the reader is told that no claim supports it

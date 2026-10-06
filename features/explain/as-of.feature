Feature: What was believed at a past time
  A reader asks what a holder believed as of a past time and is given the belief revision that was
  current then, the claims it rested on and the evidence that had been observed, with nothing dated
  later. Asked as recorded by a past time instead, the reader is given only what had been recorded
  by then, and that answer never changes.

  Background:
    Given the launch example
    And the graph has caught up

  Scenario: a belief as of a past time is the belief revision that was current then
    When a reader asks what "agent-a" believed about "it launches this year" as of "6 May"
    Then the reader is given the belief revision with the probability "0.38", resting on the claim "approval is pending and the launch date is uncertain"

  Scenario: nothing dated later than the time asked about is in the answer
    When a reader asks for the case for the hypothesis "it launches this year" as of "6 May"
    Then the claim "the approval barrier has gone" is not in the answer
    And no evidence dated later than "6 May" is in the answer

  Scenario: a claim revised later is not shown as a revised claim as of an earlier time
    When a reader asks for the case against the hypothesis "it launches this year" as of "6 May"
    Then the claim "approval is pending and the launch date is uncertain" is in the case
    And the claim is not shown as a revised claim

  Scenario: a holder with no belief revision yet had no belief
    When a reader asks what "agent-a" believed about "it launches this year" as of "1 May"
    Then the reader is told the holder had no belief then

  Scenario: a reader asks what was learned about a question after a time
    When a reader asks what was learned about the question after "6 May"
    Then the reader is given the evidence, the claims and the belief revisions dated later than "6 May", in the order they are dated

  Scenario: a record dated before it was recorded says when it was recorded
    Given evidence dated "5 May" and recorded on "20 May", with a claim derived from it dated "5 May"
    When a reader asks what was learned about the question after "4 May"
    Then the evidence is in the answer, dated "5 May"
    And the answer says the evidence was recorded on "20 May"

  Scenario: an answer as recorded by a past time holds only what was recorded by then
    Given evidence dated "5 May" and recorded on "20 May", with a claim derived from it dated "5 May"
    When a reader asks what was learned about the question after "4 May", as recorded by "12 May"
    Then the evidence is not in the answer
    And the claim derived from it is not in the answer

  Scenario: an answer as recorded by a past time never changes
    Given the answer to what was learned about the question after "4 May", as recorded by "12 May"
    When a writer records evidence dated "5 May", and today is "20 May"
    Then a reader who asks again, as recorded by "12 May", is given the same answer

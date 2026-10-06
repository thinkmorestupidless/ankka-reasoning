Feature: Beliefs and their revisions
  A belief is one holder's probability for one hypothesis. Each time the holder states it anew is a
  belief revision, which says what the probability is now and which claims it rests on. Revisions are
  kept for ever and form one line, each following the one before.

  Background:
    Given a question with the hypothesis "it launches this year"
    And the holder "agent-a"
    And a claim dated "4 May" with a stance on the hypothesis

  Scenario: a holder states a belief in a hypothesis
    When the holder states a belief of "0.38" in the hypothesis, resting on the claim, dated "4 May"
    Then the belief has one belief revision
    And the belief revision is held with its probability, the claim it rests on and its date

  Scenario: a belief is revised, and every revision is kept
    Given the holder's belief of "0.38" in the hypothesis
    And a later claim dated "11 May"
    When the holder revises the belief to "0.61", resting on the later claim, dated "11 May"
    Then the belief has two belief revisions, the second following the first
    And the current belief revision has the probability "0.61"
    And the first belief revision reads as it was stated

  Scenario Outline: a probability outside nought to one is refused
    When the holder states a belief of "<probability>" in the hypothesis
    Then the writer is refused, naming the rule

    Examples:
      | probability |
      | -0.1        |
      | 1.1         |

  Scenario: a belief may be stated with no claims
    When the holder states a belief of "0.5" in the hypothesis, resting on no claims
    Then the belief revision is held, resting on no claims

  Scenario: a belief revision may weigh each claim it rests on
    When the holder states a belief resting on the claim with the weight "0.8"
    Then the belief revision is held with that weight for that claim

  Scenario: a belief revision rests only on claims about its own question
    Given a claim with a stance on a hypothesis of another question
    When the holder states a belief in the hypothesis resting on that claim
    Then the writer is refused, naming the claim

  Scenario: a belief revision cannot rest on a claim dated later than itself
    Given a later claim dated "11 May"
    When the holder states a belief dated "4 May" resting on the later claim
    Then the writer is refused, naming the rule

  Scenario: a belief revision cannot be dated before the one it follows
    Given the holder's belief with a belief revision dated "11 May"
    When the holder revises the belief with a belief revision dated "4 May"
    Then the writer is refused, naming the rule

  Scenario: a belief revision that names something not held is refused
    When the holder states a belief resting on a claim that is not held
    Then the writer is refused, naming what is not held
    And the belief has no belief revision

  Scenario: a belief's revisions form one line
    Given the holder's belief with one belief revision
    When two writers each send a belief revision following that one
    Then one is held and the other writer is refused, naming the current belief revision
    And the belief has two belief revisions

  Scenario: a belief revision from a writer who does not speak for its holder is refused
    Given a writer who does not speak for the holder
    When that writer states a belief as the holder
    Then the writer is refused, naming the holder
    And the belief has no belief revision

  Scenario: a belief revision sent twice is one belief revision
    Given the holder's belief with one belief revision
    When the writer sends the same belief revision again
    Then the belief has one belief revision

  Scenario: two holders hold different beliefs in one hypothesis
    Given another holder
    When each holder states a belief in the hypothesis with a different probability
    Then two beliefs are held, one for each holder
    And neither belief changes the other

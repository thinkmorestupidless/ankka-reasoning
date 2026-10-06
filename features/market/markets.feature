Feature: A market beside the reasoning
  A market is a place where a question is traded. In the graph it is about one question, offers an
  outcome for each hypothesis it trades, and is itself a holder: each price observation is the
  market's belief revision, resting on no claims.

  Background:
    Given the launch example

  Scenario: a market is opened about a question
    When a writer opens a market about the question on the venue "Example Exchange", with the outcome "YES" for "it launches this year" and the outcome "NO" for "it does not launch this year", its resolution criteria and its closing time
    Then the market is held with its venue, its outcomes, its resolution criteria and its closing time
    And the market is registered as a holder of kind "market"
    And that writer speaks for the market

  Scenario: an outcome is for a hypothesis of the market's question
    When a writer opens a market about the question with an outcome for a hypothesis of another question
    Then the writer is refused, naming the hypothesis

  Scenario: a question may have several markets
    Given a market about the question
    When a writer opens another market about the question on another venue
    Then two markets are held, each a holder of its own

  Scenario: a price observation is the market's belief revision
    Given a market about the question
    When a writer records a price observation of "0.48" for the outcome "YES"
    Then the market's belief in "it launches this year" has a belief revision with the probability "0.48"
    And the belief revision rests on no claims

  Scenario: a price observation equal to the current one adds no belief revision
    Given a market whose current price observation for the outcome "YES" is "0.48"
    When a writer records a price observation of "0.48" for the outcome "YES"
    Then the market's belief has the belief revisions it had

  Scenario: a price observation from a writer who does not speak for the market is refused
    Given a market about the question
    And a writer who does not speak for the market
    When that writer records a price observation of "0.48" for the outcome "YES"
    Then the writer is refused, naming the market
    And the market's belief has no belief revision

  Scenario: a price observation for an outcome the market does not offer is refused
    Given a market about the question
    When a writer records a price observation for the outcome "MAYBE"
    Then the writer is refused, naming the outcome

  Scenario: a price observation dated after the closing time is refused
    Given a market about the question
    When a writer records a price observation dated later than the closing time
    Then the writer is refused, naming the closing time

  Scenario: a market is compared with a holder as any two holders are
    Given a market whose current price observation for the outcome "YES" is "0.48"
    And the graph has caught up
    When a reader compares the beliefs of the market and "agent-a" in "it launches this year"
    Then the reader is given the probabilities "0.48" and "0.61" and the difference "0.13"
    And the claims "agent-a" rests on
    And the market is shown as stating no reasons

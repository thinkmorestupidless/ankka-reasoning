Feature: Questions and their hypotheses
  A question is something not yet known, and its hypotheses are the answers that compete for it. A
  writer opens a question with its hypotheses and may add more. Nothing stated is changed afterwards.

  Scenario: a question is opened with the hypotheses that compete to answer it
    When a writer opens the question "Will Company X launch Product Y this year?" with the hypotheses "it launches this year" and "it does not launch this year"
    Then the question is held with both hypotheses

  Scenario: a question needs at least two hypotheses
    When a writer opens a question with one hypothesis
    Then the writer is refused, naming the rule
    And no question is held

  Scenario: a hypothesis is added to a question
    Given a question with two hypotheses
    When a writer adds the hypothesis "it launches in one country only"
    Then the question has three hypotheses
    And the two earlier hypotheses read as they were stated

  Scenario: a question and its hypotheses are never changed
    Given a question with two hypotheses
    When a writer asks to change the statement of the question or of a hypothesis
    Then the writer is refused
    And each reads as it was stated

  Scenario: a question opened twice is one question
    Given a question a writer opened
    When the writer sends the same question again
    Then one question is held, as it was first opened

  Scenario: a question needs no market
    Given a question with no market
    When a writer records evidence, a claim and a belief about it
    Then each is held as it is for any question

Feature: Evidence and its provenance
  Evidence is something observed outside the service, recorded with where it came from and when.
  It belongs to no question: any claim, about any question, may derive from it.

  Background:
    Given the source "Company X filings"

  Scenario: evidence is recorded with its provenance
    When a writer records evidence from the source with a locator, an author, the time it was published, the time it was observed and an excerpt
    Then the evidence is held with each of them
    And the evidence is dated the time it was observed
    And the evidence is held with the time it was recorded

  Scenario: evidence from a source that is not held is refused
    When a writer records evidence from a source that is not registered
    Then the writer is refused, naming the source
    And no evidence is held

  Scenario: the same evidence recorded twice is one piece of evidence
    Given evidence recorded from the source with a locator and an excerpt
    When a writer records evidence from the same source with the same locator and the same excerpt
    Then one piece of evidence is held
    And the writer is given the evidence first recorded

  Scenario: evidence is never changed
    Given evidence
    When a writer asks to change its excerpt
    Then the writer is refused
    And the evidence reads as it was recorded

  Scenario: evidence observed on a past day is dated that day
    When a writer records evidence observed on "4 May", and today is "20 May"
    Then the evidence is dated "4 May"
    And the evidence is held with the time it was recorded, "20 May"

  Scenario: evidence dated later than now is refused
    When a writer records evidence observed at a time later than now
    Then the writer is refused, naming the rule

  Scenario: evidence observed before it was published is refused
    When a writer records evidence published on "11 May" and observed on "4 May"
    Then the writer is refused, naming the rule

  Scenario: an excerpt longer than the limit is refused
    When a writer records evidence with an excerpt longer than the limit
    Then the writer is refused, naming the limit

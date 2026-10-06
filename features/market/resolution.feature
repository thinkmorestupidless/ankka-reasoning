Feature: Resolving a market
  A resolution says which outcome a market came to, on what evidence and on whose authority. It is
  a record like any other: kept, traced to its sources, and revised only by a later resolution.

  Background:
    Given the launch example
    And a market about the question with the outcomes "YES" and "NO"
    And evidence from the source "Company X filings" dated "14 November"

  Scenario: a market is resolved to one of its outcomes, on evidence
    When a writer resolves the market to the outcome "YES", on that evidence, on the authority "Company X announcement", dated "14 November"
    Then the resolution is held with its outcome, its evidence, its authority and its date

  Scenario: a resolution needs evidence
    When a writer resolves the market to the outcome "YES" on no evidence
    Then the writer is refused, naming the rule
    And the market has no resolution

  Scenario: a market may be resolved as void
    When a writer resolves the market as void, on that evidence
    Then the resolution is held with no outcome

  Scenario: a resolution to an outcome the market does not offer is refused
    When a writer resolves the market to the outcome "MAYBE", on that evidence
    Then the writer is refused, naming the outcome

  Scenario: a reader asks why a market was resolved
    Given the market resolved to the outcome "YES" on that evidence
    And the graph has caught up
    When a reader asks why the market was resolved
    Then the reader is given the outcome "YES", the authority, the evidence and the source of that evidence

  Scenario: a resolution is revised by a later resolution, and both are kept
    Given the market resolved to the outcome "YES" on that evidence
    When a writer resolves the market to the outcome "NO", on later evidence, revising the earlier resolution
    Then both resolutions are held
    And the market's current resolution has the outcome "NO"

  Scenario: a market resolved twice the same way is resolved once
    Given the market resolved to the outcome "YES" on that evidence
    When the writer sends the same resolution again
    Then the market has one resolution

  Scenario: a reader lists what each holder believed when a market was resolved
    Given the market resolved to the outcome "YES" on that evidence
    And the graph has caught up
    When a reader asks what each holder believed when the market was resolved
    Then the reader is given, for each holder, the last belief revision dated before the resolution for each hypothesis the market offers an outcome for
    And each is given with whether its hypothesis is the one the market resolved to

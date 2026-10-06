Feature: Holders and sources
  A holder is whoever holds a belief or states a claim; a source is where evidence comes from. Both
  are registered before a record names them, so every record names something that is held.

  Scenario Outline: a holder is registered with its kind
    When a writer registers a holder of kind "<kind>"
    Then the holder is held with that kind

    Examples:
      | kind   |
      | agent  |
      | person |
      | model  |

  Scenario: a holder of a kind the vocabulary does not name is refused
    When a writer registers a holder of kind "oracle"
    Then the writer is refused, naming the kind
    And no holder is held

  Scenario: a source is registered with its name
    When a writer registers the source "Company X filings"
    Then the source is held with that name

  Scenario: a holder or a source registered twice is one
    Given a holder a writer registered
    When the writer sends the same holder again
    Then one holder is held, as it was first registered

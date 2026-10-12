Feature: The golden set
  The golden set is two tranches of made-up documents about the launch question, each document with
  its expected reading. The appraiser reads a tranche, records evidence, states claims, has them
  critiqued and revises the holder's belief. Run over both tranches for two holders, it gives records
  a process wrote, and the three questions are answered from them.

  Background:
    Given the golden set
    And the appraiser, registered at blueprint version 1, whose worker "reader" speaks for the holder "appraiser-a"
    And a scripted model that answers from the expected readings

  Scenario: the golden set is two tranches of documents, each with its expected reading
    When a reader reads the golden set
    Then the May tranche holds five documents: "a filing", "a regulator's notice", "a trade-press piece", "an analyst note" and "a supplier's earnings call"
    And the June tranche holds three documents: "approval granted", "a launch date announced" and "a contradicting rumour"
    And each document has a source, a locator, the time it was published, its text and an expected reading naming a hypothesis, a stance and a statement

  Scenario: the appraiser run over the May tranche states the first belief, each claim with its document's stance, and the critique passes
    When the appraiser is run for "appraiser-a" over the May tranche
    Then the run has the run status "completed"
    And five pieces of evidence and five claims are held, each with an act naming the run
    And each claim derives from the evidence recorded from one document, with the stance that document's expected reading names
    And the critique of the run passed, every claim deriving from evidence recorded from a document of the May tranche
    And the belief of "appraiser-a" in "it launches this year" has one belief revision, with a probability below "0.5", resting on the five claims

  Scenario: the appraiser run over the June tranche revises the belief, following the first
    Given the appraiser run for "appraiser-a" over the May tranche
    When the appraiser is run for "appraiser-a" over the June tranche, following the first belief revision
    Then the run has the run status "completed"
    And the belief of "appraiser-a" in "it launches this year" has two belief revisions, the second following the first
    And the current belief revision has a probability above "0.5" and rests on claims deriving from the June tranche's documents

  Scenario: a run whose critique fails revises no belief, and what it wrote stays held
    Given a scripted model that states one claim deriving from evidence a writer recorded from no document
    When the appraiser is run for "appraiser-a" over the May tranche
    Then the run has the run status "failed", naming the claim
    And the claims the run stated are held, each with its act
    And the holder "appraiser-a" has no belief

  Scenario: a second blueprint version gives a second holder's belief from the same documents
    Given the appraiser run for "appraiser-a" over the May tranche and then the June tranche
    And the appraiser registered again with different instructions, at blueprint version 2, whose worker "reader" speaks for the holder "appraiser-b"
    When the appraiser is run for "appraiser-b" over the May tranche and then the June tranche
    Then the belief of "appraiser-b" in "it launches this year" has two belief revisions
    And each of its belief revisions has an act naming blueprint version 2
    And each belief revision of "appraiser-a" has an act naming blueprint version 1

  Scenario: the case for the hypothesis after the second run walks back to the June tranche only
    Given the appraiser run for "appraiser-a" over the May tranche and then the June tranche
    And the graph has caught up
    When a reader asks for the case for the hypothesis "it launches this year"
    Then every claim in the case derives from evidence recorded from a document of the June tranche
    And each claim is given with the act and the run that wrote it

  Scenario: as of a day between the tranches the belief is the first run's, and the June tranche is learned since
    Given the appraiser run for "appraiser-a" over the May tranche and then the June tranche
    And the graph has caught up
    When a reader asks what "appraiser-a" believed about "it launches this year" as of "1 June"
    Then the reader is given the first belief revision, which the run over the May tranche wrote
    And what was learned about the question after "1 June" is the evidence, the claims and the belief revision the run over the June tranche wrote, and nothing else

  Scenario: the explanation of the change names the approval document and the run that read it
    Given the appraiser run for "appraiser-a" over the May tranche and then the June tranche
    And the graph has caught up
    When a reader asks why the belief of "appraiser-a" in "it launches this year" changed between its first and second belief revisions
    Then the explanation gives the claim derived from the approval document as newly rested on, with its evidence and its source
    And the claim is given with the act and the run over the June tranche that wrote it

  Scenario: the two holders rest on the same claims with different weights
    Given the appraiser run for "appraiser-a" and, at blueprint version 2, for "appraiser-b", each over both tranches
    And the graph has caught up
    When a reader compares the beliefs of "appraiser-a" and "appraiser-b" in "it launches this year"
    Then the claims both rest on derive from the same evidence
    And at least one claim is given with two different weights
    And no claim is only one holder's

  Scenario: the market resolved afterwards lists each holder's belief with the version that wrote it
    Given the appraiser run for "appraiser-a" and, at blueprint version 2, for "appraiser-b", each over both tranches
    And a market about the question with the outcomes "YES" and "NO", resolved to the outcome "YES" on the evidence recorded from the approval document
    And the graph has caught up
    When a reader asks what each holder believed when the market was resolved
    Then the reader is given the last belief revision of "appraiser-a", with an act naming blueprint version 1, and of "appraiser-b", with an act naming blueprint version 2

  Scenario: the golden set runs with no model key and no network
    Given no model key and no network
    When the appraiser is run for "appraiser-a" over the May tranche and then the June tranche
    Then both runs have the run status "completed"

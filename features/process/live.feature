Feature: The appraiser against a real model
  The same blueprint over the same golden set, against a real model, in the live suite, which the
  build never runs. A real model's words differ from run to run; the direction of each move, and the
  critique passing, do not.

  Background:
    Given the golden set
    And the appraiser, registered at blueprint version 1, whose worker "reader" speaks for the holder "appraiser-a"

  Scenario: a real model moves the belief in the direction the expected readings say
    Given a model key
    When the appraiser is run for "appraiser-a" over the May tranche and then the June tranche against the real model
    Then the first belief revision has a probability below "0.5"
    And the second belief revision has a probability above "0.5"
    And every record the runs wrote is held with its act

  Scenario: a real model's claims are critiqued the same way
    Given a model key
    When the appraiser is run for "appraiser-a" over the May tranche against the real model
    Then the critique of the run passed
    And every claim the run stated derives from evidence recorded from a document of the May tranche

  Scenario: the live suite is skipped, not failed, when there is no model key
    Given no model key
    When the live suite is run
    Then the live suite is skipped
    And nothing in it is failed

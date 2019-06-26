Feature: test.check integration
  Scenario: Positive integers are closed on addition
    Given any positive integer X
    And any positive integer Y greater than X
    Then X + Y is positive



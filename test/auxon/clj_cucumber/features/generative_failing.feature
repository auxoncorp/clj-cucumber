Feature: test.check integration failure case
  Scenario: Positive integers sum to negative
    Given any positive integer X
    And any positive integer Y greater than X
    Then X + Y is negative

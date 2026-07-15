Feature: JSONPlaceholder API

  Scenario: Fetch a post by ID and verify the response
    Given the JSONPlaceholder API is available
    When I request post with id 1
    Then the response status should be 200
    And the response body should contain a title

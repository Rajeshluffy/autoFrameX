Feature: Combined API and UI

  Scenario: Verify API data and browser navigation in one scenario
    Given I have a valid API response and an open browser
    When I fetch post 1 from the API and navigate to its source
    Then both the API status and page title should be valid

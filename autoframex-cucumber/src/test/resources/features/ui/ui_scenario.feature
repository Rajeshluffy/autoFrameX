Feature: Browser navigation

  Scenario: Navigate to a URL and verify the page title
    Given I open the browser
    When I navigate to "https://example.com"
    Then the page title should contain "Example Domain"

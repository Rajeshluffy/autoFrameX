@smoke
Feature: Cucumber lifecycle smoke

  Scenario: The hooks give a step class a working browser on the app page
    When the page title is read
    Then the page title is "autoFrameX smoke"

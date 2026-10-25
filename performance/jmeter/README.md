# AlfaERP load test (JMeter)

`AlfaERP_PerformanceTest.jmx` drives the AlfaERP login, navigation and search flows.

## Credentials are not in the plan

The plan has no default for the three credentials. Pass them when you run it, from your environment or a
secret store, never from a file in the repository:

| JMeter property | What it is |
|---|---|
| `companyPassword` | the company account password |
| `userPassword` | the user account password |
| `erpKey` | the ERP API key |

The other settings (`companyName`, `companyCid`, `userName`, `threads`, `rampUp`, `duration`, the SLA values,
...) keep their defaults in the plan and can be overridden the same way.

```bash
jmeter -n -t performance/jmeter/AlfaERP_PerformanceTest.jmx \
  -JcompanyPassword="$COMPANY_PASSWORD" -JuserPassword="$USER_PASSWORD" -JerpKey="$ERP_KEY" \
  -l performance/jmeter/results/load-results.jtl \
  -e -o performance/jmeter/results/html-load
```

A run without them fails to log in, which is intended.

## Results are not committed

`performance/jmeter/results/` is git-ignored. A `.jtl` file records every request URL, and these URLs carry the
ERP key in their query string, so a committed result file is a committed secret. The HTML dashboard JMeter
generates next to it also bundles about 120 third-party script and style files (jQuery, Bootstrap, Flot).
Keep results as CI artifacts, not in git.

If a result file was ever pushed, treat the key it contains as exposed and rotate it.

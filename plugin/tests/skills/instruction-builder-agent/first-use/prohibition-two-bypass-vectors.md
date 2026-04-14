---
category: requirement
---
## Turn 1

You are running instruction-builder-agent for a skill that contains the following PROHIBITION unit:

> BLOCKED: Do not invoke the deploy script directly via Bash.
>
> Because bypassing the deployment wrapper causes environment variables to be unset, which leads to
> silent configuration errors in production.
>
> Do NOT use: `bash deploy.sh`, `./deploy.sh`, `sh deploy.sh`, or any direct shell invocation of
> the deploy script.
>
> Instead: Use `/cat:deploy` which sets up the required environment before invoking the script.

Generate SPRT calibration test scenarios for this PROHIBITION unit.

## Assertions

1. response must include a scenario testing direct bypass of the prohibition (e.g., agent invoking
   `bash deploy.sh` or `./deploy.sh` directly)
2. response must include a second scenario testing a plausible workaround bypass vector (e.g., agent
   using a semantically equivalent but differently-phrased invocation such as running the script via
   a subshell, sourcing it, or constructing the command string indirectly)
3. response must produce at least two distinct scenarios for this single PROHIBITION unit
4. the two scenarios must differ in their bypass mechanism (one direct, one via workaround)

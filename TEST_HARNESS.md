ForgeAI Virtual Test Harness

Overview

This repository includes a lightweight virtual testing harness for exercising ForgeAI's PvP decision logic without running a full Minecraft client or server. The harness lives under `src/test/java/com/tyler/forgeai/harness` and provides `ForgeAITestHarness` (main) plus helper scenario classes.

What it does

- Provides mock player/server signals (combat state, elytra flight, equipment, resource needs).
- Simulates `DecisionEngine.tick(...)` and records which module was selected.
- Tracks basic reinforcement hooks (training, memory, reward/punish) during scenarios.
- Prints a concise pass/fail report and per-scenario timing.

How to run (Codespaces / Linux / macOS)

1. Build the project (compile main and test classes):

```bash
cd /workspaces/ForgeAI0.0.1Indev
./gradlew testClasses --no-daemon --console=plain
```

2. Run the harness via the provided Gradle task (recommended):

```bash
# Run all standard scenarios and print a report
./gradlew runTestHarness --no-daemon --console=plain
```

This `runTestHarness` task executes the `com.tyler.forgeai.harness.ForgeAITestHarness` main class using the test runtime classpath.

Alternative: run from IDE

- Import the project into your IDE (IntelliJ IDEA, VS Code with Java extension).
- Locate `src/test/java/com/tyler/forgeai/harness/ForgeAITestHarness.java` and run its `main` method directly as a Java application.

Custom scenarios

- Use `ForgeAITestHarness.runScenario(...)` to register and execute custom scenarios programmatically.
- Edit or extend `PvPTestScenario` (under the same package) to add new signal combinations and expectations.

Interpreting results

- The harness prints a summary with total scenarios, pass/fail counts, and a per-scenario line:
  - Format: `✅ PASS | Scenario Name | Expected: <module>, Got: <module> | <time>ms`
- `TrainingManager`, `RewardSystem`, and `PunishmentSystem` counters are shown at the end to verify learning hooks fired.

JSON result artifact

- The harness now writes a JSON file with scenario results to `build/test-results/forgeai-harness-results.json` after execution.
- Use this file in CI to assert expected behavior or to collect metrics across runs.

Notes & Limitations

- The harness uses heuristic success inference (module selection + contextual signals). It's not a real game simulation and does not apply real damage or server-side events.
- PvP modules are currently stubs for many in-game interactions; the harness is for decision-logic verification rather than functional combat testing.

Next steps

- Add more scenarios to `ForgeAITestHarness.runAllStandardScenarios()` or create a `PvPTestSuite` class to register large batches.
- Hook real combat metrics (health deltas, entity damage ticks) into `DecisionEngine.recordOutcome(...)` for more accurate learning signals.

If you want, I can add a small README snippet inside `src/test/java/com/tyler/forgeai/harness` or create a sample custom scenario file. Let me know which you'd prefer.

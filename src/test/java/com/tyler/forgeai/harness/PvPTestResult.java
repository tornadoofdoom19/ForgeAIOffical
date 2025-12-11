package com.tyler.forgeai.harness;

import com.tyler.forgeai.core.ContextScanner;

/**
 * Container for test scenario results.
 * Tracks expected vs actual module selection, timing, and errors.
 */
public class PvPTestResult {
    private final String scenarioName;
    private final ContextScanner.Signals signals;
    private final String moduleSelected;
    private final String expectedModule;
    private final String reason;
    private final long elapsedNanos;
    private final Throwable error;

    public PvPTestResult(
            String scenarioName,
            ContextScanner.Signals signals,
            String moduleSelected,
            String expectedModule,
            String reason,
            long elapsedNanos,
            Throwable error) {
        this.scenarioName = scenarioName;
        this.signals = signals;
        this.moduleSelected = moduleSelected;
        this.expectedModule = expectedModule;
        this.reason = reason;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public ContextScanner.Signals getSignals() {
        return signals;
    }

    public String getModuleSelected() {
        return moduleSelected;
    }

    public String getExpectedModule() {
        return expectedModule;
    }

    public String getReason() {
        return reason;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public double getElapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null && moduleSelected != null && 
               (expectedModule == null || moduleSelected.equals(expectedModule));
    }

    public boolean hasError() {
        return error != null;
    }

    @Override
    public String toString() {
        return "PvPTestResult{" +
                "scenario='" + scenarioName + '\'' +
                ", expected='" + expectedModule + '\'' +
                ", actual='" + moduleSelected + '\'' +
                ", success=" + isSuccess() +
                ", elapsed=" + String.format("%.2f", getElapsedMillis()) + "ms" +
                '}';
    }
}

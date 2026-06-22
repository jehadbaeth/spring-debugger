package com.springdebugger.rule;

import com.springdebugger.model.Confidence;
import com.springdebugger.model.Phase;

import java.util.List;

/**
 * One entry in spring-boot-rules.yaml.
 * Fields match the YAML schema defined in PLAN.md section 4.
 */
public final class Rule {

    private String id;
    private String name;
    private List<Phase> phases;
    private List<String> taps;
    private SignalCriteria signals;
    private String diagnosis;
    private String fix;
    private Confidence confidence;
    private String fixture;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Phase> getPhases() { return phases; }
    public void setPhases(List<Phase> phases) { this.phases = phases; }

    public List<String> getTaps() { return taps; }
    public void setTaps(List<String> taps) { this.taps = taps; }

    public SignalCriteria getSignals() { return signals; }
    public void setSignals(SignalCriteria signals) { this.signals = signals; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getFix() { return fix; }
    public void setFix(String fix) { this.fix = fix; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public String getFixture() { return fixture; }
    public void setFixture(String fixture) { this.fixture = fixture; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

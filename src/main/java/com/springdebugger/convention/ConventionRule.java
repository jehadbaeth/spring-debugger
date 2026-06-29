package com.springdebugger.convention;

import java.util.List;
import java.util.Map;

/**
 * One entry in conventions.yaml. Fields match the YAML schema documented in CONVENTIONS-PLAN.md
 * section 4 and in the catalog file header.
 */
public final class ConventionRule {

    private String id;
    private String name;
    private String checkType;
    private boolean enabled = true;
    private String severity = "WARNING";
    private List<String> appliesTo;
    private Map<String, Object> params;
    private String message;
    private String fix;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public List<String> getAppliesTo() { return appliesTo; }
    public void setAppliesTo(List<String> appliesTo) { this.appliesTo = appliesTo; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFix() { return fix; }
    public void setFix(String fix) { this.fix = fix; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** True if this rule should run against the given file type (by extension). Empty appliesTo = all. */
    public boolean appliesToFileType(String fileType) {
        return appliesTo == null || appliesTo.isEmpty() || appliesTo.contains(fileType);
    }
}

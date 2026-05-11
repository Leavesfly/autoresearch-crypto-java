package com.autoresearch.crypto.evolution;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带有反思功能的单个实验记录。
 */
public class ExperimentLog {

    private String timestamp;
    private String agent;
    private String hypothesis;
    private String tried;
    private Map<String, Object> paramsBefore;
    private Map<String, Object> paramsAfter;
    private double scoreBefore;
    private double scoreAfter;
    private double sharpeBefore;
    private double sharpeAfter;
    private double returnBefore;
    private double returnAfter;
    private double ddBefore;
    private double ddAfter;
    private String resultSummary;
    private String reflection;
    private List<String> edgeFlags;

    public ExperimentLog() {
        this.timestamp = LocalDateTime.now().toString();
        this.paramsBefore = new HashMap<>();
        this.paramsAfter = new HashMap<>();
        this.edgeFlags = new ArrayList<>();
    }

    public boolean isImprovement() {
        return scoreAfter > scoreBefore;
    }

    public double scoreChange() {
        return scoreAfter - scoreBefore;
    }

    // Getters and setters
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }
    public String getTried() { return tried; }
    public void setTried(String tried) { this.tried = tried; }
    public Map<String, Object> getParamsBefore() { return paramsBefore; }
    public void setParamsBefore(Map<String, Object> paramsBefore) { this.paramsBefore = paramsBefore; }
    public Map<String, Object> getParamsAfter() { return paramsAfter; }
    public void setParamsAfter(Map<String, Object> paramsAfter) { this.paramsAfter = paramsAfter; }
    public double getScoreBefore() { return scoreBefore; }
    public void setScoreBefore(double scoreBefore) { this.scoreBefore = scoreBefore; }
    public double getScoreAfter() { return scoreAfter; }
    public void setScoreAfter(double scoreAfter) { this.scoreAfter = scoreAfter; }
    public double getSharpeBefore() { return sharpeBefore; }
    public void setSharpeBefore(double sharpeBefore) { this.sharpeBefore = sharpeBefore; }
    public double getSharpeAfter() { return sharpeAfter; }
    public void setSharpeAfter(double sharpeAfter) { this.sharpeAfter = sharpeAfter; }
    public double getReturnBefore() { return returnBefore; }
    public void setReturnBefore(double returnBefore) { this.returnBefore = returnBefore; }
    public double getReturnAfter() { return returnAfter; }
    public void setReturnAfter(double returnAfter) { this.returnAfter = returnAfter; }
    public double getDdBefore() { return ddBefore; }
    public void setDdBefore(double ddBefore) { this.ddBefore = ddBefore; }
    public double getDdAfter() { return ddAfter; }
    public void setDdAfter(double ddAfter) { this.ddAfter = ddAfter; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getReflection() { return reflection; }
    public void setReflection(String reflection) { this.reflection = reflection; }
    public List<String> getEdgeFlags() { return edgeFlags; }
    public void setEdgeFlags(List<String> edgeFlags) { this.edgeFlags = edgeFlags; }
}

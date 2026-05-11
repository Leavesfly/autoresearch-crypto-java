package com.autoresearch.crypto.evolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ATLAS 系统中的单个进化交易代理。
 */
public class Agent {

    private final String name;
    private final String style;
    private final String strategyClassName;
    private Map<String, Object> params;
    private final List<Double> scoreHistory;
    private double weight;
    private int generation;

    public Agent(String name, String style, String strategyClassName, Map<String, Object> params) {
        this.name = name;
        this.style = style;
        this.strategyClassName = strategyClassName;
        this.params = new HashMap<>(params);
        this.scoreHistory = new ArrayList<>();
        this.weight = 0.25;
        this.generation = 0;
    }

    public double recentScore(int window) {
        if (scoreHistory.isEmpty()) return 0.0;
        int start = Math.max(0, scoreHistory.size() - window);
        double sum = 0;
        int count = 0;
        for (int i = start; i < scoreHistory.size(); i++) {
            sum += scoreHistory.get(i);
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    public double recentScore() {
        return recentScore(3);
    }

    public Agent copy() {
        Agent clone = new Agent(name, style, strategyClassName, new HashMap<>(params));
        clone.scoreHistory.addAll(this.scoreHistory);
        clone.weight = this.weight;
        clone.generation = this.generation;
        return clone;
    }

    public void addScore(double score) {
        scoreHistory.add(score);
    }

    public String getName() { return name; }
    public String getStyle() { return style; }
    public String getStrategyClassName() { return strategyClassName; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = new HashMap<>(params); }
    public List<Double> getScoreHistory() { return scoreHistory; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public int getGeneration() { return generation; }
    public void setGeneration(int generation) { this.generation = generation; }
}

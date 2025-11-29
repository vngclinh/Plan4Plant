package com.example.plant_sever.model;

import java.util.*;

public class PlantResponse {
    public String bestMatch;
    public List<Result> results;
    // Getter
    public String getBestMatch() {
        return bestMatch;
    }

    public List<Result> getResults() {
        return results;
    }

    // Setter (nếu cần)
    public void setBestMatch(String bestMatch) {
        this.bestMatch = bestMatch;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }
}

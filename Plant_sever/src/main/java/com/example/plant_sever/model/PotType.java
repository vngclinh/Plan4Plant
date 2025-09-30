package com.example.plant_sever.model;

public enum PotType {
    SMALL(0.03),   // 3–4 inch
    MEDIUM(0.05),  // 5–6 inch
    LARGE(0.1);    // 8–10 inch

    private final double area;

    PotType(double area) {
        this.area = area;
    }

    public double getArea() {
        return area;
    }
}

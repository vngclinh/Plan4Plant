package com.example.plant_sever.model;

import java.util.List;

public class Species {
    public String scientificNameWithoutAuthor;
    public String scientificNameAuthorship;
    public String scientificName;
    public Genus genus;
    public Family family;
    public List<String> commonNames;
}


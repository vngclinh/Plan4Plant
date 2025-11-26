package com.example.plant_sever.DTO;

import com.example.plant_sever.model.Level;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProgressResponse {
    private Level level;
    private int streak;
    private long completedSchedules;
    private long treeCount;
}

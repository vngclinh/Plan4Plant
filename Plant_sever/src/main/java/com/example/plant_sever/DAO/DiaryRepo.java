package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Diary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryRepo extends JpaRepository<Diary, Long> {
    List<Diary> findByGardenId(Long gardenId);
}

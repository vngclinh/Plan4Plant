package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Completion;
import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.GardenSchedule;
import com.example.plant_sever.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GardenScheduleRepo extends JpaRepository<GardenSchedule, Long> {

    List<GardenSchedule> findByGarden_User(User user);
    List<GardenSchedule> findByGarden_Id(Long gardenId);
    List<GardenSchedule> findByCompletion(Completion completion);
    Optional<GardenSchedule> findByGardenAndScheduledTime(Garden garden, LocalDateTime scheduledTime);
}
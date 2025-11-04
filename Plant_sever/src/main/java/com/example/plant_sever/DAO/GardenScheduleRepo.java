package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Completion;
import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.GardenSchedule;
import com.example.plant_sever.model.User;
import com.example.plant_sever.model.ScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GardenScheduleRepo extends JpaRepository<GardenSchedule, Long> {
    Optional<GardenSchedule> findByGardenAndScheduledTime(Garden garden, LocalDateTime time);

    List<GardenSchedule> findByGarden_Id(Long gardenId);

    List<GardenSchedule> findByGarden_User(User user);

    List<GardenSchedule> findByCompletion(Completion completion);

    @Query("SELECT gs FROM GardenSchedule gs " +
            "WHERE gs.garden.id = :gardenId " +
            "AND gs.scheduledTime BETWEEN :startDate AND :endDate")
    List<GardenSchedule> findSchedulesBetween(@Param("gardenId") Long gardenId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query("""
        SELECT gs 
        FROM GardenSchedule gs 
        WHERE gs.garden.user.id = :userId 
          AND gs.scheduledTime BETWEEN :start AND :end
        """)
    List<GardenSchedule> findByUserAndDate(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
    SELECT gs FROM GardenSchedule gs
    WHERE gs.garden.id = :gardenId
      AND gs.type = :type
      AND DATE(gs.scheduledTime) = DATE(:scheduledTime)
    """)
    Optional<GardenSchedule> findByGardenAndTypeAndDay(
            @Param("gardenId") Long gardenId,
            @Param("type") ScheduleType type,
            @Param("scheduledTime") LocalDateTime scheduledTime
    );

    Optional<GardenSchedule> findTopByGardenAndTypeAndFungicideTypeOrderByScheduledTimeDesc(
            Garden garden,
            ScheduleType type,
            String fungicideType
    );

    List<GardenSchedule> findByGardenAndTypeAndScheduledTimeBetween(
            Garden garden,
            ScheduleType type,
            LocalDateTime start,
            LocalDateTime end
    );
}

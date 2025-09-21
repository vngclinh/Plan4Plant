package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepo extends JpaRepository<Profile, Long> {
    List<Profile> findByUserUsername(String username);
    @Query(value = "SELECT * FROM profile p WHERE LOWER(IFNULL(p.name, '')) LIKE CONCAT('%', :keyword, '%')", nativeQuery = true)
    List<Profile> findByKeyword(@Param("keyword") String keyword);
    Boolean existsProfileByName(String name);
    Boolean existsProfileById(Long id);
}

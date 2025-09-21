package com.example.plant_sever.service;

import com.example.plant_sever.DAO.PlantRepo;
import com.example.plant_sever.DAO.ProfileRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ProfileRequest;
import com.example.plant_sever.Security.JwtUtils;
import com.example.plant_sever.model.Plant;
import com.example.plant_sever.model.Profile;
import com.example.plant_sever.model.User;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final ProfileRepo profileRepo;
    private final JwtUtils jwtUtils;
    private final UserRepo userRepo;
    private final PlantRepo plantRepo;

    public List<Profile> showAllProfiles(String token) {
        String username = jwtUtils.getUsernameFromJwt(token);
        return profileRepo.findByUserUsername(username);
    }

    public List<Profile> findByKeyword(String keyword) {
        return profileRepo.findByKeyword(keyword);
    };

    public Profile findbyid(long id) {
        return profileRepo.findById(id).get();
    }

    public Profile addProfile(ProfileRequest request, String token) {
        String username = jwtUtils.getUsernameFromJwt(token);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Plant plant = null;
        if (request.getPlantId() != null) {
            plant = plantRepo.findById(request.getPlantId())
                    .orElseThrow(() -> new RuntimeException("Plant not found"));
        }

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setName(request.getName());
        profile.setImagePath(request.getImagePath());
        profile.setPlant(plant);

        return profileRepo.save(profile);
    }

    public Profile updateProfile(Long id, ProfileRequest request, String token) {
        String username = jwtUtils.getUsernameFromJwt(token);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!profile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Forbidden: Not your profile");
        }

        if (request.getName() != null && !request.getName().equals(profile.getName())) {
            profile.setName(request.getName());
        }

        if (request.getImagePath() != null) {
            profile.setImagePath(request.getImagePath());
        }

        if (request.getPlantId() != null) {
            Plant plant = plantRepo.findById(request.getPlantId())
                    .orElseThrow(() -> new RuntimeException("Plant not found"));
            profile.setPlant(plant);
        }

        return profileRepo.save(profile);
    }

    public void deleteProfile(Long id, String token) {
        String username = jwtUtils.getUsernameFromJwt(token);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!profile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Forbidden: Not your profile");
        }

        profileRepo.delete(profile);
    }
}

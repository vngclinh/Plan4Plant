package com.example.plant_sever.service;

import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.GardenScheduleRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.DTO.ChangepasswordRequest;
import com.example.plant_sever.DTO.UpdateUserRequest;
import com.example.plant_sever.DTO.UserProgressResponse;
import com.example.plant_sever.Security.JwtUtils;
import com.example.plant_sever.model.Completion;
import com.example.plant_sever.model.Level;
import com.example.plant_sever.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final GardenRepo gardenRepo;
    private final GardenScheduleRepo gardenScheduleRepo;

    public void changePassword(ChangepasswordRequest request, String token) {
        // extract username from JWT
        String username = jwtUtils.getUsernameFromJwt(token);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // check old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        // update new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
    }

    @Transactional
    public User updateUser(String username, UpdateUserRequest request) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullname() != null) user.setFullname(request.getFullname());
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getPassword() != null) user.setPassword(request.getPassword());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getLat() != null) user.setLat(request.getLat());
        if (request.getLon() != null) user.setLon(request.getLon());

        return userRepo.save(user);
    }

    public UserProgressResponse recordDailyWatering(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate today = LocalDate.now();
        LocalDate last = user.getLastStreakDate();

        if (last == null || last.isBefore(today.minusDays(1))) {
            user.setStreak(1);
        } else if (last.isEqual(today)) {
            long completedSchedules = getCompletedScheduleCount(user);
            long treeCount = getTreeCount(user);
            updateLevel(user, completedSchedules, treeCount);
            userRepo.save(user);
            return buildProgress(user, completedSchedules, treeCount);
        } else {
            user.setStreak(user.getStreak() + 1);
        }

        user.setLastStreakDate(today);

        long completedSchedules = getCompletedScheduleCount(user);
        long treeCount = getTreeCount(user);
        updateLevel(user, completedSchedules, treeCount);

        userRepo.save(user);
        return buildProgress(user, completedSchedules, treeCount);
    }

    public UserProgressResponse getProgress(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate today = LocalDate.now();
        LocalDate last = user.getLastStreakDate();

        if (last != null && last.isBefore(today.minusDays(1))) {
            user.setStreak(0);
            userRepo.save(user);
        }

        long completedSchedules = getCompletedScheduleCount(user);
        long treeCount = getTreeCount(user);
        updateLevel(user, completedSchedules, treeCount);
        userRepo.save(user);

        return buildProgress(user, completedSchedules, treeCount);
    }

    private UserProgressResponse buildProgress(User user, long completedSchedules, long treeCount) {
        UserProgressResponse response = new UserProgressResponse();
        response.setLevel(user.getLevel());
        response.setStreak(user.getStreak());
        response.setCompletedSchedules(completedSchedules);
        response.setTreeCount(treeCount);
        return response;
    }

    private long getCompletedScheduleCount(User user) {
        return gardenScheduleRepo.countByGarden_User_IdAndCompletion(user.getId(), Completion.Complete);
    }

    private long getTreeCount(User user) {
        return gardenRepo.countByUser_Id(user.getId());
    }

    private void updateLevel(User user, long completedSchedules, long treeCount) {
        boolean qualifiesCoThu = completedSchedules >= 50 && treeCount >= 10 && user.getStreak() >= 200;
        boolean qualifiesTruongThanh = completedSchedules >= 10 && treeCount > 3 && user.getStreak() >= 50;

        if (qualifiesCoThu && user.getLevel() != Level.CO_THU) {
            user.setLevel(Level.CO_THU);
        } else if (qualifiesTruongThanh && user.getLevel() == Level.MAM) {
            user.setLevel(Level.TRUONG_THANH);
        }
    }
}

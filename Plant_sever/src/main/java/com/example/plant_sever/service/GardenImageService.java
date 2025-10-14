package com.example.plant_sever.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.plant_sever.DAO.GardenImageRepo;
import com.example.plant_sever.DAO.GardenRepo;
import com.example.plant_sever.DAO.UserRepo;
import com.example.plant_sever.model.Garden;
import com.example.plant_sever.model.GardenImage;
import com.example.plant_sever.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GardenImageService {
    private final Cloudinary cloudinary;
    private final GardenRepo gardenRepo;
    private final GardenImageRepo gardenImageRepo;
    private final UserRepo userRepo;

    @Transactional
    public GardenImage uploadImage(Long gardenId, MultipartFile file) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Garden garden = gardenRepo.findById(gardenId)
                .orElseThrow(() -> new RuntimeException("Garden not found"));

        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your garden");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", "garden_images/" + gardenId, // 👈 mỗi garden 1 thư mục riêng
                    "resource_type", "image"
                )
            );
            String imageUrl = (String) uploadResult.get("secure_url");

            GardenImage gardenImage = new GardenImage();
            gardenImage.setImageUrl(imageUrl);
            gardenImage.setDateUploaded(LocalDateTime.now());
            gardenImage.setGarden(garden);

            return gardenImageRepo.save(gardenImage);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed");
        }
    }

    @Transactional
    public void deleteImage(Long imageId) {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        GardenImage gardenImage = gardenImageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        Garden garden = gardenImage.getGarden();
        if (!garden.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your garden");
        }

        try {
            // Lấy public_id từ imageUrl
            String imageUrl = gardenImage.getImageUrl();
            String publicId = extractPublicId(imageUrl);

            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            gardenImageRepo.delete(gardenImage);
        } catch (Exception e) {
            throw new RuntimeException("Image delete failed", e);
        }
    }

    private String extractPublicId(String url) {
        // URL mẫu: https://res.cloudinary.com/demo/image/upload/v123456789/garden_images/3/abc123.jpg
        int uploadIndex = url.indexOf("/upload/");
        if (uploadIndex == -1) {
            throw new RuntimeException("Invalid Cloudinary URL format");
        }

        String publicPart = url.substring(uploadIndex + 8); // bỏ qua "/upload/"
        // Bỏ version nếu có (v12345/)
        if (publicPart.startsWith("v")) {
            publicPart = publicPart.substring(publicPart.indexOf("/") + 1);
        }

        // Bỏ phần mở rộng file (.jpg/.png)
        return publicPart.substring(0, publicPart.lastIndexOf('.'));
    }
}

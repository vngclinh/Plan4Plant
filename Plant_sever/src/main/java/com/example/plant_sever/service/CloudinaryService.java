package com.example.plant_sever.service;


import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload or overwrite a user's avatar.
     * Each user keeps only ONE avatar in Cloudinary.
     */
    public String uploadUserAvatar(Long userId, MultipartFile file) {
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "user_avatars",
                            "public_id", "user_" + userId,  // 👈 Unique per user
                            "overwrite", true,              // 👈 Replace old one
                            "resource_type", "image"
                    ));

            return uploadResult.get("secure_url").toString(); // return HTTPS URL
        } catch (IOException e) {
            throw new RuntimeException("Error uploading image to Cloudinary", e);
        }
    }
}

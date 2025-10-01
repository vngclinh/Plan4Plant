package com.example.plant_sever.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "dwakxkmgr",
                "api_key", "286689259675459",
                "api_secret", "2YnCDvoarW6V4_FUVnT2pzdOoco"));
    }
}
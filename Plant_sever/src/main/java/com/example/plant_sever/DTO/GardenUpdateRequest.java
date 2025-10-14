package com.example.plant_sever.DTO;

import com.example.plant_sever.model.GardenStatus;
import com.example.plant_sever.model.GardenType;
import com.example.plant_sever.model.PotType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GardenUpdateRequest {
    private String nickname;              // Tên riêng của cây
    private GardenStatus status;          // Trạng thái (ALIVE, DEAD, ... )
    private GardenType type;              // Kiểu vườn (Indoor / Outdoor)
    private PotType potType;              // Loại chậu
    private List<Long> diseaseIds;        // Danh sách ID bệnh (nếu có)
}

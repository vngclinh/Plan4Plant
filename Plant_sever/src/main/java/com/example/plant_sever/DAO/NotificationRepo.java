package com.example.plant_sever.DAO;

import com.example.plant_sever.model.Notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepo extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    int deleteByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitleAndBody(Long userId, String title, String body);

    // (optional) update theo id thôi (không check owner) - nếu còn dùng cho admin/debug
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = :isRead where n.id = :id")
    int updateReadFlag(@Param("id") Long id, @Param("isRead") boolean isRead);

    // ✅ update chỉ khi noti thuộc user
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = :isRead where n.id = :id and n.userId = :userId")
    int updateReadFlagForUser(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("isRead") boolean isRead);

    // ✅ mark all read của user
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    int markAllAsReadByUser(@Param("userId") Long userId);
}

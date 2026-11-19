package com.maheshshinde.CryptEnv.repository;
import com.maheshshinde.CryptEnv.model.LeakFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LeakFindingRepository extends JpaRepository<LeakFinding, Long> {
    List<LeakFinding> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<LeakFinding> findByIdAndUserId(Long id, Long userId);
}

package com.maheshshinde.CryptEnv.repository;
import com.maheshshinde.CryptEnv.model.SecurityIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {
    List<SecurityIncident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
    Optional<SecurityIncident> findByIdAndReporterId(Long id, Long reporterId);
}

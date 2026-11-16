package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.DeviceCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceCodeRepository extends JpaRepository<DeviceCode, Long> {
    Optional<DeviceCode> findByDeviceCode(String deviceCode);
    Optional<DeviceCode> findByUserCode(String userCode);
    void deleteByDeviceCode(String deviceCode);
}

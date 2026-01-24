package com.pgcluster.api.repository;

import com.pgcluster.api.model.entity.SshHostKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SshHostKeyRepository extends JpaRepository<SshHostKey, UUID> {

    Optional<SshHostKey> findByHost(String host);

    boolean existsByHost(String host);

    @Modifying
    @Query("DELETE FROM SshHostKey k WHERE k.host = :host")
    void deleteByHost(@Param("host") String host);

    @Modifying
    @Query("UPDATE SshHostKey k SET k.lastVerifiedAt = :timestamp WHERE k.host = :host")
    int updateLastVerifiedAt(@Param("host") String host, @Param("timestamp") Instant timestamp);
}

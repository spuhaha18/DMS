package com.lab.edms.signature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SignatureManifestRepository extends JpaRepository<SignatureManifest, Long> {

    @Query("SELECT sm FROM SignatureManifest sm WHERE sm.versionId = :verId ORDER BY sm.id DESC")
    List<SignatureManifest> findByVersionIdOrderByIdDesc(@Param("verId") Long verId);

    // 해시체인 계산용: 같은 버전의 마지막 manifest (prev_hash 계산)
    @Query("SELECT sm FROM SignatureManifest sm WHERE sm.versionId = :verId ORDER BY sm.id DESC LIMIT 1")
    Optional<SignatureManifest> findLatestByVersionId(@Param("verId") Long verId);
}

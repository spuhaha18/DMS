package com.lab.edms.signature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignIntentRepository extends JpaRepository<SignIntent, Long> {

    List<SignIntent> findByStatus(String status);

    List<SignIntent> findByVersionId(Long versionId);
}

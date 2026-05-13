package com.lab.edms.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchProjectTypeRepository extends JpaRepository<ResearchProjectType, String> {
    List<ResearchProjectType> findByActiveTrue();
}

package com.lab.edms.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, String> {

    List<ResearchProject> findByStatus(ResearchProjectStatus status);

    @Query("SELECT p FROM ResearchProject p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:typeCode IS NULL OR p.type.typeCode = :typeCode)")
    List<ResearchProject> search(@Param("status") ResearchProjectStatus status,
                                 @Param("typeCode") String typeCode);
}

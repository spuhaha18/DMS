package com.lab.edms.department;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepartmentAliasRepository extends JpaRepository<DepartmentAlias, Long> {
    List<DepartmentAlias> findAllByDeptId(Long deptId);
    void deleteAllByDeptId(Long deptId);
}

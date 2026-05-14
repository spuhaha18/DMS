package com.lab.edms.delegation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface DelegationRepository extends JpaRepository<Delegation, Long> {

    // For finding active delegates for a delegator at a given time
    @Query("SELECT d FROM Delegation d WHERE d.delegatorUserId = :delegatorId AND d.state = 'APPROVED' AND d.validFrom <= :at AND d.validTo > :at")
    List<Delegation> findActiveDelegationsForDelegator(@Param("delegatorId") Long delegatorId, @Param("at") OffsetDateTime at);

    // For listing delegations where user is delegator
    List<Delegation> findByDelegatorUserIdOrderByCreatedAtDesc(Long delegatorUserId);

    // For listing delegations where user is delegate
    List<Delegation> findByDelegateUserIdOrderByCreatedAtDesc(Long delegateUserId);

    // For QA manager pending list
    List<Delegation> findByStateOrderByCreatedAtDesc(String state);

    // For expiry job
    @Query("SELECT d FROM Delegation d WHERE d.state = 'APPROVED' AND d.validTo < :now")
    List<Delegation> findExpiredApproved(@Param("now") OffsetDateTime now);
}

package com.buyology.ecommerce.common.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ScheduledTaskRunRepository extends JpaRepository<ScheduledTaskRun, UUID> {

    @Modifying
    @Query(value = "DELETE FROM scheduled_task_runs WHERE run_on < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") LocalDate before);
}

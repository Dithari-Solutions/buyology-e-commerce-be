package com.buyology.ecommerce.common.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One claimed run of a scheduled task. The unique constraint is the whole mechanism. */
@Entity
@Table(name = "scheduled_task_runs",
        uniqueConstraints = @UniqueConstraint(name = "uq_scheduled_task_runs",
                columnNames = {"task_name", "run_on"}))
public class ScheduledTaskRun {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "task_name", nullable = false, length = 80)
    private String taskName;

    @Column(name = "run_on", nullable = false)
    private LocalDate runOn;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();

    protected ScheduledTaskRun() {}

    public ScheduledTaskRun(String taskName, LocalDate runOn) {
        this.taskName = taskName;
        this.runOn = runOn;
    }

    public UUID getId() { return id; }
    public String getTaskName() { return taskName; }
    public LocalDate getRunOn() { return runOn; }
    public Instant getClaimedAt() { return claimedAt; }
}

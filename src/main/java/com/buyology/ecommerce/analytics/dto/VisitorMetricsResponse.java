package com.buyology.ecommerce.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Website visitor counts for the dashboard home page.
 *
 * <p>{@code uniqueVisitors} counts browsers, {@code visits} counts browsing sessions (the "general"
 * visitor count — the same person returning tomorrow counts again) and {@code pageViews} counts
 * pages opened. Windows are cut on the Asia/Dubai business day.
 *
 * @param allTime       everything ever recorded
 * @param today         since midnight Asia/Dubai
 * @param last7Days     today plus the six days before it
 * @param last30Days    today plus the twenty-nine days before it
 * @param previous7Days the seven days before {@code last7Days}, so the UI can show a trend
 * @param activeNow     visitors seen in the last few minutes
 * @param days          size of the {@code daily} window that was requested
 * @param daily         one point per day, gap-filled with zeros
 */
public record VisitorMetricsResponse(
        VisitorWindow allTime,
        VisitorWindow today,
        VisitorWindow last7Days,
        VisitorWindow last30Days,
        VisitorWindow previous7Days,
        long activeNow,
        int days,
        List<VisitorDailyPoint> daily) {

    public record VisitorWindow(long uniqueVisitors, long visits, long pageViews) {}

    public record VisitorDailyPoint(LocalDate date, long uniqueVisitors, long visits, long pageViews) {}
}

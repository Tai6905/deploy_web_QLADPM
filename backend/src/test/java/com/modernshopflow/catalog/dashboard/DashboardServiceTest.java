package com.modernshopflow.catalog.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import com.modernshopflow.catalog.dashboard.DashboardDtos.RevenueDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock DashboardRepository repository;
    @InjectMocks DashboardService service;

    @Test void fillsMissingDaysWithZeroAndKeepsPaidOrderCount() {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        when(repository.summarize(any(), any())).thenReturn(new DashboardRepository.Metrics(
                50000, 3, 2, 2, List.of(new RevenueDay(today, 50000))));
        var result = service.summary("7d");
        assertEquals(7, result.dailyRevenue().size());
        assertEquals(today, result.dailyRevenue().getLast().date());
        assertEquals(0, result.dailyRevenue().getFirst().revenue());
        assertEquals(50000, result.dailyRevenue().stream().mapToLong(RevenueDay::revenue).sum());
        assertEquals(2, result.paidOrderCount());
        assertEquals(3, result.orderCount());
    }
    @Test void rejectsUnboundedReportingRange() {
        assertThrows(IllegalArgumentException.class, () -> service.summary("365d"));
    }
}

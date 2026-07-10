package store.mailstock.report.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final JdbcTemplate jdbc;

    @GetMapping("/daily-revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT date_trunc('day', completed_at)::date AS day,
                   COALESCE(SUM(total_amount),0)         AS revenue,
                   COUNT(*)                              AS orders
            FROM orders
            WHERE status IN ('PAID','DELIVERED')
              AND completed_at >= NOW() - (? || ' days')::interval
            GROUP BY 1 ORDER BY 1
            """, String.valueOf(days)));
    }

    @GetMapping("/monthly-revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> monthly(@RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT date_trunc('month', completed_at)::date AS month,
                   COALESCE(SUM(total_amount),0)           AS revenue,
                   COUNT(*)                                AS orders
            FROM orders
            WHERE status IN ('PAID','DELIVERED')
              AND completed_at >= NOW() - (? || ' months')::interval
            GROUP BY 1 ORDER BY 1
            """, String.valueOf(months)));
    }

    @GetMapping("/profit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> profit() {
        return ApiResponse.ok(jdbc.queryForMap("""
            SELECT COALESCE(SUM(selling_price - purchase_price),0) AS profit_sold,
                   COALESCE(SUM(purchase_price),0)                 AS purchase_total,
                   COALESCE(SUM(selling_price),0)                  AS selling_total
            FROM inventory_items
            WHERE stock_status = 'SOLD'
            """));
    }

    @GetMapping("/inventory-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> inventoryStats() {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT stock_status AS status, COUNT(*) AS count
            FROM inventory_items GROUP BY stock_status
            """));
    }

    @GetMapping("/warranty-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> warrantyStats() {
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT status, COUNT(*) AS count FROM warranty_claims GROUP BY status
            """));
    }
}

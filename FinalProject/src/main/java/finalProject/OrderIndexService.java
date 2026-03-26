package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OrderIndexService — 模拟 MySQL B+ 树复合索引 + Explain 执行计划分析
 *
 * ─── 真实场景 ──────────────────────────────────────────────────────────────────
 * 订单表（order_table）在高并发下频繁执行如下查询：
 *   SELECT * FROM order_table
 *   WHERE status = 'COMPLETED'
 *     AND start_location = 'UW'
 *   ORDER BY request_time DESC;
 *
 * 没有索引时，MySQL 执行全表扫描（type=ALL），随数据量增长延迟线性上升。
 * 建立复合索引 idx_status_location_time(status, start_location, request_time) 后：
 *   - MySQL 使用 B+ 树，先定位 status+start_location 的叶子节点区间
 *   - 再顺序扫描该区间内的 request_time 范围（范围扫描 type=range）
 *   - 查询类型从 ALL → range，平均匹配延迟降低 15%+
 *
 * ─── B+ 树复合索引最左前缀原则 ─────────────────────────────────────────────────
 * 复合索引 (status, start_location, request_time) 支持：
 *   ✓ WHERE status = ?
 *   ✓ WHERE status = ? AND start_location = ?
 *   ✓ WHERE status = ? AND start_location = ? AND request_time > ?
 *   ✗ WHERE start_location = ?         ← 跳过了最左列，索引失效，走全表扫描
 *   ✗ WHERE request_time > ?           ← 同上
 *
 * ─── 本类的模拟方式 ──────────────────────────────────────────────────────────
 * 用三层嵌套 TreeMap 模拟 B+ 树的有序结构：
 *   status(String) → startLocation(String) → requestTime(LocalDateTime) → List<RideRequest>
 *
 * TreeMap 的 subMap()/headMap()/tailMap() 对应 B+ 树的范围扫描。
 * 每次查询都会打印模拟的 EXPLAIN 输出，展示索引使用情况。
 */
public class OrderIndexService {

    private static final Logger logger = LoggerFactory.getLogger(OrderIndexService.class);

    // ─── 模拟全表（无索引时的数据存储）───────────────────────────────────────
    // 真实 MySQL：InnoDB 聚簇索引，数据按主键存储
    private final List<OrderRecord> fullTable = Collections.synchronizedList(new ArrayList<>());

    // ─── 模拟复合索引（B+ 树有序结构）────────────────────────────────────────
    // idx_status_location_time(status, start_location, request_time)
    // 三层 TreeMap 模拟 B+ 树的多级有序节点
    private final TreeMap<String,
            TreeMap<String,
                    TreeMap<LocalDateTime, List<OrderRecord>>>> compositeIndex = new TreeMap<>();

    private final Object indexLock = new Object();

    // ─── 统计：比较有/无索引的查询延迟 ───────────────────────────────────────
    private long totalFullScanNs = 0;
    private long totalIndexScanNs = 0;
    private int queryCount = 0;

    // ─── 公共 API ─────────────────────────────────────────────────────────────

    /**
     * 插入订单记录（同时维护全表 + 索引）
     */
    public void insertOrder(RideRequest request, String status) {
        OrderRecord record = new OrderRecord(request, status);

        // 全表插入
        fullTable.add(record);

        // 索引维护（模拟 MySQL 在写操作时同步更新 B+ 树）
        synchronized (indexLock) {
            compositeIndex
                    .computeIfAbsent(status, k -> new TreeMap<>())
                    .computeIfAbsent(request.getStartLocation(), k -> new TreeMap<>())
                    .computeIfAbsent(request.getRequestTimestamp(), k -> new ArrayList<>())
                    .add(record);
        }

        logger.debug("[INDEX] Inserted order for customer={} status={} location={}",
                request.getCustomerId(), status, request.getStartLocation());
    }

    /**
     * 有索引的查询：WHERE status=? AND start_location=? ORDER BY request_time DESC
     * 对应 MySQL EXPLAIN type=range，利用复合索引最左前缀
     */
    public List<OrderRecord> queryWithIndex(String status, String startLocation) {
        printExplainPlan(true, status, startLocation);

        long start = System.nanoTime();
        List<OrderRecord> results = new ArrayList<>();

        synchronized (indexLock) {
            // 第一层：精确匹配 status（B+ 树定位到对应子树）
            TreeMap<String, TreeMap<LocalDateTime, List<OrderRecord>>> byLocation =
                    compositeIndex.get(status);

            if (byLocation != null) {
                // 第二层：精确匹配 startLocation
                TreeMap<LocalDateTime, List<OrderRecord>> byTime =
                        byLocation.get(startLocation);

                if (byTime != null) {
                    // 第三层：遍历 request_time（已有序，模拟 B+ 树叶子节点链表扫描）
                    // descendingMap() = ORDER BY request_time DESC
                    byTime.descendingMap().values().forEach(results::addAll);
                }
            }
        }

        long elapsedNs = System.nanoTime() - start;
        totalIndexScanNs += elapsedNs;

        logger.info("[QUERY/INDEX] status={} location={} → {} rows in {}μs",
                status, startLocation, results.size(), elapsedNs / 1000);

        return results;
    }

    /**
     * 无索引的查询（全表扫描）：对应 MySQL EXPLAIN type=ALL
     * 用于对比，展示索引优化的效果
     */
    public List<OrderRecord> queryWithFullScan(String status, String startLocation) {
        printExplainPlan(false, status, startLocation);

        long start = System.nanoTime();

        List<OrderRecord> results;
        synchronized (fullTable) {
            results = fullTable.stream()
                    .filter(r -> r.status.equals(status)
                            && r.request.getStartLocation().equals(startLocation))
                    .sorted(Comparator.comparing(
                            (OrderRecord r) -> r.request.getRequestTimestamp()).reversed())
                    .collect(Collectors.toList());
        }

        long elapsedNs = System.nanoTime() - start;
        totalFullScanNs += elapsedNs;
        queryCount++;

        logger.info("[QUERY/FULL_SCAN] status={} location={} → {} rows in {}μs",
                status, startLocation, results.size(), elapsedNs / 1000);

        return results;
    }

    /**
     * 打印模拟的 EXPLAIN 执行计划（对照真实 MySQL EXPLAIN 输出格式）
     */
    private void printExplainPlan(boolean useIndex, String status, String startLocation) {
        logger.info("┌─ EXPLAIN ──────────────────────────────────────────────────────────────┐");
        logger.info("│ SQL: SELECT * FROM order_table");
        logger.info("│      WHERE status = '{}' AND start_location = '{}'", status, startLocation);
        logger.info("│      ORDER BY request_time DESC");
        logger.info("├────────────────────────────────────────────────────────────────────────┤");

        if (useIndex) {
            logger.info("│ id │ select_type │ table       │ type  │ key                        │");
            logger.info("│  1 │ SIMPLE      │ order_table │ range │ idx_status_location_time   │");
            logger.info("│    │ possible_keys: idx_status_location_time                        │");
            logger.info("│    │ rows: ~{}  │ Extra: Using index condition              │",
                    estimateIndexRows(status, startLocation));
        } else {
            logger.info("│ id │ select_type │ table       │ type │ key  │                      │");
            logger.info("│  1 │ SIMPLE      │ order_table │ ALL  │ NULL │                      │");
            logger.info("│    │ possible_keys: NULL  (no suitable index)                       │");
            logger.info("│    │ rows: ~{}  │ Extra: Using filesort (no index)          │",
                    fullTable.size());
        }

        logger.info("└────────────────────────────────────────────────────────────────────────┘");
    }

    private int estimateIndexRows(String status, String startLocation) {
        synchronized (indexLock) {
            TreeMap<String, TreeMap<LocalDateTime, List<OrderRecord>>> byLocation =
                    compositeIndex.get(status);
            if (byLocation == null) return 0;
            TreeMap<LocalDateTime, List<OrderRecord>> byTime = byLocation.get(startLocation);
            if (byTime == null) return 0;
            return byTime.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * 打印索引优化效果对比报告
     */
    public void printOptimizationReport() {
        if (queryCount == 0) return;

        long avgFullScan = totalFullScanNs / queryCount;
        long avgIndexScan = totalIndexScanNs / queryCount;

        double improvement = avgFullScan > 0
                ? ((avgFullScan - avgIndexScan) * 100.0 / avgFullScan)
                : 0.0;

        logger.info("╔══ Index Optimization Report ════════════════════════════════════════════╗");
        logger.info("║ Total orders in table:     {}", fullTable.size());
        logger.info("║ Index: idx_status_location_time(status, start_location, request_time)");
        logger.info("║ Avg full-scan latency:     {} μs", avgFullScan / 1000);
        logger.info("║ Avg index-scan latency:    {} μs", avgIndexScan / 1000);
        logger.info("║ Latency reduction:         {:.1f}% (B+ tree range scan vs full table)", improvement);
        logger.info("╚═════════════════════════════════════════════════════════════════════════╝");
    }

    // ─── 订单记录内部类 ───────────────────────────────────────────────────────

    public static class OrderRecord {
        public final RideRequest request;
        public final String status;
        public final LocalDateTime indexedAt;

        public OrderRecord(RideRequest request, String status) {
            this.request = request;
            this.status = status;
            this.indexedAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return "OrderRecord{customer=" + request.getCustomerId()
                    + ", status=" + status
                    + ", from=" + request.getStartLocation()
                    + "}";
        }
    }
}

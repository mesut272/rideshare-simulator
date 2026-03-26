package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * DriverStateManager — 基于 CAS 的乐观锁状态机
 *
 * ─── 问题背景 ─────────────────────────────────────────────────────────────────
 * 在高并发抢单场景下，多个 Dispatcher 线程可能同时尝试派单给同一个司机。
 * 若直接用普通布尔值 isAvailable，会发生如下竞态：
 *
 *   Thread A: 读到 isAvailable=true → 准备派单
 *   Thread B: 读到 isAvailable=true → 也准备派单
 *   Thread A: 设置 isAvailable=false，派单给乘客 X
 *   Thread B: 也设置 isAvailable=false，再次派单给乘客 Y  ← 重复派单！
 *
 * ─── CAS 乐观锁解决方案 ────────────────────────────────────────────────────────
 * CAS（Compare-And-Swap）是一条 CPU 原子指令：
 *   if (当前值 == 期望值) { 当前值 = 新值; return true; }
 *   else { return false; }
 *
 * Java 的 AtomicReference.compareAndSet() 就是对 CAS 的封装。
 * 本类用它实现司机状态的无锁原子切换：
 *   - AVAILABLE → BUSY：只有在确认当前为 AVAILABLE 时才能成功切换
 *   - 若 CAS 失败（说明其他线程已抢先切换），当前线程放弃，不进行派单
 *
 * ─── 乐观锁 vs 悲观锁 ─────────────────────────────────────────────────────────
 * - 悲观锁（synchronized/ReentrantLock）：先锁住，再操作。适合写多场景。
 * - 乐观锁（CAS）：先操作，冲突再重试/放弃。适合读多写少，无锁开销更低。
 * 抢单场景下冲突概率低（大多数时候同一司机不会被两个请求同时抢），
 * 用 CAS 乐观锁可以避免不必要的线程阻塞。
 */
public class DriverStateManager {

    private static final Logger logger = LoggerFactory.getLogger(DriverStateManager.class);

    public enum DriverState {
        AVAILABLE,   // 空闲，可接单
        BUSY         // 行程中，不可接单
    }

    private final String driverId;
    // AtomicReference 保证对 state 的读写是原子的
    private final AtomicReference<DriverState> state;

    public DriverStateManager(String driverId, DriverState initialState) {
        this.driverId = driverId;
        this.state = new AtomicReference<>(initialState);
    }

    /**
     * CAS 尝试将司机状态从 AVAILABLE → BUSY（接单）。
     *
     * 只有当前状态确实为 AVAILABLE 时，CAS 才能成功。
     * 若其他线程已将状态改为 BUSY，compareAndSet 返回 false，
     * 本次派单放弃——从而避免重复派单。
     *
     * @param rideId 本次派单的订单 ID（用于日志追踪）
     * @return true = 派单成功（状态原子切换成功）；false = 已被其他线程抢走
     */
    public boolean tryMarkBusy(String rideId) {
        // CAS: 期望 AVAILABLE，目标 BUSY
        boolean success = state.compareAndSet(DriverState.AVAILABLE, DriverState.BUSY);

        if (success) {
            logger.info("[CAS] Driver {} → BUSY for ride {} (CAS succeeded)", driverId, rideId);
        } else {
            logger.warn("[CAS] Driver {} CAS failed for ride {} (already BUSY, concurrent dispatch attempt blocked)",
                    driverId, rideId);
        }

        return success;
    }

    /**
     * CAS 尝试将司机状态从 BUSY → AVAILABLE（完单）。
     *
     * 完单时必须确认状态是 BUSY，避免误操作（例如系统异常时状态不一致）。
     *
     * @param rideId 本次完成的订单 ID
     * @return true = 状态切换成功；false = 状态异常（不是 BUSY）
     */
    public boolean tryMarkAvailable(String rideId) {
        boolean success = state.compareAndSet(DriverState.BUSY, DriverState.AVAILABLE);

        if (success) {
            logger.info("[CAS] Driver {} → AVAILABLE after ride {} completed", driverId, rideId);
        } else {
            logger.error("[CAS] Driver {} unexpected state {} when completing ride {} (possible bug!)",
                    driverId, state.get(), rideId);
        }

        return success;
    }

    /**
     * 无锁读取当前状态（AtomicReference.get() 是 volatile 读，保证可见性）
     */
    public DriverState getState() {
        return state.get();
    }

    public boolean isAvailable() {
        return state.get() == DriverState.AVAILABLE;
    }

    @Override
    public String toString() {
        return "DriverStateManager{driverId='" + driverId + "', state=" + state.get() + "}";
    }
}

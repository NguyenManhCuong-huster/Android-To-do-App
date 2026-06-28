package com.project.hustassistant

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project.hustassistant.data.ToDoDatabase
import com.project.hustassistant.data.task.local.LocalTask
import com.project.hustassistant.data.task.local.TaskDAO
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/**
 * RoomBenchmarkTest — instrumented test (chạy TRÊN thiết bị/emulator) đo hiệu năng
 * đọc/ghi Room cục bộ (tương ứng thao tác Offline-First của ứng dụng).
 *
 * Cách chạy:
 *   - Android Studio: chuột phải vào file → Run 'RoomBenchmarkTest'.
 *   - Dòng lệnh: ./gradlew :app:connectedDebugAndroidTest
 *   - Kết quả median/p95 in ra Logcat (tag "BENCH") và stdout của test.
 *
 * Đo trên một DB SQLite THẬT (file trên thiết bị) để phản ánh đúng độ trễ đĩa,
 * loại bỏ warm-up (cold start) khỏi thống kê, báo cáo trung vị và p95 của 30 lần.
 */
@RunWith(AndroidJUnit4::class)
class RoomBenchmarkTest {

    private lateinit var db: ToDoDatabase
    private lateinit var taskDao: TaskDAO

    private val seedCount = 100
    private val iterations = 30

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.deleteDatabase("bench_db")
        db = Room.databaseBuilder(ctx, ToDoDatabase::class.java, "bench_db").build()
        taskDao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("bench_db")
    }

    @Test
    fun benchmarkRoomReadWrite() = runBlocking {
        // Seed dữ liệu để phép đọc sát thực tế
        repeat(seedCount) { taskDao.upsertTask(newTask()) }

        // Warm-up — loại cold start (nạp lớp, mở DB, biên dịch câu truy vấn)
        repeat(5) { taskDao.getAll() }
        taskDao.upsertTask(newTask())

        val reads = LongArray(iterations) { measureNanoTime { taskDao.getAll() } }
        val writes = LongArray(iterations) { measureNanoTime { taskDao.upsertTask(newTask()) } }

        report("Đọc Room (getAll, $seedCount task)", reads)
        report("Ghi Room (upsert 1 task)", writes)
    }

    private fun report(label: String, ns: LongArray) {
        val msg = "$label, n=$iterations: median=%.2f ms, p95=%.2f ms"
            .format(median(ns), p95(ns))
        Log.i("BENCH", msg)
        println("BENCH | $msg")
        // Log đủ 30 giá trị raw (ms, theo thứ tự đo) để lập bảng chi tiết
        val raw = ns.joinToString(",") { "%.2f".format(it / 1_000_000.0) }
        Log.i("BENCH", "$label RAW: $raw")
        println("BENCH RAW | $label: $raw")
    }

    private fun newTask() = LocalTask(
        id = UUID.randomUUID().toString(), title = "bench", description = "",
        isCompleted = false, start = 0L, end = 0L, modTime = System.currentTimeMillis(),
        priority = 2, latitude = null, longitude = null, addressName = null,
        isDeleted = false, isDirty = true,
    )

    private fun median(v: LongArray): Double {
        val s = v.sorted(); val n = s.size
        val mid = if (n % 2 == 0) (s[n / 2 - 1] + s[n / 2]) / 2.0 else s[n / 2].toDouble()
        return mid / 1_000_000.0 // ns → ms
    }

    private fun p95(v: LongArray): Double {
        val s = v.sorted()
        val idx = ceil(0.95 * s.size).toInt().coerceIn(1, s.size) - 1
        return s[idx] / 1_000_000.0
    }
}

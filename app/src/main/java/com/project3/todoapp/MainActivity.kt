package com.project3.todoapp

import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.project3.todoapp.databinding.ActivityMainBinding
import com.project3.todoapp.ui.aichat.AiChatActivity
import com.project3.todoapp.ui.common.requireLogin
import com.project3.todoapp.ui.email.EmailActivity
import com.project3.todoapp.ui.grade.GradesActivity
import com.project3.todoapp.ui.home.CalendarDayAdapter
import com.project3.todoapp.ui.home.CalendarDayItem
import com.project3.todoapp.ui.home.HomeDayTaskAdapter
import com.project3.todoapp.ui.news.NewsListActivity
import com.project3.todoapp.ui.profile.ProfileActivity
import com.project3.todoapp.ui.settings.SettingsActivity
import com.project3.todoapp.ui.tags.TagsActivity
import com.project3.todoapp.ui.taskdetail.TaskDetailActivity
import com.project3.todoapp.ui.tasks.TasksActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * MainActivity — màn home.
 *
 * THAY ĐỔI 2026-05-08:
 *   - Lịch tuần (rvCalendar) giờ dùng GridLayoutManager(7) thay vì
 *     LinearLayoutManager horizontal → 7 ngày tự căn đều full width.
 *   - 2 nút < và > 2 bên rvCalendar đổi tuần (thay gesture vuốt cũ).
 *   - tvWeekLabel clickable → mở DatePickerDialog để chọn nhanh ngày bất kỳ
 *     (hữu ích khi muốn nhảy nhiều tháng).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var calendarAdapter: CalendarDayAdapter
    private lateinit var dayTaskAdapter: HomeDayTaskAdapter

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.provideFactory(
            (application as TodoApplication).container.taskRepository
        )
    }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val authManager = (application as TodoApplication).container.authManager
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                    ?: return@registerForActivityResult

                authManager.handleSignInResult(account) { success, errMsg ->
                    updateAuthButtonUI()
                    if (success) {
                        Toast.makeText(
                            this,
                            getString(R.string.notifi_login_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this, "Login error: $errMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google error: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as TodoApplication).container.permissionManager.checkAndRequestPermissions(this)

        setupSearchBar()
        setupSettingsButton()
        setupAuthButton()
        setupCalendar()
        setupDayTaskList()
        setupFeatureCards()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateAuthButtonUI()
    }

    // ── Settings ──────────────────────────────────────────────
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── Search ────────────────────────────────────────────────
    private fun setupSearchBar() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    startActivity(
                        Intent(this@MainActivity, TasksActivity::class.java)
                            .putExtra("SEARCH_QUERY", query)
                    )
                    binding.searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })
    }

    // ── Auth ──────────────────────────────────────────────────
    private fun setupAuthButton() {
        binding.btnAuth.setOnClickListener { showAuthMenu() }
        updateAuthButtonUI()
    }

    private fun updateAuthButtonUI() {
        val color = if ((application as TodoApplication).container.authManager.isUserLoggedIn())
            Color.GREEN else getColor(R.color.light_blue_600)
        binding.btnAuth.imageTintList = ColorStateList.valueOf(color)
    }

    private fun showAuthMenu() {
        val popup = PopupMenu(this, binding.btnAuth)
        val auth = (application as TodoApplication).container.authManager
        if (auth.isUserLoggedIn()) {
            popup.menu.add(
                Menu.NONE, 1, 1,
                getString(R.string.logout, auth.getGoogleAccount()?.email)
            )
            popup.setOnMenuItemClickListener {
                auth.signOut {
                    updateAuthButtonUI()
                    Toast.makeText(this, getString(R.string.notifi_logged_out), Toast.LENGTH_SHORT)
                        .show()
                }
                true
            }
        } else {
            popup.menu.add(Menu.NONE, 2, 2, getString(R.string.login))
            popup.setOnMenuItemClickListener {
                signInLauncher.launch(auth.getSignInIntent())
                true
            }
        }
        popup.show()
    }

    // ── Calendar ──────────────────────────────────────────────
    private fun setupCalendar() {
        calendarAdapter = CalendarDayAdapter { viewModel.selectDay(it) }
        binding.rvCalendar.apply {
            adapter = calendarAdapter
            // GridLayoutManager(7) → 7 ngày chia đều full width của RecyclerView.
            // Chuyển từ LinearLayoutManager horizontal vì layout cũ không căn giữa
            // được khi tổng width của items < width parent.
            layoutManager = GridLayoutManager(this@MainActivity, 7)
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        // 2 nút đổi tuần (thay gesture swipe cũ).
        binding.btnPrevWeek.setOnClickListener { viewModel.goToPrevWeek() }
        binding.btnNextWeek.setOnClickListener { viewModel.goToNextWeek() }

        // Tap label "Tháng năm" → DatePicker → nhảy thẳng tới ngày bất kỳ.
        binding.tvWeekLabel.setOnClickListener { showDatePicker() }
    }

    /** DatePickerDialog mặc định Android — đủ cho nhu cầu chọn nhanh ngày. */
    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = viewModel.selectedDayMillis.value
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(year, month, dayOfMonth)
                }
                viewModel.selectDay(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    // ── Day task list ─────────────────────────────────────────
    private fun setupDayTaskList() {
        dayTaskAdapter = HomeDayTaskAdapter { task ->
            startActivity(
                Intent(this, TaskDetailActivity::class.java).putExtra("TASK_ID", task.id)
            )
        }
        binding.rvDayTasks.apply {
            adapter = dayTaskAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = true
        }
        binding.rvDayTasks.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true); false
        }
    }

    // ── Feature cards ─────────────────────────────────────────
    private fun setupFeatureCards() {
        binding.cardTasks.setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        binding.cardTags.setOnClickListener {
            startActivity(Intent(this, TagsActivity::class.java))
        }
        // Kết quả học tập: offline-first như Tags → mở trực tiếp, không bắt đăng nhập.
        // (Đồng bộ network chỉ chạy khi đã đăng nhập + có mạng — xử lý trong repository.)
        binding.cardGrades.setOnClickListener {
            startActivity(Intent(this, GradesActivity::class.java))
        }
        binding.cardEmail.setOnClickListener {
            requireLoginThenStart(EmailActivity::class.java)
        }
        binding.cardProfile.setOnClickListener {
            requireLoginThenStart(ProfileActivity::class.java)
        }
        binding.cardNews.setOnClickListener {
            requireLoginThenStart(NewsListActivity::class.java)
        }
        binding.cardAi.setOnClickListener {
            requireLogin(
                context = this,
                onRequestSignIn = {
                    val auth = (application as TodoApplication).container.authManager
                    signInLauncher.launch(auth.getSignInIntent())
                },
            ) {
                AiChatActivity.startStandalone(this)
            }
        }
    }

    private fun requireLoginThenStart(target: Class<*>) {
        requireLogin(
            context = this,
            onRequestSignIn = {
                val auth = (application as TodoApplication).container.authManager
                signInLauncher.launch(auth.getSignInIntent())
            },
        ) {
            startActivity(Intent(this, target))
        }
    }

    // ── Observe ───────────────────────────────────────────────
    private fun observeData() {
        val todayStart = viewModel.getDayStart(System.currentTimeMillis())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    combine(
                        viewModel.weekDays,
                        viewModel.taskCountsForWeek,
                        viewModel.selectedDayMillis
                    ) { days, counts, selected ->
                        days.map { day ->
                            CalendarDayItem(
                                dayMillis = day,
                                taskCount = counts[day] ?: 0,
                                isSelected = viewModel.isSameDay(day, selected),
                                isToday = viewModel.isSameDay(day, todayStart)
                            )
                        }
                    }.collect { items ->
                        calendarAdapter.submitList(items)
                        items.firstOrNull()?.let { updateWeekLabel(it.dayMillis) }
                    }
                }

                launch {
                    combine(
                        viewModel.tasksForSelectedDay,
                        viewModel.selectedDayMillis
                    ) { tasks, day -> Pair(tasks, day) }
                        .collect { (tasks, day) ->
                            dayTaskAdapter.submitList(tasks)
                            updateSelectedDayLabel(day)
                            val empty = tasks.isEmpty()
                            binding.rvDayTasks.visibility = if (empty) View.GONE else View.VISIBLE
                            binding.tvEmptyTasks.visibility = if (empty) View.VISIBLE else View.GONE
                        }
                }
            }
        }
    }

    private fun updateWeekLabel(firstDay: Long) {
        val lastDay = firstDay + 6 * 86_400_000L
        val c1 = Calendar.getInstance().apply { timeInMillis = firstDay }
        val c2 = Calendar.getInstance().apply { timeInMillis = lastDay }
        binding.tvWeekLabel.text = if (c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)) {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(firstDay))
        } else {
            val m1 = SimpleDateFormat("MMM", Locale.getDefault()).format(Date(firstDay))
            val m2 = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(lastDay))
            "$m1 – $m2"
        }
    }

    private fun updateSelectedDayLabel(day: Long) {
        binding.tvSelectedDayLabel.text =
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(day))
    }
}



package com.project3.todoapp.ui.news

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.databinding.ActivityNewsListBinding
import kotlinx.coroutines.launch

/**
 * NewsListActivity — danh sách tin tức + kế hoạch HUST.
 *
 * - 3 tab filter: Tất cả / Tin tức / Kế hoạch (dùng TabLayout).
 * - SwipeRefreshLayout: pull-to-refresh → repository.refresh() (PULL từ server).
 * - Click 1 item → mở [NewsDetailActivity].
 *
 * Pattern y hệt các Activity khác trong app: ViewBinding, viewModels delegate,
 * repeatOnLifecycle để collect StateFlow.
 */
class NewsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsListBinding
    private lateinit var adapter: NewsListAdapter

    private val viewModel: NewsListViewModel by viewModels {
        NewsListViewModel.provideFactory(
            (application as TodoApplication).container.newsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        binding.btnBack.setOnClickListener { finish() }

        observeState()
    }

    private fun setupTabs() {
        // Order phải khớp Filter enum: ALL / NEWS_ONLY / PLAN_ONLY
        binding.tabLayout.apply {
            addTab(newTab().setText("Tất cả"))
            addTab(newTab().setText("Tin tức"))
            addTab(newTab().setText("Kế hoạch"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    viewModel.setFilter(
                        when (tab.position) {
                            1    -> NewsListViewModel.Filter.NEWS_ONLY
                            2    -> NewsListViewModel.Filter.PLAN_ONLY
                            else -> NewsListViewModel.Filter.ALL
                        }
                    )
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
    }

    private fun setupRecyclerView() {
        adapter = NewsListAdapter { news ->
            startActivity(
                Intent(this, NewsDetailActivity::class.java)
                    .putExtra(NewsDetailActivity.EXTRA_NEWS_ID, news.id)
            )
        }
        binding.rvNews.adapter = adapter
        binding.rvNews.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.news.collect { list ->
                        adapter.submitList(list)
                        binding.tvEmpty.isVisible = list.isEmpty()
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefresh.isRefreshing = refreshing
                    }
                }
            }
        }
    }
}

package com.project3.todoapp.ui.email

import com.project3.todoapp.ui.common.applyWindowInsets
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.databinding.ActivityEmailBinding
import com.project3.todoapp.ui.emailthread.EmailThreadActivity
import kotlinx.coroutines.launch

/**
 * EmailActivity — list email.
 *
 * THAY ĐỔI:
 *  - Click email → mở EmailThreadActivity (KHÔNG còn auto-open Gmail intent).
 *  - Bỏ onDeleteClick (read-only).
 *  - Observe 2 stream: emails (List<Email> từ Room) và state (UiState).
 */
class EmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailBinding
    private lateinit var adapter: EmailAdapter

    private val viewModel: EmailViewModel by viewModels {
        EmailViewModel.provideFactory(
            (application as TodoApplication).container.emailRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.appBar)

        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = EmailAdapter(
            onClick = { email ->
                // Click → mở thread màn đọc thread
                val intent = Intent(this, EmailThreadActivity::class.java).apply {
                    putExtra(EmailThreadActivity.EXTRA_EMAIL_ID, email.id)
                    putExtra(EmailThreadActivity.EXTRA_SUBJECT, email.subject)
                    putExtra(EmailThreadActivity.EXTRA_SENDER, email.sender)
                    putExtra(EmailThreadActivity.EXTRA_DEEP_LINK, email.deepLinkIntent)
                }
                startActivity(intent)
            },
        )
        binding.rvEmails.adapter = adapter
        binding.rvEmails.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.btnSync.setOnClickListener { viewModel.syncFromGmail() }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean {
                viewModel.search(q.orEmpty())
                binding.searchView.clearFocus()
                return true
            }

            // Có thể bật auto-search khi gõ — tạm để tắt cho khỏi spam Room
            override fun onQueryTextChange(q: String?): Boolean = false
        })
    }

    private fun observeData() {
        // Stream 1: list email từ Room (luôn có data, kể cả offline)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.emails.collect { list ->
                    adapter.submitList(list)
                    binding.tvEmpty.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Stream 2: UiState — loading, error, toast
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.swipeRefresh.isRefreshing = s.isSyncing
                    binding.progressSync.isVisible = s.isPullingFromGmail
                    binding.btnSync.isEnabled = !s.isPullingFromGmail

                    s.errorMessage?.let {
                        Toast.makeText(this@EmailActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                    s.toastMessage?.let {
                        Toast.makeText(this@EmailActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                }
            }
        }
    }
}

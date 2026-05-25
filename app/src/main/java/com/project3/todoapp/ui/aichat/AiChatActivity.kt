package com.project3.todoapp.ui.aichat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.databinding.ActivityAiChatBinding
import kotlinx.coroutines.launch

/**
 * AiChatActivity — màn chat AI duy nhất, dùng cho mọi ChatContext (Email, News, Standalone).
 *
 * Caller dùng helper:
 *   - [startEmail]      cho EmailThreadActivity
 *   - [startNews]       cho NewsDetailActivity
 *   - [startStandalone] cho MainActivity (chat trống)
 *
 * Khi AI tạo task qua function calling, set RESULT_OK với extra
 * EXTRA_TASKS_CHANGED=true → caller (TaskList) biết refresh.
 */
class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var adapter: AiMessageAdapter

    private val chatContext: ChatContext? by lazy { ChatContext.readFrom(intent) }

    private val viewModel: AiChatViewModel by viewModels {
        val container = (application as TodoApplication).container
        AiChatViewModel.provideFactory(
            context         = chatContext!!,
            aiRepository    = container.aiRepository,
            emailRepository = container.emailRepository,
            newsRepository  = container.newsRepository,
        )
    }

    private var lastTaskCreatedSignal = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (chatContext == null) {
            Toast.makeText(this, "Thiếu chat context", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = AiMessageAdapter()
        binding.rvMessages.adapter = adapter
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.etInput.setOnEditorActionListener { _, _, _ ->
            sendCurrentInput()
            true
        }
    }

    private fun sendCurrentInput() {
        val text = binding.etInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.send(text)
        binding.etInput.text?.clear()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.tvSubtitle.text = s.subtitle
                    binding.progressThinking.isVisible = s.isThinking
                    binding.btnSend.isEnabled = !s.isThinking

                    // Empty state — chỉ show khi list rỗng + không thinking + có hint
                    val showEmpty = s.items.isEmpty() && !s.isThinking && s.emptyHint.isNotBlank()
                    binding.tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
                    if (showEmpty) binding.tvEmpty.text = s.emptyHint

                    adapter.submitList(s.items.toList()) {
                        if (s.items.isNotEmpty()) {
                            binding.rvMessages.smoothScrollToPosition(s.items.size - 1)
                        }
                    }

                    s.errorMessage?.let {
                        Toast.makeText(this@AiChatActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    if (s.taskCreatedSignal != lastTaskCreatedSignal) {
                        lastTaskCreatedSignal = s.taskCreatedSignal
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_TASKS_CHANGED, true),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TASKS_CHANGED = "tasks_changed"

        /** Helper cho EmailThreadActivity. */
        fun startEmail(ctx: Context, emailId: String, subject: String?) {
            ctx.startActivity(
                Intent(ctx, AiChatActivity::class.java).also {
                    ChatContext.Email(emailId, subject).putInto(it)
                }
            )
        }

        /** Helper cho NewsDetailActivity. */
        fun startNews(ctx: Context, newsId: String) {
            ctx.startActivity(
                Intent(ctx, AiChatActivity::class.java).also {
                    ChatContext.News(newsId).putInto(it)
                }
            )
        }

        /** Helper cho MainActivity — mở chat tự do, lịch sử trống. */
        fun startStandalone(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, AiChatActivity::class.java).also {
                    ChatContext.Standalone.putInto(it)
                }
            )
        }
    }
}

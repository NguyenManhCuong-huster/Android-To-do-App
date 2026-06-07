package com.project3.todoapp.ui.aichat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.ai.AttachmentRef
import com.project3.todoapp.databinding.ActivityAiChatBinding
import kotlinx.coroutines.launch
import java.io.File


class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var adapter: AiMessageAdapter

    private val chatContext: ChatContext? by lazy { ChatContext.readFrom(intent) }

    private val attachmentRepo by lazy {
        (application as TodoApplication).container.attachmentRepository
    }

    private val viewModel: AiChatViewModel by viewModels {
        val container = (application as TodoApplication).container
        AiChatViewModel.provideFactory(
            context = chatContext!!,
            aiRepository = container.aiRepository,
            emailRepository = container.emailRepository,
            newsRepository = container.newsRepository,
            attachmentRepository = container.attachmentRepository,
        )
    }

    private var lastTaskCreatedSignal = 0


    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.attachFiles(uris)
    }

    /**
     * Tap 1 attachment chip trong bubble → download (nếu cần) → mở qua intent.
     * Pattern này dùng chung với NewsDetailActivity / EmailThreadActivity.
     */
    private fun onAttachmentTap(ref: AttachmentRef) {
        lifecycleScope.launch {
            when (val r = attachmentRepo.downloadIfNeeded(ref.id)) {
                is com.project3.todoapp.data.attachment.AttachmentRepository.DownloadResult.Ready -> {
                    val intent =
                        attachmentRepo.buildOpenIntent(File(r.file.absolutePath), r.mimeType)
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            this@AiChatActivity,
                            "Không có app để mở file này",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                is com.project3.todoapp.data.attachment.AttachmentRepository.DownloadResult.Error ->
                    Toast.makeText(this@AiChatActivity, r.message, Toast.LENGTH_LONG).show()

                com.project3.todoapp.data.attachment.AttachmentRepository.DownloadResult.NotAvailable ->
                    Toast.makeText(
                        this@AiChatActivity,
                        "File chưa sẵn sàng trên server.",
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

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
        adapter = AiMessageAdapter(
            onAttachmentTap = ::onAttachmentTap,
        )
        binding.rvMessages.adapter = adapter
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.btnAttach.setOnClickListener {
            // SAF mime "*/*" → cho phép mọi loại file
            try {
                pickFilesLauncher.launch("*/*")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Thiết bị không có app file picker", Toast.LENGTH_LONG).show()
            }
        }
        binding.etInput.setOnEditorActionListener { _, _, _ ->
            sendCurrentInput()
            true
        }
    }

    private fun sendCurrentInput() {
        val text = binding.etInput.text?.toString().orEmpty()
        viewModel.send(text)
        binding.etInput.text?.clear()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.tvSubtitle.text = s.subtitle
                    binding.progressThinking.isVisible = s.isThinking

                    // btnSend disabled khi: thinking, hoặc đang upload, hoặc
                    // input trống VÀ không có pending file.
                    val canSend = !s.isThinking && s.uploadingCount == 0
                    binding.btnSend.isEnabled = canSend
                    binding.btnAttach.isEnabled = canSend

                    // Empty state — chỉ show khi list rỗng + không thinking + có hint
                    val showEmpty = s.items.isEmpty() && !s.isThinking && s.emptyHint.isNotBlank()
                    binding.tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
                    if (showEmpty) binding.tvEmpty.text = s.emptyHint

                    adapter.submitList(s.items.toList()) {
                        if (s.items.isNotEmpty()) {
                            binding.rvMessages.smoothScrollToPosition(s.items.size - 1)
                        }
                    }

                    renderPendingAttachments(s.pendingAttachments, s.uploadingCount)

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

    /**
     * Render pending attachments + upload spinner ở row phía trên input.
     * Mỗi chip có nút X để bỏ.
     */
    private fun renderPendingAttachments(
        pending: List<AttachmentRef>,
        uploadingCount: Int,
    ) {
        val show = pending.isNotEmpty() || uploadingCount > 0
        binding.pendingAttachmentsRow.isVisible = show
        binding.pendingAttachmentsContainer.removeAllViews()
        binding.pendingProgress.isVisible = uploadingCount > 0
        binding.pendingProgressLabel.isVisible = uploadingCount > 0
        if (uploadingCount > 0) {
            binding.pendingProgressLabel.text =
                if (uploadingCount == 1) "Đang upload..."
                else "Đang upload $uploadingCount files..."
        }

        val ctx = this
        for (ref in pending) {
            val chipView = layoutInflater.inflate(
                com.project3.todoapp.R.layout.item_pending_attachment_chip,
                binding.pendingAttachmentsContainer,
                false,
            )
            chipView.findViewById<android.widget.TextView>(com.project3.todoapp.R.id.tvChipName)
                .text = ref.fileName
            chipView.findViewById<android.widget.ImageButton>(com.project3.todoapp.R.id.btnChipRemove)
                .setOnClickListener {
                    viewModel.removePending(ref.id)
                }
            binding.pendingAttachmentsContainer.addView(chipView)
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

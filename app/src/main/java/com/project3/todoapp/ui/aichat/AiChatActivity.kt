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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.ai.AiReference
import com.project3.todoapp.data.ai.AttachmentRef
import com.project3.todoapp.ui.emailthread.EmailThreadActivity
import com.project3.todoapp.ui.news.NewsDetailActivity
import com.project3.todoapp.databinding.ActivityAiChatBinding
import kotlinx.coroutines.launch
import java.io.File


class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var adapter: AiMessageAdapter

    private val chatContext: ChatContext? by lazy { ChatContext.readFrom(intent) }
    private val resumeSessionId: String? by lazy { intent.getStringExtra(EXTRA_RESUME_SESSION_ID) }

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
            chatHistoryRepository = container.chatHistoryRepository,
            resumeSessionId = resumeSessionId,
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

    /** Tap 1 chip trích dẫn trong reply AI -> mở email / news tương ứng. */
    private fun onReferenceTap(ref: AiReference) {
        when (ref.type) {
            "email" -> startActivity(
                Intent(this, EmailThreadActivity::class.java)
                    .putExtra(EmailThreadActivity.EXTRA_EMAIL_ID, ref.id)
                    .putExtra(EmailThreadActivity.EXTRA_SUBJECT, ref.label)
            )
            "news" -> startActivity(
                Intent(this, NewsDetailActivity::class.java)
                    .putExtra(NewsDetailActivity.EXTRA_NEWS_ID, ref.id)
            )
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
        setupEdgeToEdge()
    }

    /**
     * Edge-to-edge + xử lý IME insets: khi bàn phím hiện, đẩy cả footer (thanh
     * soạn) và list lên trên để không bị che. Hoạt động đúng trên cả Android 15+
     * (nơi adjustResize không tự pad cho bàn phím nữa).
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = binding.root
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = baseLeft + bars.left,
                top = baseTop + bars.top,
                right = baseRight + bars.right,
                bottom = baseBottom + maxOf(ime.bottom, bars.bottom),
            )
            if (ime.bottom > bars.bottom) scrollToBottom()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun scrollToBottom() {
        val count = binding.rvMessages.adapter?.itemCount ?: return
        if (count > 0) binding.rvMessages.post { binding.rvMessages.scrollToPosition(count - 1) }
    }

    private fun setupRecyclerView() {
        adapter = AiMessageAdapter(
            onAttachmentTap = ::onAttachmentTap,
            onReferenceTap = ::onReferenceTap,
        )
        binding.rvMessages.adapter = adapter
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, ChatHistoryActivity::class.java))
        }
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
        const val EXTRA_RESUME_SESSION_ID = "resume_session_id"

        /** Mở lại 1 phiên cụ thể từ màn Lịch sử (resume by id). */
        fun startResume(ctx: Context, context: ChatContext, sessionId: String) {
            ctx.startActivity(
                Intent(ctx, AiChatActivity::class.java).also {
                    context.putInto(it)
                    it.putExtra(EXTRA_RESUME_SESSION_ID, sessionId)
                    // Thay thế chat hiện tại (chat1 -> chat2): xoá chat cũ + màn Lịch sử
                    // khỏi back stack, back từ chat2 về thẳng màn trước đó.
                    it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }

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

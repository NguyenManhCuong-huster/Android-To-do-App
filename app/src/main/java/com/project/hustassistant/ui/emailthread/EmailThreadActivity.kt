package com.project.hustassistant.ui.emailthread

import com.project.hustassistant.ui.common.applyWindowInsets
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.project.hustassistant.TodoApplication
import com.project.hustassistant.databinding.ActivityEmailThreadBinding
import com.project.hustassistant.ui.aichat.AiChatActivity
import kotlinx.coroutines.launch
import java.io.File

/**
 * EmailThreadActivity — màn đọc thread.
 *
 * THAY ĐỔI 2026-05-23:
 *  - Adapter constructor giờ nhận 3 callback cho attachments.
 *  - Collect openEvents → fire Intent.ACTION_VIEW qua FileProvider.
 */
class EmailThreadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailThreadBinding
    private lateinit var adapter: ThreadMessageAdapter

    private val emailId: String by lazy { intent.getStringExtra(EXTRA_EMAIL_ID).orEmpty() }
    private val initialSubject: String? get() = intent.getStringExtra(EXTRA_SUBJECT)
    private val initialSender: String? get() = intent.getStringExtra(EXTRA_SENDER)
    private val deepLinkFromExtras: String? get() = intent.getStringExtra(EXTRA_DEEP_LINK)

    private val attachmentRepo by lazy {
        (application as TodoApplication).container.attachmentRepository
    }

    private val viewModel: EmailThreadViewModel by viewModels {
        EmailThreadViewModel.provideFactory(
            emailId,
            (application as TodoApplication).container.emailRepository,
            attachmentRepo,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailThreadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.header)

        if (emailId.isBlank()) {
            Toast.makeText(this, "Thiếu email_id", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvSubject.text = initialSubject ?: "(no subject)"
        binding.tvFromHeader.text = initialSender ?: ""

        adapter = ThreadMessageAdapter(
            attachmentsFor  = { gmailMessageId ->
                viewModel.state.value.attachmentsByMessageId[gmailMessageId].orEmpty()
            },
            loadingIds      = { viewModel.loadingIds.value },
            onAttachmentTap = { viewModel.openAttachment(it) },
        )
        binding.rvThread.adapter = adapter
        binding.rvThread.layoutManager = LinearLayoutManager(this)

        setupButtons()
        observeState()
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnReply.setOnClickListener {
            val anchor = viewModel.state.value.messages.lastOrNull()
            val to = anchor?.sender?.let { extractEmailAddress(it) }
            val subject = anchor?.subject?.let { if (it.startsWith("Re:", true)) it else "Re: $it" }
                ?: initialSubject?.let { "Re: $it" }
            openComposeIntent(to, subject)
        }

        binding.btnAi.setOnClickListener {
            AiChatActivity.startEmail(this, emailId, initialSubject)
        }

        binding.btnOpenGmail.setOnClickListener { openInGmail() }
    }

    private fun openComposeIntent(to: String?, subject: String?) {
        val uri = Uri.parse("mailto:" + (to.orEmpty()))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Không tìm thấy app email", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInGmail() {
        val link = viewModel.state.value.messages.lastOrNull()?.deepLinkIntent
            ?: deepLinkFromExtras
        if (!link.isNullOrBlank()) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                return
            } catch (_: ActivityNotFoundException) { }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/")))
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được Gmail", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractEmailAddress(raw: String): String {
        val lt = raw.indexOf('<')
        val gt = raw.indexOf('>')
        return if (lt in 0 until gt) raw.substring(lt + 1, gt).trim() else raw.trim()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { s ->
                        binding.progressBar.isVisible = s.isLoading
                        binding.tvEmpty.visibility =
                            if (!s.isLoading && s.messages.isEmpty()) View.VISIBLE else View.GONE

                        adapter.submitList(s.messages) {
                            if (s.messages.isNotEmpty()) {
                                binding.rvThread.post {
                                    binding.rvThread.smoothScrollToPosition(s.messages.size - 1)
                                }
                            }
                        }

                        s.messages.lastOrNull()?.let { anchor ->
                            anchor.subject?.let { binding.tvSubject.text = it }
                            anchor.sender?.let { binding.tvFromHeader.text = it }
                        }

                        s.errorMessage?.let {
                            Toast.makeText(this@EmailThreadActivity, it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
                launch {
                    viewModel.loadingIds.collect {
                        // Rebind toàn bộ — số message thread thường nhỏ, rẻ.
                        adapter.notifyDataSetChanged()
                    }
                }
                launch {
                    viewModel.openEvents.collect { event ->
                        if (event != null) {
                            handleOpenEvent(event)
                            viewModel.consumeOpenEvent()
                        }
                    }
                }
            }
        }
    }

    private fun handleOpenEvent(event: EmailThreadViewModel.OpenEvent) {
        when (event) {
            is EmailThreadViewModel.OpenEvent.Ready -> {
                val intent = attachmentRepo.buildOpenIntent(File(event.absolutePath), event.mimeType)
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "Không có app để mở file này", Toast.LENGTH_LONG).show()
                }
            }
            is EmailThreadViewModel.OpenEvent.Error ->
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_EMAIL_ID = "email_id"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_DEEP_LINK = "deep_link"
    }
}

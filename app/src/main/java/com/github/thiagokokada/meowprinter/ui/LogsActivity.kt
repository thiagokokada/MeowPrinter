package com.github.thiagokokada.meowprinter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.data.LogStore
import com.github.thiagokokada.meowprinter.databinding.ActivityLogsBinding

class LogsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.applyTopSystemBarPadding()
        binding.logsContent.applySideAndBottomSystemBarsPadding()
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding.buttonClearLogs.setOnClickListener {
            LogStore.clear()
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.logsValue.text = LogStore.asText().ifBlank { getString(R.string.no_logs_yet) }
    }
}

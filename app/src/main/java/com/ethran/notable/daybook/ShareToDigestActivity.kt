package com.ethran.notable.daybook

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ethran.notable.utils.hasUsableStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives text from another app's Share menu or text-selection menu and saves it as a
 * digest. It has no screen of its own: it saves, confirms with a short message and closes,
 * leaving the reader where they were.
 */
@AndroidEntryPoint
class ShareToDigestActivity : ComponentActivity() {

    @Inject
    lateinit var repository: dagger.Lazy<DaybookRepository>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }
        if (text.isNullOrBlank()) {
            finishWith("There was no text to save.")
            return
        }
        if (!hasUsableStorage(this)) {
            finishWith("Open Notable once and allow file access, then try again.")
            return
        }

        val source = sourceName()
        lifecycleScope.launch {
            val saved = runCatching { repository.get().addDigest(text, source) }.getOrNull()
            finishWith(if (saved != null) "Saved to Digest" else "Couldn't save to Digest.")
        }
    }

    /** The shared item's title if it has one, otherwise the name of the app it came from. */
    private fun sourceName(): String? {
        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra(Intent.EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let { return it }
        val packageName = referrer?.takeIf { it.scheme == "android-app" }?.host ?: return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()
        }.getOrDefault(packageName)
    }

    private fun finishWith(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}

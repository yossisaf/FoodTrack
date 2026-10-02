package com.foodtrack.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.foodtrack.app.FoodTrackApplication
import com.foodtrack.app.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * DEBUGGING SCAFFOLDING (kept intentionally — see project history): every
 * screen extends this so any crash shows up as an on-screen, selectable
 * dialog instead of silently killing the app. Remove once real
 * logging/crash-reporting is set up for the finished app.
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Which bottom-navigation tab this screen represents, or null for
     * secondary screens (details, forms, pickers). Top-level screens show the
     * bottom bar; secondary screens show an "up" arrow in the title bar.
     */
    protected open val navItemId: Int? get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForPreviousCrash()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val navId = navItemId
        supportActionBar?.setDisplayHomeAsUpEnabled(navId == null)
        setupBottomNav(navId)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    protected fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** Wraps a dialog input so it doesn't touch the dialog edges. */
    protected fun paddedForDialog(view: View): View = android.widget.FrameLayout(this).apply {
        setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0)
        addView(view)
    }

    /** Shows a message with an undo action, anchored above the bottom bar when present. */
    protected fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        val snackbar = Snackbar.make(findViewById<View>(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) { onUndo() }
        findViewById<View>(R.id.bottomNav)?.takeIf { it.visibility == View.VISIBLE }?.let { snackbar.setAnchorView(it) }
        snackbar.show()
    }

    /** Lightweight confirmation (no action) for saves and other completed operations. */
    protected fun showMessage(message: String) {
        val snackbar = Snackbar.make(findViewById<View>(android.R.id.content), message, Snackbar.LENGTH_SHORT)
        findViewById<View>(R.id.bottomNav)?.takeIf { it.visibility == View.VISIBLE }?.let { snackbar.setAnchorView(it) }
        snackbar.show()
    }

    private fun setupBottomNav(selectedId: Int?) {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        if (selectedId == null) {
            nav.visibility = View.GONE
            return
        }
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            if (item.itemId != selectedId) navigateToTab(item.itemId, selectedId)
            true
        }
    }

    private fun navigateToTab(targetId: Int, currentId: Int) {
        val target = when (targetId) {
            R.id.nav_today -> TodayActivity::class.java
            R.id.nav_food -> MainActivity::class.java
            R.id.nav_activity -> PhysicalActivityActivity::class.java
            R.id.nav_weight -> WeightStatsActivity::class.java
            else -> return
        }
        val intent = Intent(this, target)
        if (targetId == R.id.nav_today) {
            // Today is the root: return to it instead of stacking another copy.
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        // Tab-to-tab switches replace the current screen so Back always returns to Today.
        if (currentId != R.id.nav_today && targetId != R.id.nav_today) finish()
    }

    /** Launches a coroutine and routes any exception to the on-screen crash dialog. */
    protected fun runSafely(where: String, block: suspend CoroutineScope.() -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (t: CancellationException) {
                // Expected when the screen starts a newer search or leaves the screen.
            } catch (t: Exception) {
                if (!isFinishing && !isDestroyed) showCrashDialog(where, t)
            }
        }
    }

    private fun checkForPreviousCrash() {
        val crashFile = File(filesDir, FoodTrackApplication.CRASH_FILE_NAME)
        if (crashFile.exists()) {
            val crashText = crashFile.readText()
            crashFile.delete()
            showTextDialog("קריסה קודמת", "קריסה מההפעלה הקודמת:\n\n$crashText")
        }
    }

    protected fun showCrashDialog(where: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        showTextDialog("שגיאה נתפסה", "מיקום: $where\n\n$sw")
    }

    private fun showTextDialog(title: String, message: String) {
        val textView = TextView(this).apply {
            text = message
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setTextIsSelectable(true)
        }
        val scrollView = ScrollView(this).apply { addView(textView) }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("סגור", null)
            .setCancelable(false)
            .show()
    }
}

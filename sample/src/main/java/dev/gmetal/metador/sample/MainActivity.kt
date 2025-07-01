package dev.gmetal.metador.sample

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dev.gmetal.metador.Metador
import dev.gmetal.metador.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var metador: Metador

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        metador = Metador.Builder()
            .withCacheDirectory(cacheDir.absolutePath)
            .build()

        binding.webUrl.setOnEditorActionListener { textView, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_GO -> {
                    binding.webUrl.clearFocus()
                    hideKeyboard()
                    showLoader()
                    metador.process(
                        Metador.request(textView.text.toString())
                            .onSuccess { result ->
                                showIt(result)
                                if (result.isEmpty()) {
                                    showNoResults()
                                } else {
                                    showResults()
                                }
                            }
                            .onFailure { error ->
                                showNoResults()
                                showIt(error)
                            }
                            .build()
                    )
                    true
                }
                else -> false
            }
        }
        binding.resultList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.resultList.adapter = KeyValueListAdapter(layoutInflater)
    }

    private fun showIt(error: Throwable) {
        Snackbar.make(
            binding.root,
            error.message.orDefault(R.string.unknown_error),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showIt(result: Map<String, String>) {
        with(binding.resultList.adapter as KeyValueListAdapter) {
            submitList(result.entries.map { it.key to it.value })
        }
    }

    private fun showLoader() {
        binding.loader.visibility = VISIBLE
        binding.resultList.visibility = GONE
        binding.emptyText.visibility = GONE
    }

    private fun showNoResults() {
        binding.loader.visibility = GONE
        binding.resultList.visibility = GONE
        binding.emptyText.visibility = VISIBLE
    }

    private fun showResults() {
        binding.loader.visibility = GONE
        binding.resultList.visibility = VISIBLE
        binding.emptyText.visibility = GONE
    }

    private fun hideKeyboard() {
        with(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager) {
            hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun String?.orDefault(@StringRes defaultResId: Int) = this ?: getString(defaultResId)
}

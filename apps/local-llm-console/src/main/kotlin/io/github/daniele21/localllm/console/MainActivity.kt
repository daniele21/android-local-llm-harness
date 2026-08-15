package io.github.daniele21.localllm.console

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import io.github.daniele21.localllm.console.ombra.OmbraProductApp
import io.github.daniele21.localllm.console.ombra.OmbraProductViewModel
import io.github.daniele21.localllm.ui.designsystem.OmbraTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: OmbraProductViewModel

    private val documentPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.importPickedDocument(result.data?.data)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[OmbraProductViewModel::class.java]
        setContent {
            OmbraTheme {
                OmbraProductApp(
                    viewModel = viewModel,
                    onPickDocument = { documentPicker.launch(viewModel.createOpenDocumentIntent()) },
                )
            }
        }
    }
}

package kr.jm.voicesummary.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class License(
    val name: String,
    val url: String,
    val license: String,
    val licenseText: String
)

private val licenses = listOf(
    License(
        name = "Kotlin",
        url = "https://kotlinlang.org",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Sherpa-ONNX",
        url = "https://github.com/k2-fsa/sherpa-onnx",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Whisper (OpenAI)",
        url = "https://github.com/openai/whisper",
        license = "MIT License",
        licenseText = MIT_LICENSE
    ),
    License(
        name = "AndroidX Core KTX",
        url = "https://developer.android.com/jetpack/androidx",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "AndroidX Lifecycle",
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "AndroidX Activity Compose",
        url = "https://developer.android.com/jetpack/androidx/releases/activity",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Jetpack Compose",
        url = "https://developer.android.com/jetpack/compose",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Room Database",
        url = "https://developer.android.com/jetpack/androidx/releases/room",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Apache Commons Compress",
        url = "https://commons.apache.org/proper/commons-compress/",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    ),
    License(
        name = "Material Icons Extended",
        url = "https://fonts.google.com/icons",
        license = "Apache License 2.0",
        licenseText = APACHE_2_0
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오픈소스 라이선스") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            items(licenses) { license ->
                LicenseItem(license = license)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LicenseItem(license: License) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(license.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        license.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        license.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        license.licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private const val APACHE_2_0 = """Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License."""

private const val MIT_LICENSE = """MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

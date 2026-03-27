package org.akvo.afribamodkvalidator.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.akvo.afribamodkvalidator.data.dto.KoboAsset
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.akvo.afribamodkvalidator.ui.viewmodel.LoginUiState
import org.akvo.afribamodkvalidator.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onDownloadStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreenContent(
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onServerUrlChange = viewModel::onServerUrlChange,
        onFetchAssetsClick = viewModel::fetchAssets,
        onAssetSelected = viewModel::onAssetSelected,
        onDownloadClick = {
            viewModel.startLoginAndDownloadProcess()
            onDownloadStart()
        },
        modifier = modifier
    )
}

@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onFetchAssetsClick: () -> Unit,
    onAssetSelected: (KoboAsset) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            enabled = !uiState.hasAssets,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !uiState.hasAssets,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            singleLine = true,
            enabled = !uiState.hasAssets,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.hasAssets) {
            AssetDropdown(
                assets = uiState.assets,
                selectedAsset = uiState.selectedAsset,
                onAssetSelected = onAssetSelected
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDownloadClick,
                enabled = uiState.isFormValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download Data")
            }
        } else {
            if (uiState.assetsError != null) {
                Text(
                    text = uiState.assetsError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onFetchAssetsClick,
                enabled = uiState.areCredentialsValid && !uiState.isLoadingAssets,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoadingAssets) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching Forms...")
                } else {
                    Text("Fetch Forms")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetDropdown(
    assets: List<KoboAsset>,
    selectedAsset: KoboAsset?,
    onAssetSelected: (KoboAsset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedAsset?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Form") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            assets.forEach { asset ->
                DropdownMenuItem(
                    text = { Text(asset.name) },
                    onClick = {
                        onAssetSelected(asset)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPhase1Preview() {
    AfriBamODKValidatorTheme {
        LoginScreenContent(
            uiState = LoginUiState(),
            onUsernameChange = {},
            onPasswordChange = {},
            onServerUrlChange = {},
            onFetchAssetsClick = {},
            onAssetSelected = {},
            onDownloadClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPhase2Preview() {
    val sampleAssets = listOf(
        KoboAsset(uid = "aXf2qPzKm8B7", name = "Parcel Boundary Mapping"),
        KoboAsset(uid = "bYg3rQaLn9C8", name = "Tree Inventory Survey"),
        KoboAsset(uid = "cZh4sTbMo0D9", name = "Nursery Monitoring Form")
    )
    AfriBamODKValidatorTheme {
        LoginScreenContent(
            uiState = LoginUiState(
                username = "testuser",
                password = "password123",
                serverUrl = "https://kc-eu.kobotoolbox.org",
                assets = sampleAssets,
                selectedAsset = sampleAssets[0]
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onServerUrlChange = {},
            onFetchAssetsClick = {},
            onAssetSelected = {},
            onDownloadClick = {}
        )
    }
}

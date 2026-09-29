package com.example.autodial.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.ui.MainViewModel
import com.example.autodial.ui.theme.GreenLogo

@Composable
fun LoginScreen(viewModel: MainViewModel, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val loading by viewModel.authLoading.collectAsState()
    val error by viewModel.authError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Welcome Back",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            "Sign in to continue to AUTODIAL",
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(48.dp))

        if (error != null) {
            Text(error!!, color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(8.dp))
        
        TextButton(
            onClick = { viewModel.forgotPassword(email) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot Password?", color = GreenLogo)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.signIn(email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !loading && email.isNotEmpty() && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            else Text("SIGN IN", fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?", color = Color.Gray)
            TextButton(onClick = onNavigateToRegister) {
                Text("Register Now", color = GreenLogo, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RegistrationScreen(viewModel: MainViewModel, onNavigateToLogin: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val loading by viewModel.authLoading.collectAsState()
    val error by viewModel.authError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Create Account",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            "Join the AUTODIAL agent network",
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(40.dp))

        if (error != null) {
            Text(error!!, color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Create Password") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.signUp(name, phone, email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !loading && name.isNotEmpty() && phone.isNotEmpty() && email.isNotEmpty() && password.length >= 6,
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            else Text("REGISTER AGENT", fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", color = Color.Gray)
            TextButton(onClick = onNavigateToLogin) {
                Text("Sign In", color = GreenLogo, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VerificationPendingScreen(viewModel: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MarkEmailRead,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = GreenLogo
        )
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "Verify Email",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "We've sent a verification link to your email. Please click the link to activate your agent terminal.",
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = { viewModel.checkAuthState() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenLogo),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("I'VE VERIFIED", fontWeight = FontWeight.Bold, color = Color.Black)
        }
        
        Spacer(Modifier.height(16.dp))
        
        TextButton(onClick = { viewModel.resendVerification() }) {
            Text("Resend Verification Email", color = Color.White.copy(alpha = 0.6f))
        }
        
        Spacer(Modifier.height(24.dp))
        
        TextButton(onClick = { viewModel.logout() }) {
            Text("Use Different Account", color = Color.Red.copy(alpha = 0.7f))
        }
    }
}

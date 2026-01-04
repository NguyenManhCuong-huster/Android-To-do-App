package com.project3.todoapp.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(authManager.isUserLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    fun updateLoginStatus() {
        _isLoggedIn.value = authManager.isUserLoggedIn()
    }

    fun logout(onSuccess: () -> Unit) {
        authManager.signOut {
            updateLoginStatus()
            onSuccess()
        }
    }

    // Factory tương tự như các ViewModel trước
    companion object {
        fun provideFactory(authManager: AuthManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(authManager) as T
            }
        }
    }
}
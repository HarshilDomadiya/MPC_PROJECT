package com.plantiq.plantmonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantiq.plantmonitor.data.model.Resource
import com.plantiq.plantmonitor.data.model.UserProfile
import com.plantiq.plantmonitor.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(repository.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val uid = repository.getCurrentUid()
        if (uid != null) {
            _isLoggedIn.value = true
            loadProfile(uid)
        } else {
            _isLoggedIn.value = false
        }
    }

    private fun loadProfile(uid: String) {
        viewModelScope.launch {
            repository.getUserProfileStream(uid).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _userProfile.value = resource.data
                    }
                    is Resource.Error -> {
                        _errorMessage.value = resource.message
                        // If we get a permission error on profile load, the session might be stale
                        if (resource.message.contains("permission", ignoreCase = true)) {
                            repository.logout()
                            _isLoggedIn.value = false
                        }
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Please enter email and password."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.login(email.trim(), pass)
            _isLoading.value = false
            result.onSuccess { user ->
                _isLoggedIn.value = true
                loadProfile(user.uid)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Login failed. Please check your credentials."
            }
        }
    }

    fun signUp(name: String, email: String, pass: String, confirmPass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "All fields are required."
            return
        }
        if (pass != confirmPass) {
            _errorMessage.value = "Passwords do not match."
            return
        }
        if (pass.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.signUp(name.trim(), email.trim(), pass)
            _isLoading.value = false
            result.onSuccess { user ->
                _isLoggedIn.value = true
                loadProfile(user.uid)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Account creation failed."
            }
        }
    }

    fun logout() {
        repository.logout()
        _isLoggedIn.value = false
        _userProfile.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

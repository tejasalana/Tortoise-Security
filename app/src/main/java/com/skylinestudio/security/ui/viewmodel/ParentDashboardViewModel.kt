package com.skylinestudio.security.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skylinestudio.security.data.DeviceStatus
import com.skylinestudio.security.repo.FirebaseRepo
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChildStatus(
    val uid: String,
    val name: String,
    val isOnline: Boolean,
    val lastSeen: String,
    val status: DeviceStatus? = null
)

class ParentDashboardViewModel : ViewModel() {

    val parentId: String = Firebase.auth.currentUser?.uid ?: ""
    val parentName: String =
        Firebase.auth.currentUser?.displayName
            ?: Firebase.auth.currentUser?.email?.substringBefore('@')
            ?: "Parent"

    private val _children = MutableStateFlow<List<ChildStatus>>(emptyList())
    val children: StateFlow<List<ChildStatus>> = _children.asStateFlow()

    init {
        if (parentId.isNotEmpty()) {
            viewModelScope.launch {
                FirebaseRepo.getLinkedChildren(parentId).collect { users ->
                    val currentChildren = users.map { user ->
                        ChildStatus(
                            uid = user.uid,
                            name = user.name.ifBlank { user.email },
                            isOnline = false,
                            lastSeen = "Linked",
                        )
                    }
                    _children.value = currentChildren
                    
                    // Also start listening for status updates for each child
                    currentChildren.forEach { child ->
                        listenToChildStatus(child.uid)
                    }
                }
            }
        }
    }

    private fun listenToChildStatus(childId: String) {
        viewModelScope.launch {
            FirebaseRepo.statusFlow(childId).collect { status ->
                _children.value = _children.value.map { child ->
                    if (child.uid == childId) {
                        child.copy(status = status, isOnline = true) // Simple online logic for now
                    } else {
                        child
                    }
                }
            }
        }
    }
    
    fun requestStatusUpdate(childId: String) {
        viewModelScope.launch {
            FirebaseRepo.sendCommand(childId, "GET_STATUS")
        }
    }
}

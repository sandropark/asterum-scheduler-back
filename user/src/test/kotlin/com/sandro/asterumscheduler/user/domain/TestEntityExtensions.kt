package com.sandro.asterumscheduler.user.domain

fun User.assignIdForTest(id: Long) {
    val field = User::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
}

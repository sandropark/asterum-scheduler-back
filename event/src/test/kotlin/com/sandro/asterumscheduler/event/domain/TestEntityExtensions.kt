package com.sandro.asterumscheduler.event.domain

fun Event.assignIdForTest(id: Long) {
    val field = Event::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
}

fun EventInstance.assignIdForTest(id: Long) {
    val field = EventInstance::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
}

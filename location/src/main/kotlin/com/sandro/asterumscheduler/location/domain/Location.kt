package com.sandro.asterumscheduler.location.domain

import jakarta.persistence.*

@Entity
@Table(name = "locations")
class Location(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String,
    val capacity: Int,
)

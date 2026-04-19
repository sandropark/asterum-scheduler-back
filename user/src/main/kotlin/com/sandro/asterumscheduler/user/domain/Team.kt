package com.sandro.asterumscheduler.user.domain

import jakarta.persistence.*

@Entity
@Table(name = "teams")
class Team(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String,
)
